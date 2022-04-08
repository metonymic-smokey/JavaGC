package at.jku.anttracks.gui.chart.jfreechart.base;

import at.jku.anttracks.gui.chart.base.AntChartPane;
import at.jku.anttracks.gui.chart.base.ChartSynchronizer;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYTitleAnnotation;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.data.xy.XYDataset;

import java.awt.*;
import java.util.ListIterator;

public class AntJFreeChart extends JFreeChart {

    private static final long serialVersionUID = -1738161593485497407L;
    private final ChartSynchronizer synchronizer;
    private final AntChartPane<? extends XYDataset, ?> chartPane;

    public AntJFreeChart(AntChartPane<? extends XYDataset, ?> chartPane,
                         String title,
                         Font titleFont,
                         Plot plot,
                         boolean createLegend,
                         ChartSynchronizer synchronizer) {
        super(title, titleFont, plot, createLegend);
        this.chartPane = chartPane;
        this.synchronizer = synchronizer;
    }

    public ChartSynchronizer getSynchronizer() {
        return synchronizer;
    }

    public String getChartID() {
        return chartPane.getPaneId();
    }

    @Override
    public LegendTitle getLegend() {
        return getLegend(0);
    }

    @Override
    public LegendTitle getLegend(int index) {
        ListIterator<?> iter = getXYPlot().getAnnotations().listIterator();
        int matches = -1;
        while (iter.hasNext()) {
            XYAnnotation ele = (XYAnnotation) iter.next();
            if (ele instanceof XYTitleAnnotation) {
                if (((XYTitleAnnotation) ele).getTitle() instanceof LegendTitle) {
                    matches++;
                }
            }
            if (matches == index) {
                XYTitleAnnotation titleAnnotation = (XYTitleAnnotation) ele;
                return (LegendTitle) titleAnnotation.getTitle();
            }
        }
        return null;
    }

    @Override
    public void addLegend(LegendTitle legend) {
        XYTitleAnnotation ta = new XYTitleAnnotation(0.0, 1, legend, RectangleAnchor.TOP_LEFT);
        ta.setMaxWidth(0.5);
        getXYPlot().addAnnotation(ta);

        fireChartChanged();
    }

    @Override
    public void removeLegend() {
        ListIterator<?> iter = getXYPlot().getAnnotations().listIterator();
        while (iter.hasNext()) {
            XYAnnotation ele = (XYAnnotation) iter.next();
            if (ele instanceof XYTitleAnnotation) {
                if (((XYTitleAnnotation) ele).getTitle() instanceof LegendTitle) {
                    getXYPlot().removeAnnotation(ele);
                    return;
                }
            }
        }

        fireChartChanged();
    }
}