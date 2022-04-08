package at.jku.anttracks.heap.iteration;

public class SpliteratorRange {
    public int startSpaceId;
    public int startLabIndex;

    /*
     * 1 larger than the maximum space index. May be changed during splitting (and therefore not final).
     */
    public int fenceSpaceId;
    /*
     * 1 larger than the maximum lab index in the last space.- May be changed during splitting (and therefore not final)
     */
    public int fenceLabIndex;

    public SpliteratorRange(int startSpaceId, int startLabIndex, int fenceSpaceId, int fenceLabIndex) {
        this.startSpaceId = startSpaceId;
        this.startLabIndex = startLabIndex;
        this.fenceSpaceId = fenceSpaceId;
        this.fenceLabIndex = fenceLabIndex;
    }
}