
package at.jku.anttracks.parser

enum class EventType(val id: Int, val mayBeFollowedByAnchor: Boolean = true) {
    NOP(0x00),
    MARK(0x01),
    /**
     * Marks the start of a GC
     */
    GC_START(0x02),
    /**
     * Marks the end of a GC
     */
    GC_END(0x03),
    GC_INFO(0x04),
    GC_FAILED(0x05),
    SPACE_CREATE(0x06),
    SPACE_ALLOC(0x07),
    SPACE_RELEASE(0x08),
    SPACE_REDEFINE(0x09),
    SPACE_DESTROY(0x0A),
    THREAD_ALIVE(0x0B),
    THREAD_DEATH(0x0C),
    TLAB_ALLOC(0x0D),
    PLAB_ALLOC(0x0E),
    OBJ_ALLOC_SLOW(0x0F),
    OBJ_ALLOC_SLOW_IR(0x10),
    OBJ_ALLOC_SLOW_IR_DEVIANT_TYPE(0x11),
    OBJ_ALLOC_NORMAL_IR(0x12),
    OBJ_ALLOC_FAST_IR(0x13),
    OBJ_ALLOC_SLOW_C1(0x14),
    OBJ_ALLOC_SLOW_C1_DEVIANT_TYPE(0x15),
    OBJ_ALLOC_NORMAL_C1(0x16),
    OBJ_ALLOC_FAST_C1(0x17),
    OBJ_ALLOC_FAST_C1_DEVIANT_TYPE(0x18),
    OBJ_ALLOC_SLOW_C2(0x19),
    OBJ_ALLOC_SLOW_C2_DEVIANT_TYPE(0x1A),
    OBJ_ALLOC_NORMAL_C2(0x1B),
    OBJ_ALLOC_FAST_C2(0x1C),
    OBJ_ALLOC_FAST_C2_DEVIANT_TYPE(0x1D),
    GC_MOVE_SLOW(0x1E),
    GC_MOVE_FAST_WIDE(0x1F),
    GC_MOVE_FAST(0x20),
    GC_MOVE_FAST_NARROW(0x21),
    GC_MOVE_REGION(0x22),
    GC_KEEP_ALIVE(0x23),
    SYNC_OBJ(0x24),
    SYNC_OBJ_NARROW(0x25),
    GC_DEALLOCATION(0x26),

    /**
     * This event contains information about an object's movement (from, to, ...) and additionally up to twelve pointers.
     * On a minor GC, the ptr addresses have to be the addresses BEFORE the object move (which get updated based on the forwarding
     * address in the parser).
     * On a major GC, the ptr addresses have to be the addresses AFTER the object move (i.e., the VM already sends correct addresses)
     */
    GC_MOVE_SLOW_PTR(0x27),
    /**
     * This event contains information about an object's movement (from, to, ...) and additionally up to twelve pointers.
     * On a minor GC, the ptr addresses have to be the addresses BEFORE the object move (which get updated based on the forwarding
     * address in the parser).
     * On a major GC, the ptr addresses have to be the addresses AFTER the object move (i.e., the VM already sends correct addresses)
     */
    GC_MOVE_FAST_WIDE_PTR(0x28),
    /**
     * This event contains information about an object's movement (from, to, ...) and additionally up to twelve pointers.
     * On a minor GC, the ptr addresses have to be the addresses BEFORE the object move (which get updated based on the forwarding
     * address in the parser).
     * On a major GC, the ptr addresses have to be the addresses AFTER the object move (i.e., the VM already sends correct addresses)
     */
    GC_MOVE_FAST_PTR(0x29),

    GC_KEEP_ALIVE_PTR(0x2A),
    /**
     * May only be sent directly after a MOVE_*_PTR event.
     * If a single object contains more than 12 pointers, consecutive GC_PTR_EXTENSION events are sent, each with 12 pointers.
     *
     *
     * Invariant: The MOVE_*_PTR event and the extension event are sent by the same thread, and no other event is sent between the
     * corresponding MOVE_*_PTR event and the GC_PTR_EXTENSION events
     *
     *
     * GC_PTR_EXTENSION events must never occur on their own, but only in conjunction with their respective MOVE_*_PTR event.
     */
    GC_PTR_EXTENSION(0x2B),
    /**
     * To improve speed-up, some garbage collectors process large objects using multiple threads.
     * Each 12-pointer-chunk of such a processing may be sent using this event, which point to the pointee's address BEFORE being moved by the GC (18 Jan 2019,
     * assumption MW).
     * Therefore, it varies from GC_PTR_EXTENSION since this event may be sent by different threads for the same object (i.e.,
     * non-consecutive), while GC_PTR_EXTENSION has to occur consecutively after the objects move event by the same thread.
     * It may also be sent for objects that have not been moved but are processed by multiple threads.
     */
    GC_PTR_MULTITHREADED(0x2C),
    GC_ROOT_PTR(0x2D),
    /**
     * This event is used to send the pointers of an object directly.
     * Each event may contain up to 12 pointers (addresses), which point to the pointee's address BEFORE being moved by the GC.
     * This means that after processing all move and pointer events during the GC, the parser has to update the addresses of these
     * pointers afterwards.
     * All PTR_UPDATE_* events for the same object have to be sent consecutively by the same thread.
     * This event may only be sent for objects that are not getting moved!
     */
    GC_PTR_UPDATE_PREMOVE(0x2E),

    GC_INTERRUPT(0x2F),
    GC_CONTINUE(0x30),

    /**
     * This event is used to send the pointers of an object directly.
     * Each event may contain up to 12 pointers (addresses), which point to the pointee's address AFTER being moved by the GC.
     * All PTR_UPDATE_* events for the same object have to be sent consecutively by the same thread.
     * This event may only be sent for objects that have not been moved!
     */
    GC_PTR_UPDATE_POSTMOVE(0x31),

    GC_TAG(0x32),
    CLEANUP(0x33),
    MOVE_GENERIC(0x34);

    companion object {
        fun parse(id: Int): EventType {
            return EventType.values()[id]
        }

        fun isDedicatedPtrEvent(eventType: EventType): Boolean {
            return eventType == EventType.GC_PTR_EXTENSION || eventType == EventType.GC_PTR_UPDATE_PREMOVE || eventType == EventType.GC_PTR_MULTITHREADED || eventType == EventType.GC_PTR_UPDATE_POSTMOVE
        }
    }
}
