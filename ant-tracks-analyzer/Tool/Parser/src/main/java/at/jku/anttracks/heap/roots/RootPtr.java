package at.jku.anttracks.heap.roots;

import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.parser.EventType;
import at.jku.anttracks.parser.hprof.handler.HprofToFastHeapHandler;

import java.io.DataInputStream;
import java.io.IOException;

public abstract class RootPtr {
    // match enum in EventRootPointerList.hpp
    // root types with additional info
    public enum RootType {
        // roots with additional info
        CLASS_LOADER_ROOT((byte) 0, "Class loader", false),
        CLASS_ROOT((byte) 1, "Class", false),
        STATIC_FIELD_ROOT((byte) 2, "Static field", true),
        LOCAL_VARIABLE_ROOT((byte) 3, "Local variable", true),
        VM_INTERNAL_THREAD_DATA_ROOT((byte) 4, "VM internal thread data", false),
        CODE_BLOB_ROOT((byte) 5, "Code blob", false),
        JNI_LOCAL_ROOT((byte) 6, "JNI local", true),
        JNI_GLOBAL_ROOT((byte) 7, "JNI global", true),

        // other root types just for distinction
        CLASS_LOADER_INTERNAL_ROOT((byte) 8, "Class loader internal", false),
        UNIVERSE_ROOT((byte) 9, "Universe", false),
        SYSTEM_DICTIONARY_ROOT((byte) 10, "System dictionary", false),
        BUSY_MONITOR_ROOT((byte) 11, "Busy monitor", false),
        INTERNED_STRING((byte) 12, "Interned String", false),
        FLAT_PROFILER_ROOT((byte) 13, "Flat profiler", false),
        MANAGEMENT_ROOT((byte) 14, "Management", false),
        JVMTI_ROOT((byte) 15, "JVMTI", true),

        // for debugging -> info contains a string indicating the corresponding jvm call
        DEBUG_ROOT((byte) 16, "Debug", false);

        public final byte byteVal;
        public final String stringRep;
        public final boolean isVariable;

        RootType(byte byteVal, String stringRep, boolean isVariable) {
            this.byteVal = byteVal;
            this.stringRep = stringRep;
            this.isVariable = isVariable;
        }
    }

    public static class RootInfo {
        public final RootPtr[] ptrs;
        public final int[] path;

        public RootInfo(RootPtr[] ptrs, int[] path) {
            this.ptrs = ptrs;
            this.path = path;
        }
    }

    public static final byte MAX_ROOTS_PER_EVENT = 3;
    public final EventType eventType = EventType.GC_ROOT_PTR;
    protected long addr;
    private int idx;
    protected boolean resolved = false;

    protected RootPtr(long addr) {
        this.addr = addr;
    }

    public static RootPtr fromMetadata(DataInputStream in) throws IOException {
        RootPtr ret;

        RootType rootType = RootType.values()[in.readByte()];

        switch (rootType) {
            case CLASS_LOADER_ROOT: {
                long ptr = in.readLong();
                byte[] loaderNameBytes = new byte[in.readInt()];
                in.read(loaderNameBytes);
                String loaderName = new String(loaderNameBytes);
                ret = new ClassLoaderRoot(ptr, loaderName);
                break;
            }

            case CLASS_ROOT:
                ret = new ClassRoot(in.readLong(), in.readInt());
                break;

            case STATIC_FIELD_ROOT:
                ret = new StaticFieldRoot(in.readLong(), in.readInt(), in.readInt());
                break;

            case LOCAL_VARIABLE_ROOT:
                ret = new LocalVariableRoot(in.readLong(), in.readLong(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
                break;

            case VM_INTERNAL_THREAD_DATA_ROOT:
                ret = new VMInternalThreadDataRoot(in.readLong(), in.readLong());
                break;

            case CODE_BLOB_ROOT:
                ret = new CodeBlobRoot(in.readLong(), in.readInt(), in.readInt());
                break;

            case JNI_LOCAL_ROOT:
                ret = new JNILocalRoot(in.readLong(), in.readLong());
                break;

            case JNI_GLOBAL_ROOT:
                ret = new JNIGlobalRoot(in.readLong(), in.readBoolean());
                break;

            case CLASS_LOADER_INTERNAL_ROOT:
            case UNIVERSE_ROOT:
            case SYSTEM_DICTIONARY_ROOT:
            case BUSY_MONITOR_ROOT:
            case INTERNED_STRING:
            case FLAT_PROFILER_ROOT:
            case MANAGEMENT_ROOT:
            case JVMTI_ROOT:
                ret = new OtherRoot(in.readLong(), rootType);
                break;

            case DEBUG_ROOT: {
                long ptr = in.readLong();
                byte[] vmCallBytes = new byte[in.readInt()];
                in.read(vmCallBytes);
                String vmCall = new String(vmCallBytes);
                ret = new DebugRoot(ptr, vmCall);
                break;
            }

            default:
                throw new IOException(rootType + " is not a valid root type id!");
        }

        return ret;
    }

    public boolean isInternal() {
        return false;
    }

    public long getAddr() {
        return addr;
    }

    public void setAddr(long addr) {
        this.addr = addr;
    }

    public void setIdx(int idx) {this.idx = idx;}

    public int getIdx() { return idx; }

    public boolean isResolved() {
        return resolved;
    }

    public String getTypeString() {
        return getRootType().stringRep;
    }

    public abstract void resolve(HprofToFastHeapHandler hprof) throws Exception;

    public abstract void resolve(DetailedHeap heap) throws Exception;

    public abstract String[] toClassificationString(boolean includePackages);

    public abstract boolean equals(Object other);

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public abstract String toString();

    public abstract String toShortString();

    public abstract String toGraphString();

    public abstract byte[] getMetadata();

    public abstract RootType getRootType();
}
