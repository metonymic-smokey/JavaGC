package at.jku.anttracks.gui.chart.fxchart.xy.stackedareapane;

import at.jku.anttracks.gui.chart.base.AxisScaler;
import at.jku.anttracks.gui.chart.base.JavaFXAxisScaler;
import at.jku.anttracks.gui.chart.fxchart.xy.stackedareapane.base.StackedAreaJavaFXChartPane;
import at.jku.anttracks.gui.classification.classifier.ObjectKindsClassifier;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.heap.statistics.Statistics;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.chart.XYChart;

public class ObjectKindStackedAreaChartPane extends StackedAreaJavaFXChartPane<AppInfo> {
    XYChart.Series<Number, Number> instancesSeries;
    XYChart.Series<Number, Number> smallSeries;
    XYChart.Series<Number, Number> bigSeries;

    public ObjectKindStackedAreaChartPane() {
        FXMLUtil.load(this, ObjectKindStackedAreaChartPane.class);
        chart.setTitle("Object Kinds");
    }

    protected ObservableList<XYChart.Series<Number, Number>> updateDataSet(AppInfo appInfo) {
        double maxX = getMaxXValue(chart.getData());

        for (int i = 0; i < appInfo.getStatistics().size(); i++) {
            Statistics stat = appInfo.getStatistics().get(i);
            double time = stat.getInfo().getTime();

            if (time > maxX) {
                XYChart.Data<Number, Number> instanceData =
                        new XYChart.Data<>(time,
                                           stat.getEden().objectTypes.getInstances() + stat.getSurvivor().objectTypes.getInstances() + stat.getOld().objectTypes.getInstances());
                XYChart.Data<Number, Number> smallData =
                        new XYChart.Data<>(time,
                                           stat.getEden().objectTypes.getSmallArrays() + stat.getSurvivor().objectTypes.getSmallArrays() + stat.getOld().objectTypes.getSmallArrays());
                XYChart.Data<Number, Number> bigData =
                        new XYChart.Data<>(time,
                                           stat.getEden().objectTypes.getBigArrays() + stat.getSurvivor().objectTypes.getBigArrays() + stat.getOld().objectTypes.getBigArrays());

                Platform.runLater(() -> {
                    instancesSeries.getData().add(instanceData);
                    smallSeries.getData().add(smallData);
                    bigSeries.getData().add(bigData);
                });
            }
        }

        return chart.getData();
    }

    @Override
    public AxisScaler<ObservableList<XYChart.Series<Number, Number>>> getScaler() {
        return new JavaFXAxisScaler.ObjectsScaler(this);
    }

    @Override
    protected void initializeChartSeries() {
        instancesSeries = new XYChart.Series<>();
        instancesSeries.setName(ObjectKindsClassifier.INSTANCES);
        smallSeries = new XYChart.Series<>();
        smallSeries.setName(ObjectKindsClassifier.SMALL_ARRAY);
        bigSeries = new XYChart.Series<>();
        bigSeries.setName(ObjectKindsClassifier.BIG_ARRAY);
        chart.getData().add(instancesSeries);
        chart.getData().add(smallSeries);
        chart.getData().add(bigSeries);
    }

    @Override
    protected void updateDataLabelContent() {}

    @Override
    protected EventHandler<ActionEvent> getOnConfigureAction() {
        return null;
    }
}
