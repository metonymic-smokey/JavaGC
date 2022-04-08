package at.jku.anttracks.gui.chart.fxchart.xy.stackedareapane;

import java.util.Set;

public interface ShowFeature {
    int getFeatureCount();

    Set<Integer> getDisplayedFeatures();

    void updateDisplayFeature(boolean display, Integer... ids);
}
