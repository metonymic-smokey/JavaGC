package at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.base;

import at.jku.anttracks.gui.chart.jfreechart.xy.base.XYJFreeChartPane;
import org.jfree.data.xy.TableXYDataset;

public abstract class StackedAreaJFreeChartPane<DATA, DS extends TableXYDataset> extends XYJFreeChartPane<DATA, DS> {

    @Override
    protected abstract DS createDataSet(DATA data);

    @Override
    public double getMaxYValue(DS dataset) {
        double maxYValue = Double.MIN_VALUE;

        if (dataset.getSeriesCount() > 0) {
            for (int i = 0; i < dataset.getItemCount(0); i++) {
                double y = 0;
                for (int s = 0; s < dataset.getSeriesCount(); s++) {
                    y += dataset.getY(s, i).doubleValue();
                }
                maxYValue = Math.max(maxYValue, y);
            }
        }
        return maxYValue;
    }

    @Override
    public double getMinYValue(DS dataset) {
        double minYValue = Double.MAX_VALUE;

        if (dataset.getSeriesCount() > 0) {
            for (int i = 0; i < dataset.getItemCount(0); i++) {
                double y = 0;
                for (int s = 0; s < dataset.getSeriesCount(); s++) {

                    y += dataset.getY(s, i).doubleValue();
                }
                minYValue = Math.min(minYValue, y);
            }
        }
        return minYValue;
    }
}
