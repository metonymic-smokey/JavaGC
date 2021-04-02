package at.jku.anttracks.util;

import java.io.PrintStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Stack;
import java.util.stream.Collectors;

public class Profiler implements AutoCloseable {

    private final String name;

    private final int samplingIntervalMillis;

    private final ThreadMXBean bean = ManagementFactory.getThreadMXBean();

    private final Map<Long, ThreadData> threads = new HashMap<>();

    private final Thread thread = new Thread(this::run, "Profiler");

    public Profiler(String name) {
        this(name, 10);
    }

    private Profiler(String name, int samplingIntervalMillis) {
        this.name = name;
        this.samplingIntervalMillis = samplingIntervalMillis;
        if (samplingIntervalMillis == 0) {
            return;
        }
        bean.setThreadCpuTimeEnabled(true);
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void close() {
        if (samplingIntervalMillis == 0) {
            return;
        }
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException e) {
            //profiler should not impede anything
        }
        print();
    }

    private void run() {
        Random random = new Random();
        Thread self = Thread.currentThread();
        long lastWallTime = System.nanoTime();
        long lastGcTime = getGCTime();
        while (!self.isInterrupted()) {
            try {
                Thread.sleep(random.nextInt(samplingIntervalMillis * 2));
                long wallTime = System.nanoTime();
                long gcTime = getGCTime();
                for (ThreadInfo info : bean.dumpAllThreads(false, false)) {
                    process(info, wallTime - lastWallTime, gcTime - lastGcTime);
                }
                lastWallTime = wallTime;
                lastGcTime = gcTime;
            } catch (InterruptedException e) {
                self.interrupt();
            }
        }
    }

    private long getGCTime() {
        long millis = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            millis += gc.getCollectionTime();
        }
        return millis * 1000;
    }

    private void process(ThreadInfo info, long wallTime, long gcTime) {
        long id = info.getThreadId();
        ThreadData data = threads.get(id);
        if (data == null) {
            data = new ThreadData(id, info.getThreadName());
            threads.put(id, data);
        }

        long cpuTime = getCpuTimeSinceLastSample(id, data);

        StackTraceElement[] stack = info.getStackTrace();
        Map<NodeKey, NodeValue> next = data.roots;
        for (int index = stack.length - 1; 0 <= index; index--) {
            StackTraceElement frame = stack[index];
            NodeKey key = new NodeKey(frame);
            NodeValue node = next.get(key);
            if (node == null) {
                node = new NodeValue(key);
                next.put(key, node);
            }
            node.count++;
            node.wallTime += wallTime;
            if (index == 0) {
                node.cpuTime += cpuTime;
                node.gcTime += gcTime;
            }
            next = node.children;
        }
    }

    private long getCpuTimeSinceLastSample(long id, ThreadData data) {
        long totalCpuTime = bean.getThreadCpuTime(id);
        long cpuTime = totalCpuTime - data.lastCpuTime;
        data.lastCpuTime = totalCpuTime;
        return cpuTime;
    }

    private void print() {
        PrintStream out = System.err;
        out.println("######################################### " + name + " #########");
        for (ThreadData thread : threads.values()) {
            print(out, thread);
        }
    }

    private void print(PrintStream out, ThreadData thread) {
        out.println("######## " + thread.id + ": " + thread.name + " #########");
        Map<NodeKey, NodeValue> bottomUp = reverse(thread);
        print(out, "", bottomUp);
    }

    private void print(PrintStream out, String indent, Map<NodeKey, NodeValue> data) {
        for (NodeValue node : data.values().stream().sorted((n1, n2) -> -Long.valueOf(n1.cpuTime).compareTo(n2.cpuTime)).limit(3).collect(Collectors.toList())) {
            out.println(indent + node.key + " (" + node.count + "x, " + (node.wallTime / 1_000_000_000.0) + " wall, " + (node.cpuTime / 1_000_000_000.0) + " cpu, " +
                                (node.gcTime / 1_000_000_000.0) + " gc" + ")");
            print(out, indent + " ", node.children);
        }
    }

    private Map<NodeKey, NodeValue> reverse(ThreadData thread) {
        Map<NodeKey, NodeValue> bottomUp = new HashMap<>();
        Stack<NodeValue> stack = new Stack<NodeValue>();
        for (NodeValue node : thread.roots.values()) {
            reverse(bottomUp, stack, node);
        }
        return bottomUp;
    }

    private void reverse(Map<NodeKey, NodeValue> roots, Stack<NodeValue> stack, NodeValue node) {
        stack.push(node);
        if (node.cpuTime > 0) {
            add(roots, stack);
        }
        for (NodeValue child : node.children.values()) {
            reverse(roots, stack, child);
        }
        stack.pop();
    }

    private void add(Map<NodeKey, NodeValue> data, Stack<NodeValue> stack) {
        for (int index = stack.size() - 1; index >= 0; index--) {
            NodeValue node = stack.get(index);
            NodeValue clone = data.get(node.key);
            if (clone == null) {
                clone = new NodeValue(node.key);
                data.put(clone.key, clone);
            }
            clone.count += node.count;
            clone.wallTime += node.wallTime;
            clone.cpuTime += node.cpuTime;
            clone.gcTime += node.gcTime;
            data = clone.children;
        }
    }

    private static class ThreadData {
        public final long id;
        public final String name;
        public long lastCpuTime;

        public final Map<NodeKey, NodeValue> roots = new HashMap<>(4);

        public ThreadData(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static class NodeKey {
        public final String clazz;
        public final String method;
        public final int line;

        public NodeKey(String clazz, String method, int line) {
            this.clazz = clazz;
            this.method = method;
            this.line = line;
        }

        public NodeKey(StackTraceElement element) {
            this(element.getClassName(), element.getMethodName(), element.getLineNumber());
        }

        @Override
        public int hashCode() {
            return clazz.hashCode() ^ method.hashCode() ^ line;
        }

        @Override
        public boolean equals(Object that) {
            return that != null && that.getClass() == NodeKey.class && equals((NodeKey) that);
        }

        public boolean equals(NodeKey that) {
            return that != null && this.clazz.equals(that.clazz) && this.method.equals(that.method) && this.line == that.line;
        }

        @Override
        public String toString() {
            return clazz + "::" + method + "(L" + line + ")";
        }
    }

    private static class NodeValue {
        public final NodeKey key;

        public long count = 0;
        public long wallTime = 0;
        public long cpuTime = 0;
        public long gcTime = 0;
        public final Map<NodeKey, NodeValue> children = new HashMap<>(4);

        public NodeValue(NodeKey key) {
            this.key = key;
        }
    }

}
