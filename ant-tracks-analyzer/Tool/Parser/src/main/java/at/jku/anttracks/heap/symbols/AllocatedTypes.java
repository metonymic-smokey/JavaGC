
package at.jku.anttracks.heap.symbols;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class AllocatedTypes implements Iterable<AllocatedType> {

    private static final Logger LOGGER = Logger.getLogger(AllocatedTypes.class.getSimpleName());

    public static final String MIRROR_CLASS_NAME = "Ljava/lang/Class;";
    public static final String MIRROR_CLASS_EXTERNAL_NAME = "java.lang.Class";

    private HashMap<Integer, AllocatedType> allocatedTypesById;
    // Will be initialized during complete()
    private HashMap<String, AllocatedType> allocatedTypesByInternalName;
    private HashMap<String, AllocatedType> allocatedTypesByFullyQualifiedExternalName;

    // Starts at 1 since "UNKNOWN" always exists
    private int count = 1;
    private AllocatedType classKlass = null;

    public AllocatedTypes() {
        allocatedTypesById = new HashMap<>();
        allocatedTypesById.put(AllocatedType.ALLOCATED_TYPE_IDENTIFIER_UNKNOWN,
                               new AllocatedType(AllocatedType.ALLOCATED_TYPE_IDENTIFIER_UNKNOWN,
                                                 AllocatedType.ALLOCATED_TYPE_IDENTIFIER_UNKNOWN,
                                                 String.valueOf(AllocatedType.ALLOCATED_TYPE_IDENTIFIER_UNKNOWN_SIG),
                                                 0));

        allocatedTypesByInternalName = new HashMap<>();
        allocatedTypesByFullyQualifiedExternalName = new HashMap<>();
    }

    public AllocatedType add(int id, AllocatedType allocatedType) throws SymbolsFileException {
        if (allocatedTypesById.get(id) == null) {
            count++;
            allocatedTypesById.put(id, allocatedType);
            if (classKlass == null && allocatedType.internalName.equals(MIRROR_CLASS_NAME)) {
                // Remember Class<> class
                classKlass = allocatedType;
            }
        } else {
            throw new SymbolsFileException(String.format("Duplicate entry in symbols file for type ID %d, Old Type: %s, Duplicate Type: %s",
                                                         id,
                                                         allocatedType,
                                                         allocatedTypesById.get(id)));
        }
        return allocatedType;
    }

    public void complete() {
        assert classKlass != null : "Class class has to exist after symbols parsing";
        classKlass.complete(this);

        // Convert class type into mirror class
        AllocatedType classOfClass = new AllocatedType.MirrorAllocatedType(classKlass, classKlass);
        allocatedTypesById.put(classOfClass.id, classOfClass);
        classOfClass.complete(this);

        allocatedTypesById.put(AllocatedType.ALLOCATED_TYPE_IDENTIFIER_UNKNOWN_MIRROR, new AllocatedType.MirrorAllocatedType(classKlass, null));

        HashMap<Integer, AllocatedType> mirrorTypes = new HashMap<>();
        allocatedTypesById.forEach((id, type) -> {
            type.complete(this);

            if (id != AllocatedType.ALLOCATED_TYPE_IDENTIFIER_UNKNOWN && id != AllocatedType.ALLOCATED_TYPE_IDENTIFIER_UNKNOWN_MIRROR && type != classKlass && type != classOfClass) {
                AllocatedType.MirrorAllocatedType mirrorType = new AllocatedType.MirrorAllocatedType(classKlass, type);
                mirrorTypes.put(-id, mirrorType);
            }
        });

        mirrorTypes.forEach((id, type) -> allocatedTypesById.put(id, type));

        int nextId = allocatedTypesById.keySet().stream().mapToInt(x -> x).max().orElse(1) + 1;
        allocatedTypesById.put(nextId, new AllocatedType(nextId, -1, "B", 0));
        nextId++;
        allocatedTypesById.put(nextId, new AllocatedType(nextId, -1, "C", 0));
        nextId++;
        allocatedTypesById.put(nextId, new AllocatedType(nextId, -1, "D", 0));
        nextId++;
        allocatedTypesById.put(nextId, new AllocatedType(nextId, -1, "F", 0));
        nextId++;
        allocatedTypesById.put(nextId, new AllocatedType(nextId, -1, "I", 0));
        nextId++;
        allocatedTypesById.put(nextId, new AllocatedType(nextId, -1, "J", 0));
        nextId++;
        allocatedTypesById.put(nextId, new AllocatedType(nextId, -1, "S", 0));
        nextId++;
        allocatedTypesById.put(nextId, new AllocatedType(nextId, -1, "Z", 0));
        nextId++;
        allocatedTypesById.put(nextId, new AllocatedType(nextId, -1, "V", 0));

        allocatedTypesById.forEach((id, type) -> {
            type.initHasUnknownPointerCount(this);
            allocatedTypesByInternalName.put(type.internalName, type);
            if(allocatedTypesByFullyQualifiedExternalName.containsKey(type.getFullyQualifiedName(true))) {
                System.err.println("Found two types with same fully qualified name! External Name: " + type.getFullyQualifiedName(true) + ", Internal name: " + type.internalName);
                AllocatedType originalType = allocatedTypesByFullyQualifiedExternalName.get(type.getFullyQualifiedName(true));
                allocatedTypesById.replace(id, originalType);
            } else {
                allocatedTypesByFullyQualifiedExternalName.put(type.getFullyQualifiedName(true), type);
            }
        });

        fixFields();

        // fix superIds and -types of primitive array types
        int javaLangObjectTypeId = allocatedTypesByFullyQualifiedExternalName.get("java.lang.Object").id;
        allocatedTypesById.forEach((id, type) -> {
            if (id == javaLangObjectTypeId) {
                assert type.getSuperId() == AllocatedType.ALLOCATED_TYPE_IDENTIFIER_UNKNOWN : "java.lang.Object type must have U type as super";

            } else if (type.isPrimitiveArray()) {
                type.setSuperId(javaLangObjectTypeId);
                type.resolveSuperType(this);
            }
        });
    }

    private void fixFields() {
        for (AllocatedType type : this) {
            for (AllocatedType.FieldInfo field : type.fieldInfos) {
                assert field != null : "Field info may not be null";
            }
        }

        List<String> nonFoundTypeMessages = new ArrayList<>();

        for (AllocatedType type : this) {
            for (AllocatedType.FieldInfo field : type.fieldInfos) {
                AllocatedType typeByName = allocatedTypesByInternalName.get(field.signature);
                if (typeByName == null) {
                    nonFoundTypeMessages.add("Found field of type '" + field.signature + "' but no matching symbols entry. This may be because this type has never been" +
                                                     " loaded (i.e., not instantiated once)");
                    field.setTypeId(AllocatedType.ALLOCATED_TYPE_IDENTIFIER_UNKNOWN);
                } else {
                    field.setTypeId(typeByName.id);
                }
            }
        }

        /*
        nonFoundTypeMessages.stream().distinct().forEach(LOGGER::info);
        */
    }

    public AllocatedType getById(int id) {
        return allocatedTypesById.get(id);
    }

    public AllocatedType getByInternalName(String internalName) {
        return allocatedTypesByInternalName.get(internalName);
    }

    public AllocatedType getByFullyQualifiedExternalName(String externalName) {
        return allocatedTypesByFullyQualifiedExternalName.get(externalName);
    }

    public int getLength() {
        return count;
    }

    @Override
    public Iterator<AllocatedType> iterator() {
        return allocatedTypesById.values().iterator();
    }

    public Stream<AllocatedType> stream() {
        return allocatedTypesById.values().stream();
    }
}
