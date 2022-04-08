package at.jku.anttracks.gui.chart.jfreechart.xy.base;

import at.jku.anttracks.gui.chart.jfreechart.base.JFreeChartPane;
import at.jku.anttracks.gui.utils.FXMLUtil;
import javafx.application.Platform;
import org.jetbrains.annotations.NotNull;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYBoxAnnotation;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;

import java.awt.*;
import java.util.List;
import java.util.*;

public abstract class XYJFreeChartPane<DATA, DS extends XYDataset> extends JFreeChartPane<DS, DATA> {
    private List<XYAnnotation> timeWindowAnnotations = new ArrayList<>();

    public XYJFreeChartPane() {
        FXMLUtil.load(this, XYJFreeChartPane.class);
    }

    @Override
    protected void updateChartOnUIThread(DS dataset) {
        chartViewer.getChart().getXYPlot().setDataset(dataset);

        // Set color for main renderer (i.e., the renderer at the last postition in the renderer list)
        int rendererCount = chartViewer.getChart().getXYPlot().getRendererCount();
        for (int series = 0; series < 9; series++) {
            Color[] colors = getSeriesColors();
            if (colors.length == 0) {
                colors = new Color[]{Color.BLACK};
            }
            chartViewer.getChart().getXYPlot().getRenderer(rendererCount - 1).setSeriesPaint(series, colors[series % colors.length]);
        }
    }

    @Override
    public void zoomX(double lowerBound, double upperBound) {
        if (lowerBound == Double.MIN_VALUE || upperBound == Double.MAX_VALUE) {
            chartViewer.getChart().getXYPlot().getDomainAxis().setAutoRange(true);
        } else {
            chartViewer.getChart().getXYPlot().getDomainAxis().setRange(lowerBound, upperBound);
            // updateCrosshair();
            // updateDataLabelLayout();
        }
        chartViewer.getChart().getXYPlot().getRangeAxis().setUpperMargin(getTopMarginPercent());
        chartViewer.getChart().getXYPlot().getRangeAxis().setAutoRange(true);
    }

    @Override
    public void setXLabel(
            @NotNull
                    String label) {
        Platform.runLater(() -> {
            chartViewer.getChart().getXYPlot().getDomainAxis().setLabel(label);
        });
    }

    @Override
    public void setYLabel(
            @NotNull
                    String label) {
        Platform.runLater(() -> {
            chartViewer.getChart().getXYPlot().getRangeAxis().setLabel(label);
        });
    }

    @Override
    public void updateMarker() {
        chartViewer.getChart().fireChartChanged();
    }

    public void clearHighlightedAreas() {
        timeWindowAnnotations.forEach(highlightedArea -> chartViewer.getChart().getXYPlot().removeAnnotation(highlightedArea));
        timeWindowAnnotations.clear();
    }

    public void highlightArea(long fromX, long toX) {
        XYPlot plot = this.chartViewer.getChart().getXYPlot();
        double maxYDataValue = getMaxY(fromX, toX);

        XYLineAnnotation leftLineAnnotation = new XYLineAnnotation(fromX,
                                                                   0,
                                                                   fromX,
                                                                   maxYDataValue,
                                                                   new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0),
                                                                   new Color(0, 0, 0, 255));
        XYLineAnnotation rightLineAnnotation = new XYLineAnnotation(toX,
                                                                    0,
                                                                    toX,
                                                                    maxYDataValue,
                                                                    new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0),
                                                                    new Color(0, 0, 0, 255));
        XYBoxAnnotation fillAnnotation = new XYBoxAnnotation(fromX,
                                                             0,
                                                             toX,
                                                             maxYDataValue,
                                                             new BasicStroke(0f),
                                                             new Color(0, 0, 0, 0),
                                                             new Color(0, 0, 0, 75));

        plot.addAnnotation(leftLineAnnotation);
        plot.addAnnotation(rightLineAnnotation);
        plot.addAnnotation(fillAnnotation);
        timeWindowAnnotations.add(leftLineAnnotation);
        timeWindowAnnotations.add(rightLineAnnotation);
        timeWindowAnnotations.add(fillAnnotation);
    }

    private double getMaxY(long fromX, long toX) {
        XYDataset dataset = chartViewer.getChart().getXYPlot().getDataset();

        Map<Double, Double> stackedYValues = new HashMap<>();

        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            for (int j = 0; j < dataset.getItemCount(i); j++) {
                double xVal = dataset.getXValue(i, j);
                if (xVal >= fromX && xVal <= toX) {
                    stackedYValues.merge(xVal, dataset.getYValue(i, j), (oldVal, newVal) -> oldVal + newVal);
                }
            }
        }

        return stackedYValues.values().stream().max(Comparator.comparingDouble(d -> d)).orElse(0.0);
    }
}
