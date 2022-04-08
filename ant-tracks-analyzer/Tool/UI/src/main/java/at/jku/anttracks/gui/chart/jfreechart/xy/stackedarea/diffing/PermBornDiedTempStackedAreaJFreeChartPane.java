package at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.diffing;

import at.jku.anttracks.gui.chart.base.ApplicationChartFactory;
import at.jku.anttracks.gui.chart.base.AxisScaler;
import at.jku.anttracks.gui.chart.base.ChartSynchronizer;
import at.jku.anttracks.gui.chart.base.JFreeChartAxisScaler;
import at.jku.anttracks.gui.chart.jfreechart.base.JFreeChartFactory;
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.base.StackedAreaJFreeChartPane;
import at.jku.anttracks.gui.chart.jfreechart.xy.util.AddALotXYSeries;
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.model.PermBornDiedTempData;
import at.jku.anttracks.gui.utils.FXMLUtil;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.data.xy.DefaultTableXYDataset;
import org.jfree.data.xy.TableXYDataset;
import org.jfree.data.xy.XYDataItem;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PermBornDiedTempStackedAreaJFreeChartPane extends StackedAreaJFreeChartPane<List<PermBornDiedTempData>, TableXYDataset> {
    public static final Color[] PERM_BORN_DIED_TEMP_COLORS = new Color[]{new Color(0x66, 0xb2, 0xff),
                                                                         new Color(0xff, 0x66, 0x66),
                                                                         new Color(130, 255, 100),
                                                                         new Color(0xc0, 0xc0, 0xc0)};

    private ApplicationChartFactory.MemoryConsumptionUnit unit;

    public PermBornDiedTempStackedAreaJFreeChartPane() {
        FXMLUtil.load(this, PermBornDiedTempStackedAreaJFreeChartPane.class);
    }

    public void init(ChartSynchronizer synchronizer, ApplicationChartFactory.MemoryConsumptionUnit unit) {
        this.unit = unit;
        super.init(synchronizer, false);
    }

    @Override
    public TableXYDataset createDataSet(List<PermBornDiedTempData> dataList) {
        AddALotXYSeries permSeries = new AddALotXYSeries("Perm", false);
        List<XYDataItem> permData = new ArrayList<>();
        AddALotXYSeries diedSeries = new AddALotXYSeries("Died", false);
        List<XYDataItem> diedData = new ArrayList<>();
        AddALotXYSeries bornSeries = new AddALotXYSeries("Born", false);
        List<XYDataItem> bornData = new ArrayList<>();
        AddALotXYSeries tempSeries = new AddALotXYSeries("Temp", false);
        List<XYDataItem> tempData = new ArrayList<>();

        dataList.forEach(data -> {
            permData.add(new XYDataItem(data.getTime(), data.getPerm().get(unit)));
            diedData.add(new XYDataItem(data.getTime(), data.getDied().get(unit)));
            bornData.add(new XYDataItem(data.getTime(), data.getBorn().get(unit)));
            tempData.add(new XYDataItem(data.getTime(), data.getTemp().get(unit)));
        });

        permSeries.setData(permData, false);
        diedSeries.setData(diedData, false);
        bornSeries.setData(bornData, false);
        tempSeries.setData(tempData, false);

        DefaultTableXYDataset dataset = new DefaultTableXYDataset();
        dataset.addSeries(permSeries);
        dataset.addSeries(diedSeries);
        dataset.addSeries(bornSeries);
        dataset.addSeries(tempSeries);

        return dataset;
    }

    @Override
    public Color[] getSeriesColors() {
        return PERM_BORN_DIED_TEMP_COLORS;
    }

    @Override
    protected void initializeChart() {
        chartViewer.setChart(JFreeChartFactory.INSTANCE.createStackedXYAreaChart(this,
                                                                                 unit.getLabel(),
                                                                                 null,
                                                                                 "x",
                                                                                 "y",
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
        switch (unit) {
            case OBJECTS:
                return new JFreeChartAxisScaler.ObjectsScaler<>(this);
            case BYTES:
                return new JFreeChartAxisScaler.BytesScaler<>(this);
            default:
                getLOGGER().warning("No scaler defined for " + unit);
                return new JFreeChartAxisScaler.NoAxisScaler<>(this);
        }
    }
}
