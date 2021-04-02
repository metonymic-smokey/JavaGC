package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.settings;

public enum SeriesSort {
    START("Start"),
    AVG("Average"),
    END("End"),
    ABS_GROWTH("Absolute Growth"),
    REL_GROWTH("Relative Growth");

    String name;

    SeriesSort(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
