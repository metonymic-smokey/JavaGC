package at.jku.anttracks.parser.hprof.datastructures

enum class RootPtr {
    UNKNOWN,
    JNI_GLOBAL,
    JNI_LOCAL,
    JAVA_FRAME,
    NATIVE_STACK,
    STICKY_CLASS,
    THREAD_BLOCK,
    MONITOR_USED,
    THREAD_OBJECT
}