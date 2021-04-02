package at.jku.anttracks.parser.heap.pointer;

import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.ObjectVisitor;
import at.jku.anttracks.heap.labs.AddressHO;
import at.jku.anttracks.heap.labs.Lab;
import at.jku.anttracks.heap.objects.ObjectInfo;
import at.jku.anttracks.heap.roots.RootPtr;
import at.jku.anttracks.heap.space.Space;
import at.jku.anttracks.heap.space.SpaceInfo;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.parser.EventType;
import at.jku.anttracks.parser.TraceParser;
import at.jku.anttracks.util.ParallelizationUtil;
import at.jku.anttracks.util.TraceException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static at.jku.anttracks.parser.EventType.GC_PTR_UPDATE_POSTMOVE;
import static at.jku.anttracks.parser.TraceSlaveParser.NULL_PTR;

public class PointerHandling {
    public static final long NULL_PTR_UPDATED = -2L;

    public static void handleMovedObjectWithPointers(DetailedHeap heap, EventType eventType, AddressHO heapObject, long toAddr, long[] pointers) throws TraceException {
        boolean isMove = !EventType.Companion.isDedicatedPtrEvent(eventType);
        if (isMove) {
            if (pointers == null || mayBeNonDirtyObject(heapObject, pointers, heap.getSymbols())) {
                // Ptrs not set in move event
                // i.e., pointers did not change since last GC, copy pointers from from-address

                // TODO: Includce again in more intelligent way, CopyOnWriteArrayList needs too much memory
                //heap.addressesThatMayHaveInvalidPointersDueToNullingOnNonDirtyObjects.add(toAddr);
            } else {
                // Major GCs always send the referenced object's target address (address of the object after its possible move)
                // No pointer adjustment needed at the end of Major GC since pointers have been sent adjusted by the VM
                // Minor GCs always send the referenced object's base (from) address (address of an object before its possible move)
                // Pointer adjustment needed at the end of the Minor GC since objects may move change during GC
                if (heap.getGC().getType().isFull()) {
                    if (pointers.length > 0) {
                        if (pointers[0] != NULL_PTR) {
                            pointers[0] = pointers[0] + 1;
                        } else {
                            pointers[0] = NULL_PTR_UPDATED;
                        }
                    }
                }
                heapObject.fillPointers(pointers);
            }
        } else {
            // In this case, we got a PTR_UPDATE_PREMOVE or PTR_UPDATE_POSTMOVE event
            // These events are only allowed for non-moved objects, therefore we have to update the pointers in BACK, since those objects get merged back into FRONT later
            if (pointers == null || mayBeNonDirtyObject(heapObject, pointers, heap.getSymbols())) {
                // Keep pointers for object in back

                // Fields may have been set to null
                // TODO: Includce again in more intelligent way, CopyOnWriteArrayList needs too much memory
                //heap.addressesThatMayHaveInvalidPointersDueToNullingOnNonDirtyObjects.add(toAddr);
            } else {
                if (eventType == GC_PTR_UPDATE_POSTMOVE) {
                    if (pointers.length > 0) {
                        if (pointers[0] != NULL_PTR) {
                            pointers[0] = pointers[0] + 1;
                        } else {
                            pointers[0] = NULL_PTR_UPDATED;
                        }
                    }
                }
                heapObject.fillPointers(pointers);
            }
        }
    }

    // manipulated by multiple threads during GC, so we need to wait until GC end, i.e., Obj ptr array & filler.
    public static void handleMultiThreadedPtrEvent(DetailedHeap heap, long fromAddr, long toAddr, long[] ptrs) throws TraceException {
        // we use getObject() here instead of getObjectInBack() because gc could have failed...
        // in case of a failed gc a space does not have back anymore instead all objects are already moved to front
        // getObject() takes care of this by looking at back/front depending on the space's transition type
        AddressHO object = heap.getObject(fromAddr);
        object.fillPointers(ptrs);
    }

    public static void updateRefsNew(DetailedHeap heap, Space space, Lab lab, long addr, AddressHO object) throws TraceException {
        if (object.getPointerCount() == 0) {
            // Objects that don't have pointers don't have to be handled
            return;
        }

        // Check if pointers of object are already up-to-date (e.g., during a major GC)
        // If so, reset flag and exit this method
        long firstPointer = object.getPointer(0);
        if (firstPointer != NULL_PTR) {
            if (firstPointer == NULL_PTR_UPDATED) {
                object.setPointer(0, NULL_PTR);
                return;
            } else if (firstPointer % 2 != 0L) {
                object.setPointer(0, firstPointer - 1);
                return;
            }
        }

        for (int ptrNr = 0; ptrNr < object.getPointerCount(); ptrNr++) {
            long oldPointer = object.getPointer(ptrNr);
            if (oldPointer != NULL_PTR) {
                Space ptrSpace = heap.getSpace(oldPointer);
                if (ptrSpace.isBeingCollected()) {
                    /*
                    long movedTo = heap.movesSinceLastGCStart.get(oldPointer);
                    object.setPointer(ptrNr, movedTo);
                    */
                    AddressHO oldObj = heap.getObjectInBack(oldPointer);
                    assert oldObj != null : "Error during processing pointers of " + object + ".\nNo object found at " + oldPointer;
                    object.setPointer(ptrNr, oldObj.getTag());
                }
            }
        }
    }

    /*
    TODO some objects may have -1 pointers here (Objects with unknown pointer count which have not been moved due to a failed GC. This probably has to be fixed in the future.
     */
    public static void updateRefsFailedGC(DetailedHeap heap, Space space, Lab lab, long addr, AddressHO object) throws TraceException {
        assert !heap.getGC().getType().isFull() : "Major GC cannot fail";

        if (object.getPointerCount() < 0) {
            // Objects that don't have pointers don't have to be handled
            return;
        }

        // Check if pointers of object are already up-to-date (e.g., during a major GC)
        // If so, reset flag and exit this method
        long firstPointer = object.getPointer(0);
        if (firstPointer != NULL_PTR) {
            if (firstPointer == NULL_PTR_UPDATED) {
                object.setPointer(0, NULL_PTR);
                return;
            } else if (firstPointer % 2 != 0L) {
                object.setPointer(0, firstPointer - 1);
                return;
            }
        }

        for (int ptrNr = 0; ptrNr < object.getPointerCount(); ptrNr++) {
            long curPtrAddr = object.getPointer(ptrNr);
            Space curPtrSpace = heap.getSpace(curPtrAddr);
            if (curPtrSpace != null) {
                if (curPtrSpace.getTransitionType() == SpaceInfo.TransitionType.None) {
                    AddressHO curPtrObj = curPtrSpace.getObjectInFront(curPtrAddr);
                    if (curPtrObj == null) {
                        throw new TraceException(String.format("Pointing object: %s (Space: %s)\nDid not find lab for object at " +
                                                                       "%,d in FRONT of failed Space (Space: %s) during GC pointer update (Failed GC).\n",
                                                               object,
                                                               space.toShortString(),
                                                               curPtrAddr,
                                                               curPtrSpace.toShortString()));
                    }
                    long curPtrForwardedAddr = curPtrObj.getTag();
                    // if the forwardedAddr >= 0, the object has been moved before the collection failed-->it exists twice. In this case,
                    // assume that the moved object will survive the following major collection and update the pointer accordingly.
                    // if the forwardedAddr < 0, (following sentence is POGC only) the object did not move: it either lives in Eden/SurvFrom and the gc failed
                    // before
                    // it could be moved or it lives in old and would not move at all. In either case, the pointer remains unchanged.
                    if (curPtrForwardedAddr >= 0) {
                        object.setPointer(ptrNr, curPtrForwardedAddr);
                    }
                } else if (curPtrSpace.getTransitionType() == SpaceInfo.TransitionType.Accumulative) {
                    // The object's space that the pointer points to has not been collected at all, it cannot have been moved at all.
                    if (TraceParser.CONSISTENCY_CHECK) {
                        // throws an exception if object could not be found
                        if (curPtrSpace.getObjectInBack(curPtrAddr) == null) {
                            throw new TraceException("Expected to find referenced object in non-moved space's back but didn't find it!");
                        }
                    }
                } else if (curPtrSpace.getTransitionType() == SpaceInfo.TransitionType.ReplaceAll) {
                    // The object's space that the pointer points to has been collected.
                    // This means the the object must have been moved, since it is pointed to and must not have been collected.
                    AddressHO curPtrObj = curPtrSpace.getObjectInBack(curPtrAddr);
                    long forwardedAddr = curPtrObj.getTag();
                    if (forwardedAddr < 0) {
                        throw new TraceException(String.format("Pointing object: %s (Space: %s)\nForwarding address not set for " +
                                                                       "object at %,d (%s) in BACK (Space: %s) during GC pointer update.\n",
                                                               object,
                                                               space.toShortString(),
                                                               curPtrAddr,
                                                               curPtrObj,
                                                               curPtrSpace.toShortString()));
                    }
                    object.setPointer(ptrNr, forwardedAddr);
                } else {
                    assert false : "Here be dragons. No transition type beside None, Accumulative and ReplaceAll";
                }

            }
        }
    }

    private static boolean hasCorrectPointerCount(AddressHO heapObject, long[] ptrs, Symbols symbols) {
        ObjectInfo info = heapObject.getInfo();

        // Type info only stored for instance types, InstanceMirrorObjectInfo (java.lang.Class) and arrays should be ignored at the moment
        int expected = AddressHO.Companion.pointerCountOf(info, symbols, false);
        if (expected >= 0) {
            return ptrs.length == expected;
        }
        return false;
    }

    public static void handlePtrsOnGCEnd(DetailedHeap heap, boolean failed) throws TraceException {
        if (heap.getSymbols().expectPointers) {
            for (IncompletePointerInfo info : heap.notYetMatchedMultiThreadedPtrEventsToHandleAtGCEnd) {
                heap.multiThreadedPtrEventsToHandleAtGCEnd.get(info.toAddr).addPointers(info.ptrs);
            }
            heap.notYetMatchedMultiThreadedPtrEventsToHandleAtGCEnd.clear();

            for (Map.Entry<Long, IncompletePointerInfo> entry : heap.multiThreadedPtrEventsToHandleAtGCEnd.entrySet()) {
                assert entry.getValue().isComplete() : "Incomplete pointer info must have been completed by multi-threaded events but this is not the case here!";
                PointerHandling.handleMultiThreadedPtrEvent(heap, entry.getValue().fromAddr, entry.getValue().toAddr, entry.getValue().ptrs);
            }
            heap.multiThreadedPtrEventsToHandleAtGCEnd.clear();

            ParallelizationUtil.temporaryExecutorServiceBlocking(Arrays.asList(heap.getSpacesCloned()).iterator(), (Space space) -> {
                try {
                    if (space != null) {
                        switch (space.getTransitionType()) {
                            case Accumulative:
                                space.iterateUpdatePointer(new PtrUpdateVisitor(true, heap, failed));
                                space.iterateUpdatePointer(new PtrUpdateVisitor(false, heap, failed));
                                break;
                            case ReplaceAll:
                                space.iterateUpdatePointer(new PtrUpdateVisitor(true, heap, failed));
                                break;
                            case None:
                                if (!failed) {
                                    throw new TraceException("Space needs to be in transition if gc did not fail");
                                }
                                space.iterateUpdatePointer(new PtrUpdateVisitor(true, heap, failed));
                                break;
                            default:
                                assert false : "here be dragons";
                        }
                    }
                } catch (TraceException e) {
                    e.printStackTrace();
                }
            });

            if (failed) {
                Arrays.stream(heap.getSpacesCloned())
                      .filter(s -> s.getTransitionType() == SpaceInfo.TransitionType.None)
                      .flatMap(s -> Arrays.stream(s.getLabs()))
                      .forEach(Lab::resetForwardingAddresses);
            }
        }
    }

    public static void validateAllPointers(DetailedHeap heap) throws TraceException {
        class ThreadLocalPointerValidator implements ObjectVisitor {
            class InvalidPointerPair {
                public long from;
                public long to;

                public InvalidPointerPair(long from, long to) {
                    this.from = from;
                    this.to = to;
                }
            }

            public List<InvalidPointerPair> invalidPointerPairs = new ArrayList<>();

            @Override
            public void visit(long address,
                              @NotNull
                                      AddressHO obj,
                              @NotNull
                                      SpaceInfo space,
                              List<? extends RootPtr> rootPtrs) {
                for (int ptrIdx = 0; ptrIdx < obj.getPointerCount(); ptrIdx++) {
                    long ptrAddr = obj.getPointer(ptrIdx);
                    if (ptrAddr >= 0) {
                        boolean pointerIsFine;
                        try {
                            Space toSpace = heap.getSpace(ptrAddr);
                            AddressHO front = toSpace.getLabInFront(ptrAddr).getObject(ptrAddr);
                            pointerIsFine = front != null;
                        } catch (TraceException ex) {
                            pointerIsFine = false;
                        }

                        if (!pointerIsFine) {
                            invalidPointerPairs.add(new InvalidPointerPair(address, ptrAddr));
                        }
                    }
                }
                // Clearing the list
                heap.addressesThatMayHaveInvalidPointersDueToNullingOnNonDirtyObjects.remove(address);
            }
        }

        List<ThreadLocalPointerValidator.InvalidPointerPair> invalidPairs = heap.toObjectStream()
                                                                                .forEachParallel(() -> new ThreadLocalPointerValidator(),
                                                                                                 new ObjectVisitor.Settings(true))
                                                                                .stream()
                                                                                .flatMap(visitor -> visitor.invalidPointerPairs.stream())
                                                                                .collect(Collectors.toList());
        assert heap.addressesThatMayHaveInvalidPointersDueToNullingOnNonDirtyObjects.isEmpty() : "After validating pointers no 'dirty info' may be left";

        if (!invalidPairs.isEmpty()) {
            throw new TraceException(
                    "Something is wrong with the pointers!\n" +
                            invalidPairs
                                    .stream()
                                    .map(pair -> String.format("-> The object at address %,d points to position %,d, but no object is located at the to-position",
                                                               pair.from,
                                                               pair.to))
                                    .collect(Collectors.joining("\n")));
        }
    }

    private static boolean mayBeNonDirtyObject(AddressHO heapObject, long[] pointers, Symbols symbols) {
        return !hasCorrectPointerCount(heapObject, pointers, symbols) && Arrays.stream(pointers).allMatch(p -> p == -1L) && !heapObject.getType().hasUnknownPointerCount;
    }
}
