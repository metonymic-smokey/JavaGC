package at.jku.anttracks.gui.chart.jfreechart.xy.line.gc;

import at.jku.anttracks.gui.chart.base.AxisScaler;
import at.jku.anttracks.gui.chart.base.JFreeChartAxisScaler;
import at.jku.anttracks.gui.chart.fxchart.xy.linepane.GCTypesChartConfigurationDialog;
import at.jku.anttracks.gui.chart.fxchart.xy.linepane.ShowGCCause;
import at.jku.anttracks.gui.chart.jfreechart.base.JFreeChartFactory;
import at.jku.anttracks.gui.chart.jfreechart.xy.line.base.LineJFreeChartPane;
import at.jku.anttracks.gui.chart.jfreechart.xy.util.AddALotXYSeries;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.gui.utils.WindowUtil;
import at.jku.anttracks.heap.GarbageCollectionCause;
import at.jku.anttracks.heap.StatisticGCInfo;
import at.jku.anttracks.parser.EventType;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import org.jfree.chart.labels.XYItemLabelGenerator;
import org.jfree.data.xy.DefaultTableXYDataset;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeriesCollection;

import java.util.*;
import java.util.stream.Collectors;

public class GCJFreeChartPane extends LineJFreeChartPane<AppInfo, XYDataset> implements ShowGCCause {
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

    public GCJFreeChartPane() {
        FXMLUtil.load(this, GCJFreeChartPane.class);
    }

    public XYDataset createDataSet(AppInfo appInfo) {
        AddALotXYSeries minorSeries = new AddALotXYSeries("Minor", true);
        List<XYDataItem> minorData = new ArrayList<>();
        AddALotXYSeries majorSeries = new AddALotXYSeries("Major", true);
        List<XYDataItem> majorData = new ArrayList<>();
        AddALotXYSeries majorConcurrentSeries = new AddALotXYSeries("Major (concurrent)", true);
        List<XYDataItem> majorConcurrentData = new ArrayList<>();
        AddALotXYSeries minorConcurrentSeries = new AddALotXYSeries("Minor (concurrent)", true);
        List<XYDataItem> minorConcurrentData = new ArrayList<>();

        infos = new HashMap<>();

        if (useFirstDerivation) {
            double minor = 0;
            double major = 0;

            // Start at 1 because 0 is an artificial GC at t=0
            for (int i = 1; i + 1 < appInfo.getStatistics().size(); i += 2) {
                StatisticGCInfo startInfo = appInfo.getStatistics().get(i).getInfo();
                assert startInfo.getMeta() == EventType.GC_START : "Must be a GC start";
                StatisticGCInfo endInfo = appInfo.getStatistics().get(i + 1).getInfo();
                assert endInfo.getMeta() == EventType.GC_START : "Must be a GC end";

                double time = endInfo.getTime();
                double start = startInfo.getTime();
                double end = endInfo.getTime();
                double duration = end - start;

                if (startInfo.getType().isFull()) {
                    major += duration;
                } else {
                    minor += duration;
                }

                minorData.add(new XYDataItem(time, minor));
                majorData.add(new XYDataItem(time, major));
                infos.put(time, new Info(startInfo.getCause(), endInfo.getFailed()));
            }
        } else {
            // Start at 1 because 0 is an artificial GC at t=0
            for (int i = 1; i + 1 < appInfo.getStatistics().size(); i += 2) {
                StatisticGCInfo startInfo = appInfo.getStatistics().get(i).getInfo();
                assert startInfo.getMeta() == EventType.GC_START : "Must be a GC start";
                StatisticGCInfo endInfo = appInfo.getStatistics().get(i + 1).getInfo();
                assert endInfo.getMeta() == EventType.GC_END : "Must be a GC end";
                long start = startInfo.getTime();
                long end = endInfo.getTime();
                long duration = end - start;

                List<XYDataItem> series = startInfo.getType().isFull() ?
                                          (startInfo.getConcurrent() ? majorConcurrentData : majorData) :
                                          (startInfo.getConcurrent() ? minorConcurrentData : minorData);

                XYDataItem firstPoint = new XYDataItem(1.0 * start, 0);
                XYDataItem secondPoint = new XYDataItem(1.0 * start, duration);
                XYDataItem thirdPoint = new XYDataItem(1.0 * end, duration);
                XYDataItem fourthPoint = new XYDataItem(1.0 * end, 0);
                series.add(firstPoint);
                series.add(secondPoint);
                series.add(thirdPoint);
                series.add(fourthPoint);
                infos.put(1.0 * start, new Info(startInfo.getCause(), endInfo.getFailed()));
            }
        }

        minorSeries.setData(minorData, false);
        majorSeries.setData(majorData, false);
        majorConcurrentSeries.setData(majorConcurrentData, false);
        minorConcurrentSeries.setData(minorConcurrentData, false);

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(majorConcurrentSeries);
        dataset.addSeries(minorConcurrentSeries);
        dataset.addSeries(majorSeries);
        dataset.addSeries(minorSeries);

        displayedCauses.addAll(Arrays.stream(appInfo.getSymbols().causes.getAll()).filter(c -> !c.getCommon()).map(c -> c.getId()).collect(Collectors.toList()));

        return dataset;
    }

    @Override
    protected void initializeChart() {
        chartViewer.setChart(JFreeChartFactory.INSTANCE.createLineXYChart(this,
                                                                          "Garbage Collection Pauses",
                                                                          null,
                                                                          "Time [ms]",
                                                                          "Duration [ms]",
                                                                          new DefaultTableXYDataset(),
                                                                          new XYItemLabelGenerator() {
                                                                              @Override
                                                                              public String generateLabel(XYDataset dataset, int series, int item) {
                                                                                  Info info = infos.get(dataset.getX(series, item));
                                                                                  // info may be null for GC end
                                                                                  if (info != null) {
                                                                                      return displayedCauses.contains(info.cause.getId()) ? info.cause.getName() : "";
                                                                                  }
                                                                                  return "";
                                                                              }
                                                                          },
                                                                          getChartSynchronizer(),
                                                                          true));
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

    @Override
    public AxisScaler<XYDataset> getScaler() {
        return new JFreeChartAxisScaler.GCScaler<>(this);
    }

    @Override
    public Set<Integer> getDisplayedCauses() {
        return displayedCauses;
    }

    @Override
    public void updateDisplayCause(boolean show, GarbageCollectionCause... causes) {
        if (show) {
            displayedCauses.addAll(Arrays.stream(causes).map(c -> c.getId()).collect(Collectors.toList()));
        } else {
            displayedCauses.removeAll(Arrays.stream(causes).map(c -> c.getId()).collect(Collectors.toList()));
        }
        chartViewer.getChart().fireChartChanged();
        //updateDataLabelContent();
    }
}
