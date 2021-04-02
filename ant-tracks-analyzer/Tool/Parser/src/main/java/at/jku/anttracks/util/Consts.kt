
package at.jku.anttracks.util

object Consts {
    const val ARRAY_SIZE_MAX_SMALL = 255
    const val ABSOLUTE_PTR = 255
    const val START_ARRAY_LEN = 64
    const val UNDEFINED_ADDR: Long = -1
    const val UNDEFINED_LENGTH = -1

    const val AVERAGE_OBJECT_SIZE = 20

    const val ANCHOR_MASK = -0xff01
    const val HEX_WORD = -0x1000000
    const val HEX_ELEM_SIZE = 0x00FF
    const val HEX_SHORT = 0xFFFF
    const val HEX_BYTE = 0xFF

    const val HEAP_OBJECT_HEAP_FILES_MAGIC_PREFIX: Int = 1992
    const val HEAP_FILES_MAGIC_PREFIX: Int = -0x543210be
    const val ANT_META_DIRECTORY = ".ant_tracks_trace_meta_data"
    const val HEADERS_META_FILE = "header"
    const val HEAP_INDEX_META_FILE = "index"
    const val STATISTICS_META_FILE = "statistics"
    const val FEATURES_META_FILE = "features"
    const val LIST_TREE_EXTENSION = ".listtree"
    const val DIFF_STAT_EXTENSION = ".diffstat"
    const val DIFF_INTERVAL_EXTENSION = ".diffinterval"
    const val DIFF_HEAP_EXTENSION = ".diffheap"

    @JvmStatic
    val AVAILABLE_PROCESSORS: Int
        get() = Runtime.getRuntime().availableProcessors()
}