package at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.allocatingsubsystem;

import at.jku.anttracks.gui.chart.base.AxisScaler;
import at.jku.anttracks.gui.chart.base.JFreeChartAxisScaler;
import at.jku.anttracks.gui.chart.jfreechart.base.JFreeChartFactory;
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.base.StackedAreaJFreeChartPane;
import at.jku.anttracks.gui.chart.jfreechart.xy.util.AddALotXYSeries;
import at.jku.anttracks.gui.classification.classifier.AllocatingSubsystemClassifier;
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

public class AllocatingSubsystemStackedAreaJFreeChartPane extends StackedAreaJFreeChartPane<AppInfo, TableXYDataset> {

    public AllocatingSubsystemStackedAreaJFreeChartPane() {
        FXMLUtil.load(this, AllocatingSubsystemStackedAreaJFreeChartPane.class);
    }

    public TableXYDataset createDataSet(AppInfo appInfo) {
        AddALotXYSeries vmSeries = new AddALotXYSeries(AllocatingSubsystemClassifier.VM, false);
        List<XYDataItem> vmData = new ArrayList<>();
        AddALotXYSeries irSeries = new AddALotXYSeries(AllocatingSubsystemClassifier.IR, false);
        List<XYDataItem> irData = new ArrayList<>();
        AddALotXYSeries c1Series = new AddALotXYSeries(AllocatingSubsystemClassifier.C1, false);
        List<XYDataItem> c1Data = new ArrayList<>();
        AddALotXYSeries c2Series = new AddALotXYSeries(AllocatingSubsystemClassifier.C2, false);
        List<XYDataItem> c2Data = new ArrayList<>();

        for (int i = 0; i < appInfo.getStatistics().size(); i++) {
            Statistics stat = appInfo.getStatistics().get(i);
            double time = stat.getInfo().getTime();

            XYDataItem vmDataItem = new XYDataItem(time, stat.getEden().allocators.getVm() + stat.getSurvivor().allocators.getVm() + stat.getOld().allocators.getVm());
            XYDataItem irDataItem = new XYDataItem(time, stat.getEden().allocators.getIr() + stat.getSurvivor().allocators.getIr() + stat.getOld().allocators.getIr());
            XYDataItem c1DataItem = new XYDataItem(time, stat.getEden().allocators.getC1() + stat.getSurvivor().allocators.getC1() + stat.getOld().allocators.getC1());
            XYDataItem c2DataItem = new XYDataItem(time, stat.getEden().allocators.getC2() + stat.getSurvivor().allocators.getC2() + stat.getOld().allocators.getC2());
            vmData.add(vmDataItem);
            irData.add(irDataItem);
            c1Data.add(c1DataItem);
            c2Data.add(c2DataItem);
        }

        vmSeries.setData(vmData, false);
        irSeries.setData(irData, false);
        c1Series.setData(c1Data, false);
        c2Series.setData(c2Data, false);

        DefaultTableXYDataset dataset = new DefaultTableXYDataset();
        dataset.addSeries(vmSeries);
        dataset.addSeries(irSeries);
        dataset.addSeries(c1Series);
        dataset.addSeries(c2Series);

        return dataset;
    }

    @Override
    protected void initializeChart() {
        chartViewer.setChart(JFreeChartFactory.INSTANCE.createStackedXYAreaChart(this,
                                                                                 "Allocating Subsystem",
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
