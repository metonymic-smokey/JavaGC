package at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.objectkind;

import at.jku.anttracks.gui.chart.base.AxisScaler;
import at.jku.anttracks.gui.chart.base.JFreeChartAxisScaler;
import at.jku.anttracks.gui.chart.jfreechart.base.JFreeChartFactory;
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.base.StackedAreaJFreeChartPane;
import at.jku.anttracks.gui.chart.jfreechart.xy.util.AddALotXYSeries;
import at.jku.anttracks.gui.classification.classifier.ObjectKindsClassifier;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.heap.statistics.Statistics;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.data.xy.DefaultTableXYDataset;
import org.jfree.data.xy.TableXYDataset;
import org.jfree.data.xy.XYDataItem;

import java.util.ArrayList;
import java.util.List;

public class ObjectKindStackedAreaJFreeChartPane extends StackedAreaJFreeChartPane<AppInfo, TableXYDataset> {

    public ObjectKindStackedAreaJFreeChartPane() {
        FXMLUtil.load(this, ObjectKindStackedAreaJFreeChartPane.class);
    }

    public TableXYDataset createDataSet(AppInfo appInfo) {
        AddALotXYSeries instanceSeries = new AddALotXYSeries(ObjectKindsClassifier.INSTANCES, false);
        List<XYDataItem> instancesData = new ArrayList<>();
        AddALotXYSeries smallArraySeries = new AddALotXYSeries(ObjectKindsClassifier.SMALL_ARRAY, false);
        List<XYDataItem> smallArrayData = new ArrayList<>();
        AddALotXYSeries bigArraySeries = new AddALotXYSeries(ObjectKindsClassifier.BIG_ARRAY, false);
        List<XYDataItem> bigArrayData = new ArrayList<>();

        for (int i = 0; i < appInfo.getStatistics().size(); i++) {
            Statistics stat = appInfo.getStatistics().get(i);
            double time = stat.getInfo().getTime();

            XYDataItem instanceDataItem = new XYDataItem(time,
                                                         stat.getEden().objectTypes.getInstances() + stat.getSurvivor().objectTypes.getInstances() + stat.getOld().objectTypes
                                                                 .getInstances());
            XYDataItem smallDataItem = new XYDataItem(time,
                                                      stat.getEden().objectTypes.getSmallArrays() + stat.getSurvivor().objectTypes.getSmallArrays() + stat.getOld().objectTypes
                                                              .getSmallArrays());
            XYDataItem bigDataItem = new XYDataItem(time,
                                                    stat.getEden().objectTypes.getBigArrays() + stat.getSurvivor().objectTypes.getBigArrays() + stat.getOld().objectTypes.getBigArrays
                                                            ());

            instancesData.add(instanceDataItem);
            smallArrayData.add(smallDataItem);
            bigArrayData.add(bigDataItem);
        }

        instanceSeries.setData(instancesData, false);
        smallArraySeries.setData(smallArrayData, false);
        bigArraySeries.setData(bigArrayData, false);

        DefaultTableXYDataset dataset = new DefaultTableXYDataset();
        dataset.addSeries(bigArraySeries);
        dataset.addSeries(smallArraySeries);
        dataset.addSeries(instanceSeries);

        return dataset;
    }

    @Override
    protected void initializeChart() {
        chartViewer.setChart(JFreeChartFactory.INSTANCE.createStackedXYAreaChart(this,
                                                                                 "Object Kinds",
                                                                                 null,
                                                                                 "set by scaler",
                                                                                 "set by scaler",
                                                                                 new DefaultTableXYDataset(),
                                                                                 null,
                                                                                 new StandardXYToolTipGenerator(),
                                                                                 null,
                                                                                 getChartSynchronizer(),
                                                                                 true));
    }

    @Override
    protected EventHandler<ActionEvent> getOnConfigureAction() {
        return null;
    }

    @Override
    public AxisScaler<TableXYDataset> getScaler() {
        return new JFreeChartAxisScaler.ObjectsScaler<>(this);
    }
}
