
package at.jku.anttracks.util;

import io.micrometer.core.instrument.Metrics;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ApplicationStatistics {

    public static class Measurement {
        private final Thread creatingThread;
        private long start;
        private final String stat;
        private final String[] additionalInfo;
        private long end;
        private final MeasurementGroup group;

        private Measurement(String stat, long start, MeasurementGroup group, String... additionalInfo) {
            this.creatingThread = Thread.currentThread();
            this.stat = stat;
            this.start = start;
            end = -1;
            this.additionalInfo = additionalInfo;
            this.group = group;
        }

        public void end() {
            end = System.nanoTime();
            ApplicationStatistics.getInstance().addMeasurement(this);
        }

        public boolean isComplete() {
            return end != -1;
        }

        public long getDuration() {
            return end - start;
        }

        public String getStat() {
            return stat;
        }

        @Override
        public String toString() {
            String splitter = new String(new char[stat.length() <= 40 ? 40 - stat.length() : 1]).replace("\0", " ");
            if (isComplete()) {
                return String.format("Completed%s%s:\t%,dns\t(%s)", splitter, stat, getDuration(), Arrays.toString(additionalInfo));
            } else {
                return String.format("Running%s%s:\t%,dns\t(%s)", splitter, stat, getDuration(), Arrays.toString(additionalInfo));
            }
        }

        public Thread getCreatingThread() {
            return creatingThread;
        }
    }

    private static class MeasurementGroup {
        private final String stat;
        private final List<MeasurementGroup> children = new java.util.ArrayList<>();
        private final MeasurementGroup parent;
        private final List<Measurement> measurements = new ArrayList<>();
        private final Map<String, MutableLong> counter = new HashMap<>();

        private MeasurementGroup(String stat, MeasurementGroup parent) {
            this.stat = stat;
            this.parent = parent;
        }

        public boolean containsChild(String stat) {
            return children.stream().anyMatch(x -> x.stat.equals(stat));
        }

        public void addChild(String stat) {
            children.add(new MeasurementGroup(stat, this));
        }

        public MeasurementGroup getChild(String stat) {
            return children.stream().filter(x -> x.stat.equals(stat)).findFirst().orElse(null);
        }

        public void addMeasurement(Measurement m) {
            measurements.add(m);
        }

        public int getLevel() {
            int level = 0;
            MeasurementGroup current = this;
            while (current.parent != null) {
                level++;
                current = current.parent;
            }
            return level;
        }

        @Override
        public String toString() {
            String s = String.format("%-65s | Count: %,7d\t| Sum: %,10.2fms\t| Average: %,10.2fms\t|",
                                     new String(new char[getLevel() + 1]).replace("\0", "-") + " " + stat,
                                     getAmount(),
                                     getSum() / 1000.0 / 1000.0,
                                     getAverage() / 1000.0 / 1000.0);
            for (Entry<String, MutableLong> counterEntry : counter.entrySet()
                                                                  .stream()
                                                                  .sorted(Comparator.comparing((Entry<String, MutableLong> e) -> e.getKey()).reversed())
                                                                  .collect(Collectors.toList())) {
                s += "\n";
                s += String.format("%-65s | Count: %,7d\t|",
                                   new String(new char[getLevel() + 2]).replace("\0", ">") + " " + counterEntry.getKey(),
                                   counterEntry.getValue().get());
            }
            for (MeasurementGroup child : children.stream().sorted(Comparator.comparingLong(MeasurementGroup::getSum).reversed()).collect(Collectors.toList())) {
                s += "\n";
                s += child.toString();
            }
            return s;
        }

        public long getAmount() {
            synchronized (measurements) {
                return measurements.stream().filter(m -> m.isComplete()).count();
            }
        }

        public double getAverage() {
            synchronized (measurements) {
                return measurements.stream().filter(m -> m.isComplete()).mapToLong(m -> m.getDuration()).average().orElse(0);
            }
        }

        public long getSum() {
            synchronized (measurements) {
                return measurements.stream().filter(m -> m.isComplete()).mapToLong(m -> m.getDuration()).sum();
            }
        }

        public void inc(String stat, long inc) {
            MutableLong c = counter.get(stat);
            if (c == null) {
                counter.put(stat, new MutableLong());
                c = counter.get(stat);
            }
            c.add(inc);
        }
    }

    HashMap<Thread, MeasurementGroup> groups = new HashMap<>();

    private final Map<String, List<Measurement>> measurements = new HashMap<>();

    // Singleton
    private static ApplicationStatistics INSTANCE;
    private static Logger LOGGER = Logger.getLogger(ApplicationStatistics.class.getSimpleName());
    private static long firstAccessTime = -1;

    private ApplicationStatistics() {
        firstAccessTime = System.currentTimeMillis();
    }

    public synchronized static ApplicationStatistics getInstance() {
        if (ApplicationStatistics.INSTANCE == null) {
            ApplicationStatistics.INSTANCE = new ApplicationStatistics();
        }
        return ApplicationStatistics.INSTANCE;
    }

    public synchronized Measurement createMeasurement(String stat, String... additionalInfo) {
        synchronized (groups) {
            if (!groups.containsKey(Thread.currentThread())) {
                groups.put(Thread.currentThread(), new MeasurementGroup("root", null));
            }
            MeasurementGroup currentGroup = groups.get(Thread.currentThread());
            if (!currentGroup.containsChild(stat)) {
                currentGroup.addChild(stat);
            }
            currentGroup = currentGroup.getChild(stat);
            groups.put(Thread.currentThread(), currentGroup);
            Measurement m = new Measurement(stat, System.nanoTime(), currentGroup, additionalInfo);
            currentGroup.addMeasurement(m);
            return m;
        }
    }

    public synchronized void addCompletedMeasurement(String stat, long duration, String... additionalInfo) {
        synchronized (groups) {
            if (!groups.containsKey(Thread.currentThread())) {
                groups.put(Thread.currentThread(), new MeasurementGroup("root", null));
            }
            MeasurementGroup currentGroup = groups.get(Thread.currentThread());
            if (!currentGroup.containsChild(stat)) {
                currentGroup.addChild(stat);
            }
            currentGroup = currentGroup.getChild(stat);
            Measurement m = new Measurement(stat, System.nanoTime(), currentGroup, additionalInfo);
            m.end();
            m.end = System.nanoTime();
            m.start = m.end - duration;
            currentGroup.addMeasurement(m);
        }
    }

    private synchronized void addMeasurement(Measurement m) {
        synchronized (measurements) {
            measurements.putIfAbsent(m.stat, new ArrayList<>());
            measurements.get(m.stat).add(m);
            synchronized (groups) {
                groups.put(Thread.currentThread(), m.group.parent);
            }
            Metrics.timer(m.stat).record(Duration.ofNanos(m.getDuration()));
        }
    }

    public synchronized void inc(String stat) {
        inc(stat, 1);
    }

    public synchronized void inc(String stat, long inc) {
        if (!groups.containsKey(Thread.currentThread())) {
            groups.put(Thread.currentThread(), new MeasurementGroup("root", null));
        }
        groups.get(Thread.currentThread()).inc(stat, inc);
        Metrics.counter(stat).increment(inc);
    }

    public double getAmount(String stat) {
        synchronized (measurements) {
            if (!measurements.containsKey(stat)) {
                return 0;
            }
            return measurements.get(stat).stream().filter(m -> m.isComplete()).count();
        }
    }

    public double getAverage(String stat) {
        synchronized (measurements) {
            if (!measurements.containsKey(stat)) {
                return -1.0;
            }
            return measurements.get(stat).stream().filter(m -> m.isComplete()).mapToLong(m -> m.getDuration()).average().orElse(0);
        }
    }

    public long getSum(String stat) {
        synchronized (measurements) {
            if (!measurements.containsKey(stat)) {
                return -1;
            }
            return measurements.get(stat).stream().filter(m -> m.isComplete()).mapToLong(m -> m.getDuration()).sum();
        }
    }

    public List<Measurement> getMeasurements(String stat) {
        synchronized (measurements) {
            if (!measurements.containsKey(stat)) {
                return new ArrayList<>();
            }
            return measurements.get(stat);
        }
    }

    @SuppressWarnings("unchecked")
    public void print() {
        System.out.println(String.format("First measurement taken %,dms ago", System.currentTimeMillis() - firstAccessTime));
        System.out.println();
        HashMap<Thread, MeasurementGroup> g = new HashMap<>();
        synchronized (measurements) {
            synchronized (groups) {
                groups.forEach((thread, m) -> {
                    while (m.parent != null) {
                        m = m.parent;
                    }
                    g.put(thread, m);
                });

                System.out.println(treeString(g));
                System.out.println();
                System.out.println(tableString());
            }
        }
    }

    public String treeString(HashMap<Thread, MeasurementGroup> g) {
        StringBuilder sb = new StringBuilder();
        for (Entry<Thread, MeasurementGroup> groupEntry : g.entrySet()) {
            sb.append(String.format("== %s ==", groupEntry.getKey()));
            sb.append("\n");
            sb.append(groupEntry.getValue());
            sb.append("\n");
        }
        return sb.toString();
    }

    public String tableString() {
        String seperator = ";";
        String newLine = "\n";

        StringBuilder sb = new StringBuilder();

        synchronized (measurements) {

            sb.append("Counter:");
            sb.append(newLine);
            sb.append("Name");
            sb.append(seperator);
            sb.append("Count");
            sb.append(newLine);
/*                for (Entry<String, MutableLong> entry : counter.entrySet()) {
                    writer.write(entry.getKey());
                    writer.write(seperator);
                    writer.write(String.valueOf(entry.getValue().get()));
                    writer.write("\n");
                }*/
            sb.append("Measurements:");
            sb.append(newLine);
            sb.append("Name");
            sb.append(seperator);
            sb.append("Count");
            sb.append(seperator);
            sb.append("Sum [ms]");
            sb.append(seperator);
            sb.append("AVG [ms]");
            sb.append(seperator);
            sb.append("All");
            sb.append(newLine);
            for (Entry<String, List<Measurement>> entry : measurements.entrySet()) {
                String name = entry.getKey();
                sb.append(name);
                sb.append(seperator);
                sb.append(String.valueOf(getAmount(name)));
                sb.append(seperator);
                sb.append(String.valueOf(getSum(name) / 1_000_000));
                sb.append(seperator);
                sb.append(String.valueOf(getAverage(name) / 1_000_000));
                /*
                 * for (Measurement m : entry.getValue()) { writer.write(seperator); writer.write(m.toString()); }
                 */
                sb.append(newLine);
            }
        }
        return sb.toString();
    }

    public void export(File file) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(tableString());
        } catch (Exception e) {
            LOGGER.warning("Error on exporting application statistics: " + e);
        }
    }
}
