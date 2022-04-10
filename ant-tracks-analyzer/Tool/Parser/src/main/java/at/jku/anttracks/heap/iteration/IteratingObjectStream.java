package at.jku.anttracks.heap.iteration;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.classification.nodes.MapGroupingNode;
import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.ObjectStream;
import at.jku.anttracks.heap.ObjectVisitor;
import at.jku.anttracks.heap.space.Space;
import at.jku.anttracks.util.Consts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IteratingObjectStream implements ObjectStream {
    public IteratingObjectStream(DetailedHeap heap, boolean current) {
        this.heap = heap;
        this.spaces = heap.getSpacesCloned();
        this.current = current;
    }

    boolean current;

    private final DetailedHeap heap;
    private final Space[] spaces;

    private final List<Filter> filters = new ArrayList<>();
    private final List<IterationListener> listener = new ArrayList<>();

    @Override
    public ObjectStream filter(Filter... filter) {
        if (filter != null) {
            this.filters.addAll(Arrays.asList(filter));
        }
        return this;
    }

    @Override
    public ObjectStream sorted() {
        Arrays.sort(spaces, Comparator.comparingLong(space -> space == null ? -1 : space.getAddress()));
        return this;
    }

    @Override
    public MapGroupingNode groupMap(ClassifierChain classifier, boolean addFilterNodeInTree) {
        ThreadLocalHeapObjectMapGroupingVisitor grouping = new ThreadLocalHeapObjectMapGroupingVisitor(heap, classifier, filters, addFilterNodeInTree);
        forEach(grouping, ObjectVisitor.Settings.Companion.getALL_INFOS());
        return grouping;
    }

    @Override
    public MapGroupingNode groupMapParallel(ClassifierChain classifier, boolean addFilterNodeInTree) {
        List<ThreadLocalHeapObjectMapGroupingVisitor> returns = forEachParallel(() -> new ThreadLocalHeapObjectMapGroupingVisitor(heap, classifier, filters, addFilterNodeInTree),
                                                                                ObjectVisitor.Settings.Companion.getALL_INFOS());
        MapGroupingNode finalGroup = returns.stream().map(x -> (MapGroupingNode) x).reduce((a, b) -> (MapGroupingNode) a.merge(b)).orElse(new MapGroupingNode());
        finalGroup.sampleTopDown(heap);
        return finalGroup;
    }

    @Override
    public void forEach(ObjectVisitor visitor, ObjectVisitor.Settings visitorSettings) {
        for (Space space : spaces) {
            if (space != null) {
                space.iterate(heap, visitor, visitorSettings, filters, current, null, listener);
            }
        }
    }

    @Override
    public <I extends ObjectVisitor> List<I> forEachParallel(ObjectStream.ThreadVisitorGenerator<I> threadLocalVisitorGenerator, ObjectVisitor.Settings visitorSettings) {
        List<I> returns;
        List<Future<I>> futures = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(Consts.getAVAILABLE_PROCESSORS() * 2);

        for (Space space : spaces) {
            if (space != null) {
                space.iterateAsync(heap, threadLocalVisitorGenerator, visitorSettings, filters, current, executorService, listener).forEach(futures::add);
            }
        }

        try {
            // Wait "forever" until executor service finished all tasks
            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        returns = futures.stream().map(future -> {
            try {
                return future.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            return null;
        }).filter(x -> x != null).collect(Collectors.toList());
        return returns;
    }

    @Override
    public <A> Stream<A> map(Classifier<A> classifier) {
        List<A> ret = new ArrayList<>();
        forEach((address, object, space, rootPtrs) -> {
            try {
                ret.add(classifier.classify(
                        object,
                        address,
                        object.getInfo(),
                        space,
                        object.getType(),
                        object.getSize(),
                        object.isArray(),
                        object.getArrayLength(),
                        object.getSite(),
                        new long[0], // TODO Pointer
                        new long[0], // TODO Pointer
                        object.getEventType(),
                        rootPtrs,
                        -1, // TODO Age
                        object.getInfo().thread,
                        heap.getExternalThreadName(object.getInfo().thread)));
            } catch (Exception e) {
            }
        }, ObjectVisitor.Settings.Companion.getALL_INFOS());
        return ret.stream();
    }

    @Override
    public void addLabListener(IterationListener ll) {
        listener.add(ll);
    }

    @Override
    public void removeLabListener(IterationListener ll) {
        listener.remove(ll);
    }
}