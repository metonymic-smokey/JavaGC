
package at.jku.anttracks.gui.chart.fxchart.xy.linepane.base;

import at.jku.anttracks.gui.chart.fxchart.xy.base.XYBaseJavaFXChartPane;
import at.jku.anttracks.gui.utils.FXMLUtil;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;

public abstract class LineJavaFXChartPane<DATA> extends XYBaseJavaFXChartPane<DATA> {

    public LineJavaFXChartPane() {
        FXMLUtil.load(this, LineJavaFXChartPane.class);
    }

    @Override
    public double getMaxYValue(ObservableList<XYChart.Series<Number, Number>> seriesList) {
        double maxYValue = Double.MIN_VALUE;

        for (XYChart.Series<Number, Number> series : seriesList) {
            for (XYChart.Data<Number, Number> dataPoint : series.getData()) {
                if (dataPoint.getYValue().doubleValue() > maxYValue) {
                    maxYValue = dataPoint.getYValue().doubleValue();
                }
            }
        }
        return maxYValue;
    }

    @Override
    public double getMinYValue(ObservableList<XYChart.Series<Number, Number>> seriesList) {
        double minYValue = Double.MAX_VALUE;

        for (XYChart.Series<Number, Number> series : seriesList) {
            for (XYChart.Data<Number, Number> dataPoint : series.getData()) {
                if (dataPoint.getYValue().doubleValue() < minYValue) {
                    minYValue = dataPoint.getYValue().doubleValue();
                }
            }
        }
        return minYValue;
    }
}
