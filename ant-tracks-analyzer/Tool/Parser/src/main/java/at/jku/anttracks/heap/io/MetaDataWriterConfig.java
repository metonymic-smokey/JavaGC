
package at.jku.anttracks.heap.io;

public class MetaDataWriterConfig extends MetaDataConfig {

    public static int DEFAULT_DUMP_COUNT = 20;
    public static boolean DEFAULT_AT_MAJOR_GCS_ONLY = false;

    public final int dumps;
    public final boolean atMajorGCsOnly;

    public MetaDataWriterConfig(String path) {
        this(path, DEFAULT_DUMP_COUNT, DEFAULT_AT_MAJOR_GCS_ONLY);
    }

    public MetaDataWriterConfig(String path, int dumps, boolean atMajorGCsOnly) {
        super(path);
        this.dumps = dumps;
        this.atMajorGCsOnly = atMajorGCsOnly;
    }

}
