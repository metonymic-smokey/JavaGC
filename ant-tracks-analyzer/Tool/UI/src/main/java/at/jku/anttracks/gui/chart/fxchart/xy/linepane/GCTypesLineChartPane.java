
package at.jku.anttracks.gui.chart.fxchart.xy.linepane;

import at.jku.anttracks.gui.chart.base.AxisScaler;
import at.jku.anttracks.gui.chart.base.JavaFXAxisScaler;
import at.jku.anttracks.gui.chart.fxchart.xy.linepane.base.LineJavaFXChartPane;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.gui.utils.WindowUtil;
import at.jku.anttracks.heap.GarbageCollectionCause;
import at.jku.anttracks.heap.StatisticGCInfo;
import at.jku.anttracks.parser.EventType;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.text.Text;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class GCTypesLineChartPane extends LineJavaFXChartPane<AppInfo> implements ShowGCCause {

    private static final class Info {
        public final GarbageCollectionCause cause;
        public final boolean failed;

        public Info(GarbageCollectionCause cause, boolean failed) {
            this.cause = cause;
            this.failed = failed;
        }
    }

    private static boolean useFirstDerivation = false;

    final Set<Integer> displayedCauses = new HashSet<>();
    private Map<Number, Info> infos;

    public GCTypesLineChartPane() {
        FXMLUtil.load(this, GCTypesLineChartPane.class);
        chart.setTitle("GC Pauses");
    }

    @Override
    public ObservableList<XYChart.Series<Number, Number>> updateDataSet(AppInfo appInfo) {
        double maxX = getMaxXValue(chart.getData());

        infos = new HashMap<>();

        if (useFirstDerivation) {
            double minor = 0;
            double major = 0;

            // Start at 1 because 0 is an artificial GC at t=0
            for (int i = 1; i + 1 < appInfo.getStatistics().size(); i += 2) {
                StatisticGCInfo startInfo = appInfo.getStatistics().get(i).getInfo();
                assert startInfo.getMeta() == EventType.GC_START : "Must be a GC start";
                StatisticGCInfo endInfo = appInfo.getStatistics().get(i + 1).getInfo();
                assert endInfo.getMeta() == EventType.GC_END : "Must be a GC end";

                double time = endInfo.getTime();
                double start = startInfo.getTime();
                double end = endInfo.getTime();
                double duration = end - start;

                if (startInfo.getType().isFull()) {
                    major += duration;
                } else {
                    minor += duration;
                }

                if (time > maxX) {
                    minorSeries.getData().add(new Data<>(time, minor));
                    majorSeries.getData().add(new Data<>(time, major));
                    infos.put(time, new Info(startInfo.getCause(), endInfo.getFailed()));
                }
            }
        } else {
            // Start at 1 because 0 is an artificial GC at t=0
            for (int i = 1; i + 1 < appInfo.getStatistics().size(); i += 2) {
                StatisticGCInfo startInfo = appInfo.getStatistics().get(i).getInfo();
                assert startInfo.getMeta() == EventType.GC_START : "Must be a GC start";
                StatisticGCInfo endInfo = appInfo.getStatistics().get(i + 1).getInfo();
                assert endInfo.getMeta() == EventType.GC_START : "Must be a GC end";
                long start = startInfo.getTime();
                long end = endInfo.getTime();
                long duration = end - start;

                if (start > maxX) {
                    XYChart.Series<Number, Number> series = startInfo.getType().isFull() ?
                                                            (startInfo.getConcurrent() ? majorConcurrentSeries : majorSeries) :
                                                            (startInfo.getConcurrent() ? minorConcurrentSeries : minorSeries);

                    XYChart.Data<Number, Number> firstPoint = new Data<>(1.0 * start, 0);
                    XYChart.Data<Number, Number> secondPoint = new Data<>(1.0 * start, duration);
                    XYChart.Data<Number, Number> thirdPoint = new Data<>(1.0 * end, duration);
                    XYChart.Data<Number, Number> fourthPoint = new Data<>(1.0 * end, 0);
                    Platform.runLater(() -> {
                        series.getData().add(firstPoint);
                        series.getData().add(secondPoint);
                        series.getData().add(thirdPoint);
                        series.getData().add(fourthPoint);
                    });
                    infos.put(1.0 * start, new Info(startInfo.getCause(), endInfo.getFailed()));
                }
            }
        }

        List<XYChart.Series<Number, Number>> series = new ArrayList<>();
        if (!minorSeries.getData().isEmpty()) {
            series.add(minorSeries);
        }
        if (!majorSeries.getData().isEmpty()) {
            series.add(majorSeries);
        }
        if (!minorConcurrentSeries.getData().isEmpty()) {
            series.add(minorConcurrentSeries);
        }
        if (!majorConcurrentSeries.getData().isEmpty()) {
            series.add(majorConcurrentSeries);
        }
        if (!unknownSeries.getData().isEmpty()) {
            series.add(unknownSeries);
        }

        displayedCauses.addAll(Arrays.stream(appInfo.getSymbols().causes.getAll()).filter(c -> !c.getCommon()).map(c -> c.getId()).collect(Collectors.toList()));

        return chart.getData();
    }

    XYChart.Series<Number, Number> minorSeries;
    XYChart.Series<Number, Number> majorSeries;
    XYChart.Series<Number, Number> minorConcurrentSeries;
    XYChart.Series<Number, Number> majorConcurrentSeries;
    XYChart.Series<Number, Number> unknownSeries;

    @Override
    protected void initializeChartSeries() {
        minorSeries = new XYChart.Series<Number, Number>();
        minorSeries.setName("Minor GC");
        majorSeries = new XYChart.Series<Number, Number>();
        majorSeries.setName("Major GC");
        minorConcurrentSeries = new XYChart.Series<Number, Number>();
        minorConcurrentSeries.setName("Minor GC (concurrent)");
        majorConcurrentSeries = new XYChart.Series<Number, Number>();
        majorConcurrentSeries.setName("Major GC (concurrent)");
        unknownSeries = new XYChart.Series<Number, Number>();
        unknownSeries.setName("Unknown GC");
        chart.getData().add(minorSeries);
        chart.getData().add(majorSeries);
        chart.getData().add(minorConcurrentSeries);
        chart.getData().add(majorConcurrentSeries);
        chart.getData().add(unknownSeries);
    }

    @Override
    protected void updateDataLabelContent() {
        dataLabels.forEach((dataPoint, dataLabel) -> getChildren().remove(dataLabel));
        dataLabels.clear();

        chart.getData().forEach(series -> series.getData().forEach(dataPoint -> {
            if (dataPoint.getYValue().doubleValue() > 0) {
                Number value = dataPoint.getXValue();
                Info info = infos.get(value);
                if (info != null && (displayedCauses.contains(info.cause.getId()) || info.failed)) {
                    Text dataLabel = new Text(info.cause.getName() + (info.failed ? "(failed)" : ""));
                    dataLabel.getStyleClass().add("data-label");
                    dataLabel.setManaged(false);
                    updateDataLabelLayout(dataPoint, dataLabel);
                    dataLabels.put(dataPoint, dataLabel);
                    getChildren().add(dataLabel);
                }
            }
        }));
    }

    @Override
    protected EventHandler<ActionEvent> getOnConfigureAction() {
        return ae -> {
            GCTypesChartConfigurationDialog dialog = new GCTypesChartConfigurationDialog();
            dialog.init(this);
            WindowUtil.INSTANCE.centerInMainFrame(dialog);
            dialog.showAndWait();
        };
    }

    @NotNull
    @Override
    public AxisScaler<ObservableList<XYChart.Series<Number, Number>>> getScaler() {
        return new JavaFXAxisScaler.GCScaler(this);
    }

    @Override
    public Set<Integer> getDisplayedCauses() {
        return displayedCauses;
    }

    @Override
    public void updateDisplayCause(boolean show, GarbageCollectionCause... causes) {
        if (show) {
            displayedCauses.addAll(Arrays.stream(causes).map(GarbageCollectionCause::getId).collect(Collectors.toList()));
        } else {
            displayedCauses.removeAll(Arrays.stream(causes).map(GarbageCollectionCause::getId).collect(Collectors.toList()));
        }
        updateDataLabelContent();
    }
}
