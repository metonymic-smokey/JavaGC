
package at.jku.anttracks.heap.io;

import at.jku.anttracks.heap.GarbageCollectionCause;
import at.jku.anttracks.heap.GarbageCollectionType;
import at.jku.anttracks.heap.StatisticGCInfo;
import at.jku.anttracks.heap.statistics.Allocators;
import at.jku.anttracks.heap.statistics.MemoryConsumption;
import at.jku.anttracks.heap.statistics.ObjectTypes;
import at.jku.anttracks.heap.statistics.SpaceStatistics;
import at.jku.anttracks.heap.statistics.Statistics;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.parser.EventType;
import at.jku.anttracks.parser.io.BaseFile;
import at.jku.anttracks.util.Consts;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StatisticsReader implements AutoCloseable {
    private static Logger LOGGER = Logger.getLogger(StatisticsReader.class.getSimpleName());

    private final DataInputStream in;

    public StatisticsReader(String path) throws IOException {
        this.in = new DataInputStream(new BufferedInputStream(BaseFile.openR(path + File.separator + Consts.STATISTICS_META_FILE)));
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    public ArrayList<Statistics> read(Symbols symbols) throws IOException {
        ArrayList<Statistics> statistics = new ArrayList<>();

        int magic = in.readInt();
        assert magic == Consts.HEAP_FILES_MAGIC_PREFIX;
        try {
            while (true) {
                short id = in.readShort();
                long time = in.readLong();
                EventType meta = EventType.Companion.parse(in.readInt());
                GarbageCollectionType type = GarbageCollectionType.Companion.parse(in.readInt());
                GarbageCollectionCause cause = symbols.causes.get(in.readInt());
                boolean concurrent = in.readBoolean();
                boolean failed = in.readBoolean();
                long reachableBytes = in.readLong();
                StatisticGCInfo info = new StatisticGCInfo(type,
                                                           cause,
                                                           concurrent,
                                                           failed,
                                                           meta,
                                                           id,
                                                           time,
                                                           reachableBytes == -1 ? null : reachableBytes);

                SpaceStatistics eden = readSpaceStatistics();
                SpaceStatistics survivor = readSpaceStatistics();
                SpaceStatistics old = readSpaceStatistics();
                statistics.add(new Statistics(info, eden, survivor, old));
            }
        } catch (EOFException e) {
            in.close();
        }

        return statistics;
    }

    private SpaceStatistics readSpaceStatistics() throws IOException {
        Allocators allocator = readAllocators();
        MemoryConsumption memoryConsumption = readMemoryConsumption();
        ObjectTypes objectTypes = readObjectTypes();
        MemoryConsumption[] featureConsumption = readFeatureAllocation();

        return new SpaceStatistics(memoryConsumption, allocator, objectTypes, featureConsumption);
    }

    private Allocators readAllocators() throws IOException {
        long vm = in.readLong();
        long ir = in.readLong();
        long c1 = in.readLong();
        long c2 = in.readLong();
        return new Allocators(vm, ir, c1, c2);
    }

    private MemoryConsumption readMemoryConsumption() throws IOException {
        long objects = in.readLong();
        long bytes = in.readLong();
        return new MemoryConsumption(objects, bytes);
    }

    private ObjectTypes readObjectTypes() throws IOException {
        long instances = in.readLong();
        long smallArrays = in.readLong();
        long bigArrays = in.readLong();

        return new ObjectTypes(instances, smallArrays, bigArrays);
    }

    private MemoryConsumption[] readFeatureAllocation() throws IOException {
        if (in.readBoolean()) {
            int f = in.readInt();
            MemoryConsumption[] consumptions = new MemoryConsumption[f];
            for (int i = 0; i < f; i++) {
                long objects = in.readLong();
                long bytes = in.readLong();
                consumptions[i] = new MemoryConsumption(objects, bytes);
            }
            return consumptions;
        }
        return null;
    }

    public static List<Statistics> readStatisticsFromMetadata(String fullMetaDataPath, Symbols symbols) {
        List<Statistics> statistics = null;
        try {
            try (StatisticsReader statisticsReader = new StatisticsReader(fullMetaDataPath)) {
                statistics = statisticsReader.read(symbols);
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "reading meta data failed (this may be due to a change in the meta data format) -> reparse");
            statistics = null;
        }

        return statistics;
    }
}
