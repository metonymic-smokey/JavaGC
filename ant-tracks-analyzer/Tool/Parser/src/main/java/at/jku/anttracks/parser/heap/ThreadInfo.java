package at.jku.anttracks.parser.heap;

import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.symbols.AllocatedType;
import at.jku.anttracks.util.Tuple;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ThreadInfo {
    public final long threadId;
    public final String threadName;
    public final String internalThreadName;
    private boolean alive;

    private final ArrayList<AllocatedType.MethodInfo> callStack;
    private final ArrayList<Tuple<Integer, Integer>> unresolvedCallStack;    // classId, methodId
    private boolean resolved = false;

    public ThreadInfo(long threadId, String threadName, String internalThreadName, boolean alive) {
        this.threadId = threadId;
        this.threadName = threadName;
        this.internalThreadName = internalThreadName;
        this.alive = alive;

        this.callStack = new ArrayList<>();
        this.unresolvedCallStack = new ArrayList<>();
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public void addStackframe(int classId, int methodId) {
        unresolvedCallStack.add(new Tuple<>(classId, methodId));
        resolved = false;
    }

    public void resolveCallStack(DetailedHeap heap) throws Exception {
        callStack.clear();

        for (Tuple<Integer, Integer> frame : unresolvedCallStack) {
            AllocatedType klass = heap.getSymbols().types.getById(frame.a);
            if (klass != null) {
                AllocatedType.MethodInfo resolvedFrame = Arrays.stream(klass.methodInfos).filter(m -> m.idnum == frame.b).findAny().orElse(null);
                if (resolvedFrame == null) {
                    throw new Exception("Could not resolve stackframe with classID " + frame.a + " (" + klass.internalName + ") and methodID " + frame.b + "(unknown method)");
                }
                callStack.add(resolvedFrame);
            } else {
                throw new Exception("Could not resolve stackframe with classID " + frame.a + " (unknown class) and methodID " + frame.b);
            }
        }

        resolved = true;
    }

    public boolean isNewFrame(int classId, int methodId) {
        // tells whether the given class and methodID differ from the last frame of the current callstack
        if (unresolvedCallStack.isEmpty()) {
            return true;
        }

        Tuple<Integer, Integer> lastFrame = unresolvedCallStack.get(unresolvedCallStack.size() - 1);
        return lastFrame.a != classId || lastFrame.b != methodId;
    }

    public int getStackDepth() {
        return unresolvedCallStack.size();
    }

    public void clearCallstack() {
        unresolvedCallStack.clear();
        callStack.clear();
        resolved = false;
    }

    public boolean isResolved() {
        return resolved;
    }

    public List<AllocatedType.MethodInfo> getCallstack() {
        if (!resolved) {
            throw new IllegalStateException("Unresolved callstack! (try calling resolve(Heap) first)");
        }

        return callStack;
    }

    public byte[] getCallstackMetadata() {
        ByteBuffer buf = ByteBuffer.allocate(getStackDepth() * 2 * Integer.BYTES);
        for (Tuple<Integer, Integer> frame : unresolvedCallStack) {
            buf.putInt(frame.a);
            buf.putInt(frame.b);
        }
        return buf.array();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ThreadInfo && internalThreadName.equals(((ThreadInfo) obj).internalThreadName);
    }

    @Override
    public int hashCode() {
        return internalThreadName.hashCode();
    }
}
