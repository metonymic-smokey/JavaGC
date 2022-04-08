
package at.jku.anttracks.heap.statistics;

import at.jku.anttracks.features.FeatureMapCache;
import at.jku.anttracks.heap.objects.ObjectInfo;
import at.jku.anttracks.heap.symbols.Symbols;

public class SpaceStatistics {
    public final MemoryConsumption memoryConsumption;
    public final Allocators allocators;
    public final ObjectTypes objectTypes;
    public final MemoryConsumption[] featureConsumptions;
    private final FeatureMapCache featureCache;

    /**
     * Used when space statistics are read from file
     *
     * @param memoryConsumption
     * @param allocator
     * @param objectTypes
     * @param featureConsumptions
     */
    public SpaceStatistics(MemoryConsumption memoryConsumption, Allocators allocator, ObjectTypes objectTypes, MemoryConsumption[] featureConsumptions) {
        this.memoryConsumption = memoryConsumption;
        this.allocators = allocator;
        this.objectTypes = objectTypes;
        this.featureConsumptions = featureConsumptions;
        this.featureCache = null;
    }

    public SpaceStatistics(int features, FeatureMapCache featureCache) {
        this.memoryConsumption = new MemoryConsumption();
        this.allocators = new Allocators();
        this.objectTypes = new ObjectTypes();
        this.featureCache = featureCache;
        this.featureConsumptions = new MemoryConsumption[features];
        for (int i = 0; i < features; i++) {
            featureConsumptions[i] = new MemoryConsumption();
        }
    }

    public SpaceStatistics(Symbols symbols) {
        this(symbols.features != null ? symbols.features.getFeatureCount() : 0, symbols.featureCache);
    }

    public void add(ObjectInfo obj) {
        switch (obj.eventType) {
            case OBJ_ALLOC_FAST_C1:
            case OBJ_ALLOC_FAST_C1_DEVIANT_TYPE:
            case OBJ_ALLOC_NORMAL_C1:
            case OBJ_ALLOC_SLOW_C1:
            case OBJ_ALLOC_SLOW_C1_DEVIANT_TYPE:
                allocators.incrementC1();
                break;
            case OBJ_ALLOC_FAST_C2:
            case OBJ_ALLOC_FAST_C2_DEVIANT_TYPE:
            case OBJ_ALLOC_NORMAL_C2:
            case OBJ_ALLOC_SLOW_C2:
            case OBJ_ALLOC_SLOW_C2_DEVIANT_TYPE:
                allocators.incrementC2();
                break;
            case OBJ_ALLOC_FAST_IR:
            case OBJ_ALLOC_NORMAL_IR:
            case OBJ_ALLOC_SLOW_IR:
            case OBJ_ALLOC_SLOW_IR_DEVIANT_TYPE:
                allocators.incrementIr();
                break;
            case OBJ_ALLOC_SLOW:
                allocators.incrementVm();
                break;
            default:
                break;
        }

        if (obj.isArray) {
            if (obj.isSmallArray()) {
                objectTypes.incrementSmallArrays(obj.size);
            } else {
                objectTypes.incrementBigArrays(obj.size);
            }
        } else {
            objectTypes.incrementInstances(obj.size);
        }

        memoryConsumption.incrementObjects();
        memoryConsumption.addBytes(obj.size);

        if (featureCache != null) {
            int[] objFeatures = featureCache.match(obj);
            for (int id : objFeatures) {
                featureConsumptions[id].addObjects(1);
                featureConsumptions[id].addBytes(obj.size);
            }
        }
    }

    public void merge(SpaceStatistics other) {
        this.memoryConsumption.addObjects(other.memoryConsumption.getObjects());
        this.memoryConsumption.addBytes(other.memoryConsumption.getBytes());

        this.allocators.addC1(other.allocators.getC1());
        this.allocators.addC2(other.allocators.getC2());
        this.allocators.addIr(other.allocators.getIr());
        this.allocators.addVm(other.allocators.getVm());

        this.objectTypes.addInstances(other.objectTypes.getInstances(), other.objectTypes.getInstancesBytes());
        this.objectTypes.addSmallArrays(other.objectTypes.getSmallArrays(), other.objectTypes.getSmallArraysBytes());
        this.objectTypes.addBigArrays(other.objectTypes.getBigArrays(), other.objectTypes.getBigArraysBytes());

        for (int id = 0; id < featureConsumptions.length; id++) {
            this.featureConsumptions[id].addObjects(other.featureConsumptions[id].getObjects());
            this.featureConsumptions[id].addBytes(other.featureConsumptions[id].getBytes());
        }
    }

}
