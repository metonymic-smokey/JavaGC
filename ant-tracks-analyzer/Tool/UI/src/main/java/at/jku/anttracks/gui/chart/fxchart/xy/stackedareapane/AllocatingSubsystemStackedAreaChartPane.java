package at.jku.anttracks.gui.chart.fxchart.xy.stackedareapane;

import at.jku.anttracks.gui.chart.base.AxisScaler;
import at.jku.anttracks.gui.chart.base.JavaFXAxisScaler;
import at.jku.anttracks.gui.chart.fxchart.xy.stackedareapane.base.StackedAreaJavaFXChartPane;
import at.jku.anttracks.gui.classification.classifier.AllocatingSubsystemClassifier;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.heap.statistics.Statistics;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.chart.XYChart;

public class AllocatingSubsystemStackedAreaChartPane extends StackedAreaJavaFXChartPane<AppInfo> {
    XYChart.Series<Number, Number> vmSeries;
    XYChart.Series<Number, Number> irSeries;
    XYChart.Series<Number, Number> c1Series;
    XYChart.Series<Number, Number> c2Series;

    @Override
    protected ObservableList<XYChart.Series<Number, Number>> updateDataSet(AppInfo appInfo) {
        double maxX = getMaxXValue(chart.getData());

        for (int i = 0; i < appInfo.getStatistics().size(); i++) {
            Statistics stat = appInfo.getStatistics().get(i);
            double time = stat.getInfo().getTime();

            if (time > maxX) {

                XYChart.Data<Number, Number> vmData =
                        new XYChart.Data<>(time, stat.getEden().allocators.getVm() + stat.getSurvivor().allocators.getVm() + stat.getOld().allocators.getVm());
                XYChart.Data<Number, Number> irData =
                        new XYChart.Data<>(time, stat.getEden().allocators.getIr() + stat.getSurvivor().allocators.getIr() + stat.getOld().allocators.getIr());
                XYChart.Data<Number, Number> c1Data =
                        new XYChart.Data<>(time, stat.getEden().allocators.getC1() + stat.getSurvivor().allocators.getC1() + stat.getOld().allocators.getC1());
                XYChart.Data<Number, Number> c2Data =
                        new XYChart.Data<>(time, stat.getEden().allocators.getC2() + stat.getSurvivor().allocators.getC2() + stat.getOld().allocators.getC2());

                Platform.runLater(() -> {
                    vmSeries.getData().add(vmData);
                    irSeries.getData().add(irData);
                    c1Series.getData().add(c1Data);
                    c2Series.getData().add(c2Data);
                });
            }
        }

        return chart.getData();
    }

    public AllocatingSubsystemStackedAreaChartPane() {
        FXMLUtil.load(this, AllocatingSubsystemStackedAreaChartPane.class);
        chart.setTitle("Allocating Subsystem");
    }

    @Override
    protected void initializeChartSeries() {
        vmSeries = new XYChart.Series<>();
        vmSeries.setName(AllocatingSubsystemClassifier.VM);
        irSeries = new XYChart.Series<>();
        irSeries.setName(AllocatingSubsystemClassifier.IR);
        c1Series = new XYChart.Series<>();
        c1Series.setName(AllocatingSubsystemClassifier.C1);
        c2Series = new XYChart.Series<>();
        c2Series.setName(AllocatingSubsystemClassifier.C2);
        chart.getData().add(vmSeries);
        chart.getData().add(irSeries);
        chart.getData().add(c1Series);
        chart.getData().add(c2Series);
    }

    @Override
    protected void updateDataLabelContent() {

    }

    @Override
    public AxisScaler<ObservableList<XYChart.Series<Number, Number>>> getScaler() {
        return new JavaFXAxisScaler.ObjectsScaler(this);
    }

    @Override
    protected EventHandler<ActionEvent> getOnConfigureAction() {
        return null;
    }
}
