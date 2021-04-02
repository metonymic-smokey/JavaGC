
package at.jku.anttracks.heap.symbols;

import at.jku.anttracks.heap.datastructures.dsl.DSLDSLayout;
import at.jku.anttracks.util.SignatureConverter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.regex.Pattern;

import static at.jku.anttracks.heap.symbols.AllocatedTypes.MIRROR_CLASS_NAME;

public class AllocatedType {
    public static class MirrorAllocatedType extends AllocatedType {
        public final AllocatedType mirrorKlass;

        public MirrorAllocatedType(AllocatedType classClass, AllocatedType mirrorKlass) {
            // Clone Class<> class
            super(mirrorKlass != null ? -mirrorKlass.id : AllocatedType.ALLOCATED_TYPE_IDENTIFIER_UNKNOWN_MIRROR, classClass.superId, classClass.internalName, classClass.size);
            this.fieldInfos = Arrays.copyOf(classClass.fieldInfos, classClass.fieldInfos.length + (mirrorKlass == null ? 0 : mirrorKlass.fieldInfos.length));
            if (mirrorKlass != null) {
                System.arraycopy(mirrorKlass.fieldInfos, 0, this.fieldInfos, classClass.fieldInfos.length, mirrorKlass.fieldInfos.length);
            }
            completeFieldInfo();
            pointersPerObject = classClass.pointersPerObject;
            if (mirrorKlass != null) {
                pointersPerObject += (int) Arrays.stream(mirrorKlass.fieldInfos).filter(x -> x.isStatic()).filter(x -> x.isInstance() || x.isArray()).count();
            }
            this.fieldIndex = classClass.fieldIndex;
            this.methodInfos = classClass.methodInfos;
            this.methodIndex = classClass.methodIndex;

            // Set mirrored class
            this.mirrorKlass = mirrorKlass;
        }

        public String getExternalName(boolean omitPackage, boolean showMirrorType) {
            if (!showMirrorType) {
                return super.getExternalName(omitPackage, showMirrorType);
            } else {
                return super.getExternalName(omitPackage, showMirrorType) + "<" + (mirrorKlass == null ?
                                                                                   "unknown" :
                                                                                   mirrorKlass.getExternalName(omitPackage, showMirrorType)) + ">";
            }
        }

        @Override
        protected boolean hasUnknownPointerCount(AllocatedTypes types) {
            return true;
        }
    }

    public static final int MAGIC_BYTE = 24;
    public static final int MAGIC_BYTE_TYPE_FIELD_INFO = 56;
    public static final int MAGIC_BYTE_TYPE_SUPER_FIELD_INFO = 57;
    public static final int MAGIC_BYTE_TYPE_METHOD_INFO = 58;
    public static final int ALLOCATED_TYPE_IDENTIFIER_UNKNOWN = 0;
    // Objects with ID 1 are class objects of which we do not know the mirror class
    public static final int ALLOCATED_TYPE_IDENTIFIER_UNKNOWN_MIRROR = 1;
    public static final char ALLOCATED_TYPE_IDENTIFIER_UNKNOWN_SIG = 'U';

    public final int id;
    private int superId;
    public final String internalName;
    public boolean hasUnknownPointerCount;
    public int size;
    public int pointersPerObject;
    protected FieldInfo[] fieldInfos;
    protected int fieldIndex;
    protected final String externalName;
    protected final String shortExternalName;
    public MethodInfo[] methodInfos;
    protected int methodIndex;

    private AllocatedType superType;

    public DSLDSLayout dataStructureLayout;
    public boolean isPossibleDomainType;

    public AllocatedType(int id, int superId, String internalName, int size) {
        this.id = id;
        this.superId = superId;
        this.internalName = internalName;
        this.size = size;
        this.externalName = SignatureConverter.convertToJavaType(internalName).intern();
        if (externalName.contains(".")) {
            shortExternalName = externalName.substring(externalName.lastIndexOf('.') + 1).intern();
        } else {
            shortExternalName = externalName;
        }
        isPossibleDomainType = TypeAndPackageHelperKt.getExternalNameIsPossibleDomainType(externalName);
        createOrExpandFieldInfos(0);

        // TODO: DSL Datastructures are deactivated at the moment
        // By default has an empty data structure definition
        // this.dataStructureLayout = new DSLDSLayout(this, new AllocatedType[0], new AllocatedType[0], false);
    }

    public static class FieldInfo {
        public final int offset;
        public final String name;
        public final String signature;
        private int typeId;
        public final int flags;
        public final boolean supr;

        public FieldInfo(boolean supr, int offset, String signature, String name, int flags) {
            this.supr = supr;
            this.offset = offset;
            this.signature = signature;
            this.name = name;
            this.flags = flags;
        }

        public boolean isStatic() {
            return (flags & (1L << 3)) != 0;
        }

        public boolean isArray() {
            return signature.startsWith("[");
        }

        public boolean isInstance() {
            return signature.startsWith("L");
        }

        public int getSize() {
            switch (signature) {
                case "B":
                    return 1;
                case "C":
                    return 2;
                case "D":
                    return 8;
                case "F":
                    return 4;
                case "I":
                    return 4;
                case "J":
                    return 8;
                case "S":
                    return 2;
                case "Z":
                    return 1;
                case "V":
                    return 0;
                default:
                    // TODO: Return pointer size depending on compressed oops
                    return 4;
            }
        }

        @Override
        public String toString() {
            return signature + " " + name + "(Offset: " + offset + ", Inherited: " + supr + ", Access: " + flags + "[Instance: " + isInstance() + ", Static:" +
                    isStatic() + "])";
        }

        public void setTypeId(int typeId) {
            this.typeId = typeId;
        }

        public int getTypeId() {
            return typeId;
        }
    }

    public static class MethodInfo {
        public final int idnum;
        public final String name;
        public final Map<Integer, String> locals;
        public final AllocatedType type;

        public MethodInfo(int idnum, String name, Map<Integer, String> locals, AllocatedType type) {
            this.idnum = idnum;
            this.name = name;
            this.locals = locals;
            this.type = type;
        }

        public String toString() {
            return idnum + " : " + name;
        }
    }

    public void addFieldInfo(boolean supr, int offset, String signature, String name, int flags) {
        FieldInfo info = new FieldInfo(supr, offset, signature, name, flags);
        fieldInfos[fieldIndex++] = info;
    }

    public void addMethodInfo(int idnum, String name, Map<Integer, String> locals) {
        MethodInfo info = new MethodInfo(idnum, name, locals, this);
        methodInfos[methodIndex++] = info;
    }

    public FieldInfo[] getFieldInfos() {
        return fieldInfos;
    }

    public void complete(AllocatedTypes types) {
        completeFieldInfo();
        resolveSuperType(types);
    }

    public void resolveSuperType(AllocatedTypes types) {
        // resolve superId to AllocatedType
        superType = superId > 0 ? types.getById(superId) : null; // no super type for java.lang.Object
    }

    protected void completeFieldInfo() {
        fieldInfos =
                Arrays.stream(fieldInfos).sorted(Comparator.comparingInt(o -> o.offset)).toArray(FieldInfo[]::new);
        pointersPerObject =
                (int) Arrays.stream(fieldInfos).filter(x -> !x.isStatic() || internalName.equals(MIRROR_CLASS_NAME)).filter(x -> x.isInstance() || x.isArray()).count();
    }

    public void createOrExpandFieldInfos(int length) {
        if (fieldInfos == null) {
            fieldInfos = new FieldInfo[length];
        } else {
            fieldInfos = Arrays.copyOf(fieldInfos, fieldInfos.length + length);
        }
    }

    public void createOrExandMethodInfos(int length) {
        if (methodInfos == null) {
            methodInfos = new MethodInfo[length];
        } else {
            methodInfos = Arrays.copyOf(methodInfos, methodInfos.length + length);
        }
    }

    @Override
    public String toString() {
        return internalName;
    }

    public void initHasUnknownPointerCount(AllocatedTypes types) {
        this.hasUnknownPointerCount = hasUnknownPointerCount(types);
    }

    protected boolean hasUnknownPointerCount(AllocatedTypes types) {
        AllocatedType cur = this;
        do {
            if (cur.internalName.equals("Ljava/lang/ref/Reference;")) {
                // Classes that inherit from Reference may have incomplete
                // pointer information, see "Efficient Memory Traces with Full
                // Pointer Information", see section 3.3
                return true;
            }
            if (cur.internalName.equals("Ljava/lang/invoke/MemberName;")) {
                // Don't know yet why they are wrong, issue on YouTack: AT-90
                return true;
            }
            if (cur.superId > ALLOCATED_TYPE_IDENTIFIER_UNKNOWN) {
                cur = types.getById(cur.superId);
            } else {
                cur = null;
            }
        } while (cur != null);
        return false;
    }

    public String getFullyQualifiedName() {
        return getFullyQualifiedName(false);
    }

    public String getFullyQualifiedName(boolean showMirrorType) {
        return getExternalName(false, showMirrorType);
    }

    public String getSimpleName() {
        return getSimpleName(false);
    }

    public String getSimpleName(boolean showMirrorType) {
        return getExternalName(true, showMirrorType);
    }

    public String getExternalName(boolean omitPackage, boolean showMirrorType) {
        if (omitPackage) {
            return shortExternalName;
        }
        return externalName;
    }

    public int getSuperId() {
        return superId;
    }

    public void setSuperId(int superId) {
        this.superId = superId;
    }

    /**
     * Tells whether this AllocatedType is a (direct or indirect) subtype of an allocated type with the given name
     *
     * @param externalName Name of the allocated type including package and mirrortype (AllocatedType.getExternalName(false, true))
     * @return true if there is an AllocatedType with the given name that is a supertype of this AllocatedType, false otherwise
     */
    public boolean isSubtypeOf(String externalName) {
        for (AllocatedType cur = superType; cur != null; cur = cur.superType) {
            if (cur.getExternalName(false, true).equals(externalName)) {
                return true;
            }
        }

        return false;
    }

    public boolean isAssignableTo(AllocatedType fieldType) {
        AllocatedType currentType = this;
        do {
            // my type matches the given type
            if (fieldType.id == currentType.id) {
                return true;
            }
            // was no match, check if my parent type is matching
            currentType = currentType.superType;
        } while (currentType != null);
        return false;
    }

    /**
     * Tells whether this AllocatedType is a (direct or indirect) subtype of an allocated type whose name matches the given pattern
     *
     * @param pattern pattern that the supertype's name (including package and mirrortype) must match
     * @return true if there is an AllocatedType whose name matches the pattern and is a supertype of this AllocatedType, false otherwise
     */
    public boolean isSubtypeOf(Pattern pattern) {
        for (AllocatedType cur = superType; cur != null; cur = cur.superType) {
            if (pattern.matcher(cur.getFullyQualifiedName(true)).matches()) {
                return true;
            }
        }

        return false;
    }

    public boolean isPrimitiveArray() {
        // Basic data types have a name of length 1 (e.g., C)
        // Basic data type arrays have a name of length 2 (e.g., [C)
        // Reference types (and non-basic data type arrays) have a name length of at least 3 (e.g., Lmypackage/MyClassName; or [LX;)
        return internalName.length() == 2;
    }

    public boolean isReferenceArray() {
        return isArray() && !isPrimitiveArray();
    }

    public boolean isArray() {
        return internalName.startsWith("[");
    }

    public boolean isPossibleDomainType() {
        return isPossibleDomainType;
    }

    @NotNull
    public String getPackage() {
        int lastDotIndex = externalName.lastIndexOf(".");
        if (lastDotIndex < 0) {
            return "(default package)";
        }
        return externalName.substring(0, lastDotIndex);
    }

    public boolean isRecursiveType() {
        // Check if any field points to the same type
        for (FieldInfo field : fieldInfos) {
            if (!field.isStatic() && field.typeId == this.id) {
                return true;
            }
        }
        return false;
    }
}
