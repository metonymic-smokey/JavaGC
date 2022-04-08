package at.jku.anttracks.gui.chart.fxchart.xy.linepane;

import at.jku.anttracks.heap.GarbageCollectionCause;

import java.util.Set;

public interface ShowGCCause {
    Set<Integer> getDisplayedCauses();

    void updateDisplayCause(boolean show, GarbageCollectionCause... causes);
}
