package at.jku.anttracks.gui.chart.jfreechart.base;

import at.jku.anttracks.gui.chart.base.AntChartPane;
import at.jku.anttracks.gui.chart.base.ChartSynchronizer;
import javafx.application.Platform;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.xy.XYDataset;

import java.awt.geom.Point2D;

public class AntJFreeChartXYPlot extends XYPlot {
    private static final long serialVersionUID = -8124501729860976121L;
    private final ChartSynchronizer synchronizer;
    private final AntChartPane<? extends XYDataset, ?> chartPane;

    public AntJFreeChartXYPlot(AntChartPane<? extends XYDataset, ?> chartPane,
                               XYDataset dataset,
                               ValueAxis xAxis,
                               ValueAxis yAxis,
                               XYItemRenderer renderer,
                               ChartSynchronizer synchronizer) {
        super(dataset, xAxis, yAxis, renderer);
        this.chartPane = chartPane;
        this.synchronizer = synchronizer;
    }

    @Override
    public void setDataset(XYDataset dataset) {
        Platform.runLater(() -> {
            for (int i = 0; i < getDatasetCount(); i++) {
                setDataset(i, dataset);
            }
        });
    }

    @Override
    public void panDomainAxes(double percent, PlotRenderingInfo info, Point2D source) {
        super.panDomainAxes(percent, info, source);
        if (synchronizer != null && chartPane.isZoomSynchronized()) {
            synchronizer.setZoomXBounds(getChart().getXYPlot().getDomainAxis().getRange().getLowerBound(),
                                        getChart().getXYPlot().getDomainAxis().getRange().getUpperBound());
        }
    }

    @Override
    public void zoom(double percent) {
        super.zoom(percent);
        if (synchronizer != null && chartPane.isZoomSynchronized()) {
            synchronizer.setZoomXBounds(getChart().getXYPlot().getDomainAxis().getRange().getLowerBound(),
                                        getChart().getXYPlot().getDomainAxis().getRange().getUpperBound());
        }
    }

    @Override
    public void zoomDomainAxes(double factor, PlotRenderingInfo state, Point2D source) {
        super.zoomDomainAxes(factor, state, source);
        if (synchronizer != null && chartPane.isZoomSynchronized()) {
            synchronizer.setZoomXBounds(getChart().getXYPlot().getDomainAxis().getRange().getLowerBound(),
                                        getChart().getXYPlot().getDomainAxis().getRange().getUpperBound());
        }
    }

    @Override
    public void zoomDomainAxes(double factor, PlotRenderingInfo state, Point2D source, boolean useAnchor) {
        super.zoomDomainAxes(factor, state, source, useAnchor);
        if (synchronizer != null && chartPane.isZoomSynchronized()) {
            synchronizer.setZoomXBounds(getChart().getXYPlot().getDomainAxis().getRange().getLowerBound(),
                                        getChart().getXYPlot().getDomainAxis().getRange().getUpperBound());
        }
    }

    @Override
    public void zoomDomainAxes(double lowerPercent, double upperPercent, PlotRenderingInfo state, Point2D source) {
        super.zoomDomainAxes(lowerPercent, upperPercent, state, source);
        if (synchronizer != null && chartPane.isZoomSynchronized()) {
            synchronizer.setZoomXBounds(getChart().getXYPlot().getDomainAxis().getRange().getLowerBound(),
                                        getChart().getXYPlot().getDomainAxis().getRange().getUpperBound());
        }
    }

    /*
    @Override
    public void zoomRangeAxes(double factor, PlotRenderingInfo info, Point2D source) {
        // Nothing to do; prevents y-axis zooming using box drag
    }

    @Override
    public void zoomRangeAxes(double factor, PlotRenderingInfo info, Point2D source, boolean useAnchor) {
        // Nothing to do; prevents y-axis zooming using box drag
    }

    @Override
    public void zoomRangeAxes(double lowerPercent, double upperPercent, PlotRenderingInfo info, Point2D source) {
        // Nothing to do; prevents y-axis zooming using box drag
    }
    */

    @Override
    public boolean isDomainZoomable() {
        // Allow zooming the x-axis by dragging a rectangle with the mouse
        return true;
    }

    @Override
    public boolean isRangeZoomable() {
        // Do not zoom y-axis on mouse drag
        return false;
    }
}