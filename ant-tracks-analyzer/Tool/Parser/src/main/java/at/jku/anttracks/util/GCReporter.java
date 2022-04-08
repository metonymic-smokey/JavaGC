
package at.jku.anttracks.util;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class GCReporter {

    private static final long OVERHEAD_REPORT_INTERVAL = 1000 * 60;
    private static final long REPORT_INTERVAL = 1000 / 10;
    private static final Logger LOGGER = Logger.getLogger(GCReporter.class.getSimpleName());

    private static final GCReporter INSTANCE = new GCReporter();

    public static GCReporter getInstance() {
        return INSTANCE;
    }

    private GCReporter() {
        {
            Thread worker = new Thread(this::runReport, "GC Reporter");
            worker.setDaemon(true);
            worker.start();
        }
        {
            Thread worker = new Thread(this::runReportOverview, "GC Overhead Reporter");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private void runReportOverview() {
        final Thread self = Thread.currentThread();
        final GarbageCollectorMXBean[] gcs = ManagementFactory.getGarbageCollectorMXBeans().toArray(new GarbageCollectorMXBean[0]);
        long lastGCCount = 0;
        long lastGCTime = 0;
        long lastReportTime = 0;
        while (!self.isInterrupted()) {
            long count = 0;
            long time = 0;
            for (GarbageCollectorMXBean gc : gcs) {
                count += gc.getCollectionCount();
                time += gc.getCollectionTime();
            }
            if (count > lastGCCount) {
                long reportTime = System.currentTimeMillis();
                double ratio = 1.0 * (time - lastGCTime) / (reportTime - lastReportTime);
                ratio *= 100;
                ratio = ((double) (long) (ratio * 100)) / 100;
                LOGGER.log(Level.INFO, "GC Ratio {0}%", ratio);
                lastGCCount = count;
                lastGCTime = time;
                lastReportTime = reportTime;
            }
            try {
                Thread.sleep(OVERHEAD_REPORT_INTERVAL);
            } catch (InterruptedException e) {
                self.interrupt();
            }
        }
    }

    private void runReport() {
        final Thread self = Thread.currentThread();
        final GarbageCollectorMXBean[] gcs = ManagementFactory.getGarbageCollectorMXBeans().toArray(new GarbageCollectorMXBean[0]);
        final Map<String, MemoryPoolMXBean> pools = CollectionsUtil.map(ManagementFactory.getMemoryPoolMXBeans(), p -> p.getName(), p -> p);
        final long[] counts = new long[gcs.length];
        final long[] times = new long[gcs.length];
        while (!self.isInterrupted()) {
            for (int i = 0; i < gcs.length; i++) {
                GarbageCollectorMXBean gc = gcs[i];
                report(gc, counts[i], times[i], pools);
                counts[i] = gc.getCollectionCount();
                times[i] = gc.getCollectionTime();
            }
            try {
                Thread.sleep(REPORT_INTERVAL);
            } catch (InterruptedException e) {
                self.interrupt();
            }
        }
    }

    private void report(GarbageCollectorMXBean gc, long prevCount, long prevTime, Map<String, MemoryPoolMXBean> pools) {
        long count = gc.getCollectionCount() - prevCount;
        long time = gc.getCollectionTime() - prevTime;
        if (count == 0) {
            // nothing to do
        } else if (count == 1) {
            StringBuilder poolDescription = new StringBuilder();
            boolean first = true;
            for (String poolName : gc.getMemoryPoolNames()) {
                if (first) {
                    first = false;
                } else {
                    poolDescription.append(", ");
                }
                MemoryPoolMXBean pool = pools.get(poolName);
                poolDescription.append(pool.getName());
                poolDescription.append(" ");
                poolDescription.append(pool.getCollectionUsage().getUsed());
                poolDescription.append("b");
            }
            LOGGER.log(Level.FINEST, "{0} GC took {1}[ms] ({2})", new Object[]{gc.getName(), time, poolDescription});
        } else {
            LOGGER.log(Level.FINEST, "{0} GCs took {1}[ms] ({2})", new Object[]{gc.getName(), time});
        }
    }
}
