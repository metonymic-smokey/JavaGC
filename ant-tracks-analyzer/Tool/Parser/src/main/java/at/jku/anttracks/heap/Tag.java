package at.jku.anttracks.heap;

import at.jku.anttracks.parser.ParserGCInfo;
import at.jku.anttracks.parser.ParserGCInfo;

public class Tag {
    //private final DetailedHeap heap;
    public final ParserGCInfo gcInfo;
    public final GarbageCollectionLookup gcLookup;
    public final String text;

    public Tag(ParserGCInfo gcInfo, GarbageCollectionLookup gcLookup, String text) {
        // this.heap = heap;
        this.gcInfo = gcInfo;
        this.gcLookup = gcLookup;
        this.text = text;
    }
}
