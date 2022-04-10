package at.jku.anttracks.gui.chart.jfreechart.xy.line.base;

import at.jku.anttracks.gui.chart.jfreechart.xy.base.XYJFreeChartPane;
import org.jfree.data.xy.XYDataset;

public abstract class LineJFreeChartPane<DATA, DS extends XYDataset> extends XYJFreeChartPane<DATA, DS> {

    @Override
    public double getMaxYValue(XYDataset dataset) {
        double maxYValue = Double.MIN_VALUE;

        for (int s = 0; s < dataset.getSeriesCount(); s++) {
            for (int i = 0; i < dataset.getItemCount(s); i++) {
                double y = dataset.getY(s, i).doubleValue();
                maxYValue = Math.max(maxYValue, y);
            }
        }
        return maxYValue;
    }

    @Override
    public double getMinYValue(XYDataset dataset) {
        double minYValue = Double.MAX_VALUE;

        for (int s = 0; s < dataset.getSeriesCount(); s++) {
            for (int i = 0; i < dataset.getItemCount(s); i++) {
                double y = dataset.getY(s, i).doubleValue();
                minYValue = Math.min(minYValue, y);
            }
        }
        return minYValue;
    }
}
