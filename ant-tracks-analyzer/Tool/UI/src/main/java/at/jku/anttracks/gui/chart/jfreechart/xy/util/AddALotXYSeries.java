package at.jku.anttracks.gui.chart.jfreechart.xy.util;

import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;

import java.util.List;

public class AddALotXYSeries extends XYSeries {

    private static final long serialVersionUID = 2659515387961860128L;

    public AddALotXYSeries(Comparable<?> key, boolean allowsDuplicates) {
        super(key, false, allowsDuplicates);
    }

    /**
     * Sets a list of data in a single go.
     * WATCH OUT: The data list has to be sorted according to the x-values before calling this method!
     */
    public void setData(List<XYDataItem> data, boolean notify) {
        if (data != null && data.size() > 0) {
            this.data = data;
            XYDataItem lastObject = (XYDataItem) this.data.remove(this.data.size() - 1);
            // Readd this object so the min and max X and Y values get calculated
            add(lastObject, notify);
        }
    }
}