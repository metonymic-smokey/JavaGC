
package at.jku.anttracks.gui.chart.fxchart.xy.base;

import at.jku.anttracks.gui.chart.base.AntChartPane;
import at.jku.anttracks.gui.model.ChartSelection;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.util.ThreadUtil;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

import java.util.*;
import java.util.stream.Collectors;

public abstract class XYBaseJavaFXChartPane<DATA> extends AntChartPane<ObservableList<Series<Number, Number>>, DATA> {
    public static final boolean USE_ELEMENT_WISE_VERSION = true;

    public static final XYChart.Data<Number, Number> ZERO_DATA = new XYChart.Data<>(0, 0);

    public XYChart<Number, Number> chart;

    @FXML
    protected CheckBox zoomSynchronizedCheckbox;
    @FXML
    protected Button configureButton;

    public Data<Number, Number> currentClosestDataPoint;

    private final double dataLabelYOffest = -15;

    private Node chartArea;
    private Node chartContent;
    protected final Line valueMarkerX = new Line();
    protected final Rectangle zoomRectangle = new Rectangle();
    protected final Map<Data<Number, Number>, Text> dataLabels = Collections.synchronizedMap(new HashMap<>());

    private ClickAction clickAction = ClickAction.SELECTING;
    private Point2D previousMouseDragPosition;

    private double initialYAxisLowerBound;
    private double initialYAxisUpperBound;
    private boolean initialYAxisBoundsSet = false;

    private enum ClickAction {
        ZOOMING,
        DRAGGING,
        SELECTING
    }

    public XYBaseJavaFXChartPane() {
        FXMLUtil.load(this, XYBaseJavaFXChartPane.class);
    }

    // Nothing to do
    @Override
    protected void updateChartOnUIThread(ObservableList<XYChart.Series<Number, Number>> dataset) {
    }

    @Override
    protected boolean createsNewDatasetOnUpdate() {
        return false;
    }

    // Nothing to do
    @Override
    protected ObservableList<XYChart.Series<Number, Number>> createDataSet(DATA appInfo) {
        return null;
    }

    @Override
    protected abstract ObservableList<XYChart.Series<Number, Number>> updateDataSet(DATA appInfo);

    @Override
    protected abstract EventHandler<ActionEvent> getOnConfigureAction();

    protected abstract void initializeChartSeries();

    protected abstract void updateDataLabelContent();

    private void initializeZoomRectangle() {
        zoomRectangle.setVisible(true);
        zoomRectangle.setManaged(false);
        zoomRectangle.setFill(Color.BLUE);
        zoomRectangle.setOpacity(0.2);
        getChildren().add(zoomRectangle);
    }

    protected Bounds getChartArea() {
        return new BoundingBox(chartContent.getLayoutX() + chartArea.getLayoutX(),
                               chartContent.getLayoutY() + chartArea.getLayoutY(),
                               chartArea.getLayoutBounds().getWidth(),
                               chartArea.getLayoutBounds().getHeight());
    }

    private void initializeCrosshair() {
        getChildren().addAll(valueMarkerX);
        valueMarkerX.setManaged(false);

        chart.heightProperty().addListener((observable, oldValue, newValue) -> {
            updateDataLabelLayout();

        });
        chart.widthProperty().addListener((observable, oldValue, newValue) -> {
            updateDataLabelLayout();
        });
    }

    protected void updateDataLabelLayout() {
        dataLabels.forEach((dataPoint, dataLabel) -> updateDataLabelLayout(dataPoint, dataLabel));
    }

    protected void updateDataLabelLayout(Data<Number, Number> dataPoint, Text dataLabel) {
        dataLabel.setVisible(false);
        Bounds chartArea = getChartArea();

        // TODO workaround: data point position may not have been updated otherwise
        ThreadUtil.runDeferred(() -> {
            Platform.runLater(() -> {
                if (dataPoint.getNode().getLayoutX() >= 0 && dataPoint.getNode().getLayoutX() <= chartArea.getWidth() && dataPoint.getNode()
                                                                                                                                  .getLayoutY() >= 0 && dataPoint
                        .getNode()
                        .getLayoutY() <=
                        chartArea
                                .getHeight()) {
                    dataLabel.setX(chartArea.getMinX() + dataPoint.getNode().getLayoutX());
                    dataLabel.setY(chartArea.getMinY() + dataPoint.getNode().getLayoutY() + dataLabelYOffest);
                    dataLabel.setVisible(true);
                }
            });
        }, ThreadUtil.DeferredPeriod.NORMAL);
    }

    private Data<Number, Number> getBestMatch(ChartSelection chartSelection) {
        if (chartSelection.series < chart.getData().size() && chartSelection.item < chart.getData().get(chartSelection.series).getData().size()) {
            Data<Number, Number> dataPoint = chart.getData().get(chartSelection.series).getData().get(chartSelection.item);
            if (chartSelection != null && dataPoint.getXValue().doubleValue() == chartSelection.x) {
                return dataPoint;
            } else {
                return getClosestDataPointBasedOnDataX(chartSelection.x);
            }
        } else {
            return getClosestDataPointBasedOnDataX(chartSelection.x);
        }
    }

    protected void initializeChart() {
        getChildren().remove(chart);
        getChildren().add(0, chart);

        chartArea = chart.lookup(".chart-plot-background");
        chartContent = chart.lookup(".chart-content");

        initializeChartSeries();

        chart.getData().addListener((ListChangeListener<Series<Number, Number>>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (Series<Number, Number> series : c.getAddedSubList()) {
                        series.getData().forEach(data -> {
                            Tooltip tooltip = new Tooltip(chart.getXAxis().getLabel() + ": " + data.getXValue() + "\n" + chart.getYAxis()
                                                                                                                              .getLabel() + ": " + data.getYValue() +
                                                                  "\n" +
                                                                  "Series: " + series
                                    .getName());
                            data.setExtraValue(tooltip);

                            data.getNode().setOnMouseEntered(me -> {
                                data.getNode().setCursor(Cursor.HAND);
                            });
                        });
                    }
                }
            }
        });

        setOnMouseReleased(me -> {
            //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("Chart: Mouse released");
            switch (clickAction) {
                case DRAGGING:
                    previousMouseDragPosition = null;
                    clickAction = ClickAction.SELECTING;
                    break;
                case SELECTING:
                    Optional<Integer> seriesId = getSeriesId(currentClosestDataPoint);
                    Optional<Integer> itemId = getDataPointId(currentClosestDataPoint);
                    ChartSelection selection = null;

                    if (seriesId.isPresent() && itemId.isPresent()) {
                        selection = new ChartSelection(seriesId.get(),
                                                       itemId.get(),
                                                       currentClosestDataPoint.getXValue().doubleValue(),
                                                       currentClosestDataPoint.getYValue().doubleValue(),
                                                       getPaneId());
                    }
                    if (selection != null) {
                        getChartSynchronizer().select(selection);
                        chart.getData().forEach(series -> series.getData().forEach(data -> clearDataPointFocus(data)));
                    }
                    break;
                case ZOOMING:
//                    zoomRectangle.setVisible(false);
//                    Bounds chartArea = getChartArea();
//                    Bounds zoomRectangleBounds = zoomRectangle.getLayoutBounds();
//                    NumberAxis xAxis = (NumberAxis) chart.getXAxis();
//                    double lowerXBound = xAxis.getValueForDisplay(zoomRectangleBounds.getMinX() - chartArea.getMinX()).doubleValue();
//                    double upperXBound = xAxis.getValueForDisplay(zoomRectangleBounds.getMaxX() - chartArea.getMinX()).doubleValue();
//                    NumberAxis yAxis = (NumberAxis) chart.getYAxis();
//                    double lowerYBound = yAxis.getValueForDisplay(zoomRectangleBounds.getMaxY() - chartArea.getMinY()).doubleValue();
//                    double upperYBound = yAxis.getValueForDisplay(zoomRectangleBounds.getMinY() - chartArea.getMinY()).doubleValue();
//                    if (zoomRectangleBounds.getMaxX() <= zoomRectangleBounds.getMinX()) {
//                        lowerXBound = -Double.MAX_VALUE;
//                        upperXBound = Double.MAX_VALUE;
//                        lowerYBound = -Double.MAX_VALUE;
//                        upperYBound = Double.MAX_VALUE;
//                    }
//                    if (isZoomSynchronized() && chart.getData()
//                                                     .size() > 0 && lowerXBound != getMinXValue(chart.getData()) && upperXBound != getMaxXValue(chart.getData())) {
//                        getChartSynchronizer().setZoomXBounds(lowerXBound, upperXBound);
//                    }
//                    zoomX(lowerXBound, upperXBound);
//
//                    if (!initialYAxisBoundsSet) {
//                        setInitialYAxisBounds();
//                    }
//                    zoomY(lowerYBound, upperYBound);
//
//                    clickAction = ClickAction.SELECTING;
                    break;
                default:
                    break;
            }
            //m.end();
        });

        chart.setAnimated(false);

        chart.setOnMouseEntered(me -> {
            //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("Chart: Mouse entered");
            chart.setCursor(Cursor.CROSSHAIR);
            //m.end();
        });

        chart.setOnMouseExited(me -> {
            //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("Chart: Mouse exited");
            chart.setCursor(Cursor.DEFAULT);
            clearDataPointFocus(currentClosestDataPoint);
            currentClosestDataPoint = null;
            //m.end();
        });

        chart.setOnMousePressed(me -> {
            //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("Chart: Mouse pressed");
            if (me.isPrimaryButtonDown()) {
                zoomRectangle.setX(me.getX());
                zoomRectangle.setY(me.getY());
            }
            //m.end();
        });

        chart.setOnMouseReleased(me -> {
            //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("Chart: Mouse released");
            if (getChartArea().contains(me.getX(), me.getY())) {
                chart.setCursor(Cursor.CROSSHAIR);
            } else {
                chart.setCursor(Cursor.DEFAULT);
            }
            //m.end();
        });

        chart.setOnMouseDragged(me -> {
            //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("Chart: Mouse Dragged");
            if (me.isPrimaryButtonDown()) {
                if (getChartArea().contains(me.getX(), me.getY())) {
                    clickAction = ClickAction.ZOOMING;
                }
            } else {
                chart.setCursor(Cursor.CLOSED_HAND);
                clickAction = ClickAction.DRAGGING;
            }

            switch (clickAction) {
                case DRAGGING:
//                    if (previousMouseDragPosition != null) {
//                        Bounds chartArea = getChartArea();
//                        NumberAxis xAxis = (NumberAxis) chart.getXAxis();
//                        NumberAxis yAxis = (NumberAxis) chart.getYAxis();
//
//                        double xDifference = xAxis.getValueForDisplay(previousMouseDragPosition.getX() - chartArea.getMinX())
//                                                  .doubleValue() - xAxis.getValueForDisplay(me.getX() - chartArea.getMinX()).doubleValue();
//                        double yDifference = yAxis.getValueForDisplay(previousMouseDragPosition.getY() - chartArea.getMinY())
//                                                  .doubleValue() - yAxis.getValueForDisplay(me.getY() - chartArea.getMinY()).doubleValue();
//
//                        double xLowerBound = xAxis.getLowerBound() + xDifference;
//                        double xUpperBound = xAxis.getUpperBound() + xDifference;
//                        double yLowerBound = yAxis.getLowerBound() + yDifference;
//                        double yUpperBound = yAxis.getUpperBound() + yDifference;
//
//                        double minXValue = getMinXValue(chart.getData());
//                        double maxXValue = getMaxXValue(chart.getData());
//                        if (!initialYAxisBoundsSet) {
//                            setInitialYAxisBounds();
//                        }
//                        double minYValue = initialYAxisLowerBound;
//                        double maxYValue = initialYAxisUpperBound;
//
//                        if (xLowerBound < minXValue) {
//                            xLowerBound = minXValue;
//                            xUpperBound = xAxis.getUpperBound() - (xAxis.getLowerBound() - minXValue);
//                        }
//                        if (xUpperBound > maxXValue) {
//                            xUpperBound = maxXValue;
//                            xLowerBound = xAxis.getLowerBound() - (xAxis.getUpperBound() - maxXValue);
//                        }
//                        if (yLowerBound < minYValue) {
//                            yLowerBound = minYValue;
//                            yUpperBound = yAxis.getUpperBound() - (yAxis.getLowerBound() - minYValue);
//                        }
//                        if (yUpperBound > maxYValue) {
//                            yUpperBound = maxYValue;
//                            yLowerBound = yAxis.getLowerBound() - (yAxis.getUpperBound() - maxYValue);
//                        }
//
//                        if (xLowerBound != xAxis.getLowerBound() && xUpperBound != xAxis.getUpperBound()) {
//                            if (isZoomSynchronized()) {
//                                getChartSynchronizer().setZoomXBounds(xLowerBound, xUpperBound);
//                            }
//                            zoomX(xLowerBound, xUpperBound);
//                        }
//                        if (yLowerBound != yAxis.getLowerBound() && yUpperBound != yAxis.getUpperBound()) {
//                            zoomY(yLowerBound, yUpperBound);
//                        }
//                    }
//
//                    previousMouseDragPosition = new Point2D(me.getX(), me.getY());

                    break;
                case SELECTING:
                    break;
                case ZOOMING:
                    zoomRectangle.setVisible(true);
                    if (me.getX() < getChartArea().getMaxX()) {
                        zoomRectangle.setWidth(me.getX() - zoomRectangle.getX());
                    } else {
                        zoomRectangle.setWidth(getChartArea().getMaxX() - zoomRectangle.getX());
                    }
                    if (me.getY() < getChartArea().getMaxY()) {
                        zoomRectangle.setHeight(me.getY() - zoomRectangle.getY());
                    } else {
                        zoomRectangle.setHeight(getChartArea().getMaxY() - zoomRectangle.getY());
                    }
                    break;
                default:
                    break;
            }
            //m.end();
        });

        chart.setOnMouseMoved(me -> {
            try {
                //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("Chart: Mouse moved");
                Bounds chartArea = getChartArea();
                if (chartArea.contains(me.getX(), me.getY())) {
                    chart.setCursor(Cursor.CROSSHAIR);
                    double x = me.getX() - chartArea.getMinX();
                    double y = me.getY() - chartArea.getMinY();
                    //ApplicationStatistics.Measurement m2 = ApplicationStatistics.getInstance().createMeasurement("Get Closest Data Point");
                    Data<Number, Number> newClosestDataPoint = getClosestDataPointBasedOnLayoutX(x);
                    //m2.end();
                    if (newClosestDataPoint != currentClosestDataPoint) {
                        focusDataPoint(newClosestDataPoint);
                    }
                } else {
                    chart.setCursor(Cursor.DEFAULT);
                    clearDataPointFocus(currentClosestDataPoint);
                    currentClosestDataPoint = null;
                }
                //m.end();
            } catch (Exception e) {
            }
        });

        initializeCrosshair();
        initializeZoomRectangle();
    }

    private void setInitialYAxisBounds() {
        NumberAxis yAxis = (NumberAxis) chart.getYAxis();
        // because displayed y values are not the same as y values in chart.getData()...
        initialYAxisLowerBound = yAxis.getLowerBound();
        initialYAxisUpperBound = yAxis.getUpperBound();
        initialYAxisBoundsSet = true;
    }

    @Override
    protected void postDataSetUpdate() {
        Platform.runLater(() -> updateDataLabelContent());
        updateAxis();
        updateMarker();
        initialYAxisBoundsSet = false;
        zoomX(-Double.MAX_VALUE, Double.MAX_VALUE);
        zoomY(-Double.MAX_VALUE, Double.MAX_VALUE);
    }

    private void updateAxis() {
//        double minXValue = getMinXValue(chart.getData());
//        double maxXValue = getMaxXValue(chart.getData());
//        NumberAxis xAxis = (NumberAxis) chart.getXAxis();
//
//        if (minXValue != Double.MAX_VALUE && maxXValue != Double.MIN_VALUE) {
//            xAxis.setTickUnit(new NiceScale(minXValue, maxXValue).getTickSpacing());
//            xAxis.setAutoRanging(false);
//            xAxis.setLowerBound(minXValue);
//            xAxis.setUpperBound(maxXValue);
//        }
    }

    public void zoomY(double lowerBound, double upperBound) {
//        double minYValue = getMinYValue(chart.getData());
//        double maxYValue = getMaxYValue(chart.getData());
//        NumberAxis yAxis = (NumberAxis) chart.getYAxis();
//
//        if (minYValue != Double.MAX_VALUE && minYValue != Double.MIN_VALUE && maxYValue != Double.MAX_VALUE && maxYValue != Double.MIN_VALUE) {
//            if (lowerBound <= minYValue && lowerBound <= yAxis.getLowerBound() && upperBound >= maxYValue && upperBound >= yAxis.getUpperBound()) {
//                yAxis.setAutoRanging(true);
//            } else {
//                NiceScale niceScale = new NiceScale(lowerBound, upperBound);
//                yAxis.setAutoRanging(false);
//                yAxis.setLowerBound(lowerBound);
//                yAxis.setUpperBound(upperBound);
//                yAxis.setTickUnit(niceScale.getTickSpacing());
//                Platform.runLater(() -> {
//                    updateCrosshair();
//                    updateDataLabelLayout();
//                });
//            }
//        }
    }

    public void zoomX(double lowerBound, double upperBound) {
//        double minXValue = getMinXValue(chart.getData());
//        double maxXValue = getMaxXValue(chart.getData());
//        NumberAxis xAxis = (NumberAxis) chart.getXAxis();
//        NumberAxis yAxis = (NumberAxis) chart.getYAxis();
//
//        xAxis.setAutoRanging(true);
//        if (minXValue != Double.MAX_VALUE && minXValue != Double.MIN_VALUE && maxXValue != Double.MIN_VALUE && maxXValue != Double.MAX_VALUE) {
//            if (lowerBound < minXValue) {
//                lowerBound = minXValue;
//            }
//            if (upperBound > maxXValue) {
//                upperBound = maxXValue;
//            }
//
//            if (!(lowerBound == minXValue && upperBound == maxXValue)) {
//                xAxis.setAutoRanging(false);
//                NiceScale niceScale = new NiceScale(lowerBound, upperBound);
//                xAxis.setLowerBound(lowerBound);
//                xAxis.setUpperBound(upperBound);
//                xAxis.setTickUnit(niceScale.getTickSpacing());
//                updateCrosshair();
//                updateDataLabelLayout();
//            }
//        }
//        yAxis.setAutoRanging(true);
    }

    protected void clearDataPointFocus(Data<Number, Number> dataPoint) {
        if (dataPoint != null) {
            try {
                List<Data<Number, Number>> dataPointSelections = getChartSynchronizer().getSelection()
                                                                                       .get()
                                                                                       .stream()
                                                                                       .map(selection -> getBestMatch(selection))
                                                                                       .collect(Collectors.toList());

                if (dataPoint.getNode() != null) {
                    if (dataPointSelections.contains(dataPoint)) {
                        if (!dataPoint.getNode().getStyleClass().contains("selected")) {
                            dataPoint.getNode().getStyleClass().add("selected");
                        }
                    } else {
                        dataPoint.getNode().getStyleClass().remove("selected");
                    }
                    dataPoint.getNode().getStyleClass().remove("focused");
                }
                // TODO: MW just added these null checks because NullPointerExceptions occur regulary here
                // Check out what getExtraValue() does
                //if (dataPoint.getExtraValue() != null) {
                //    ((Tooltip) dataPoint.getExtraValue()).hide();
                //}
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    protected void setCurrentClosestDataPoint(Data<Number, Number> closestDataPoint) {
        currentClosestDataPoint = closestDataPoint;
        closestDataPoint.getNode().getStyleClass().add("focused");
        Bounds boundsInScene = chart.localToScreen(chart.getBoundsInLocal());

        // TODO: MW just added these null checks because NullPointerExceptions occur regulary here
        // Check out what getExtraValue() does
        /*
        if (closestDataPoint.getExtraValue() != null) {
            ((Tooltip) closestDataPoint.getExtraValue()).show(closestDataPoint.getNode(), boundsInScene.getMinX() + 30,
                                                              boundsInScene.getMinY() - 50);
        }
        */
    }

    protected void focusDataPoint(Data<Number, Number> closestDataPoint) {
        if (closestDataPoint != null) {
            //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("Clear Data Points");
            clearDataPointFocus(currentClosestDataPoint);
            //m.end();
            //m = ApplicationStatistics.getInstance().createMeasurement("Set Closest Data Point");
            setCurrentClosestDataPoint(closestDataPoint);
            //m.end();
        }
    }

    protected double getMinXValue(List<Series<Number, Number>> seriesList) {
        double minXValue = Double.MAX_VALUE;

        for (Series<Number, Number> series : seriesList) {
            for (Data<Number, Number> dataPoint : series.getData()) {
                if (dataPoint.getXValue().doubleValue() < minXValue) {
                    minXValue = dataPoint.getXValue().doubleValue();
                }
            }
        }
        return minXValue;
    }

    protected double getMaxXValue(List<Series<Number, Number>> seriesList) {
        double maxXValue = Double.MIN_VALUE;

        for (Series<Number, Number> series : seriesList) {
            for (Data<Number, Number> dataPoint : series.getData()) {
                if (dataPoint.getXValue().doubleValue() > maxXValue) {
                    maxXValue = dataPoint.getXValue().doubleValue();
                }
            }
        }
        return maxXValue;
    }

    protected Optional<Integer> getSeriesId(Data<Number, Number> dataPoint) {
        for (XYChart.Series<Number, Number> series : chart.getData()) {
            if (series.getData().contains(dataPoint)) {
                return Optional.of(chart.getData().indexOf(series));
            }
        }
        return Optional.empty();
    }

    protected Optional<Integer> getDataPointId(Data<Number, Number> dataPoint) {
        Optional<Integer> seriesId = getSeriesId(dataPoint);
        if (seriesId.isPresent()) {
            return Optional.of(chart.getData().get(seriesId.get()).getData().indexOf(dataPoint));
        }
        return Optional.empty();
    }

    private Data<Number, Number> getClosestDataPointBasedOnLayoutDiagonal(double x, double y) {
        Data<Number, Number> closestDataPoint = null;
        double minDistance = Double.MAX_VALUE;
        for (Series<Number, Number> series : chart.getData()) {
            for (Data<Number, Number> dataPoint : series.getData()) {
                double currentDistance = Math.sqrt(Math.pow(dataPoint.getNode().getLayoutX() - x, 2) + Math.pow(dataPoint.getNode().getLayoutY() - y, 2));
                if (closestDataPoint == null || currentDistance < minDistance) {
                    closestDataPoint = dataPoint;
                    minDistance = currentDistance;
                }
            }
        }
        return closestDataPoint;
    }

    private Data<Number, Number> getClosestDataPointBasedOnLayoutX(double x) {
        Data<Number, Number> closestDataPoint = null;
        double minDistance = Double.MAX_VALUE;
        for (Series<Number, Number> series : chart.getData()) {
            for (Data<Number, Number> dataPoint : series.getData()) {
                double currentDistance = Math.abs(dataPoint.getNode().getLayoutX() - x);
                if (closestDataPoint == null || currentDistance < minDistance) {
                    closestDataPoint = dataPoint;
                    minDistance = currentDistance;
                } else if (currentDistance == minDistance) {
                    // TODO: Don't compare doubles, use some epsilon
                    // Current point is on the same x-position as the current min, let's check if it has a higher y (we want to always
                    // select the top one)
                    if (dataPoint.getNode().getLayoutY() < closestDataPoint.getNode().getLayoutY()) {
                        closestDataPoint = dataPoint;
                    }
                }
            }
        }
        return closestDataPoint;
    }

    private Data<Number, Number> getClosestDataPointBasedOnDataX(double x) {
        Data<Number, Number> closestDataPoint = null;
        double minDistance = Double.MAX_VALUE;
        for (Series<Number, Number> series : chart.getData()) {
            for (Data<Number, Number> dataPoint : series.getData()) {
                double currentDistance = Math.abs(dataPoint.getXValue().doubleValue() - x);
                if (closestDataPoint == null || currentDistance < minDistance) {
                    closestDataPoint = dataPoint;
                    minDistance = currentDistance;
                } else if (currentDistance == minDistance) {
                    // TODO: Don't compare doubles, use some epsilon
                    // Current point is on the same x-position as the current min, let's check if it has a higher y (we want to always
                    // select the top one)
                    if (dataPoint.getNode().getLayoutY() < closestDataPoint.getNode().getLayoutY()) {
                        closestDataPoint = dataPoint;
                    }
                }
            }
        }
        return closestDataPoint;
    }

    public void updateMarker() {

    }

    @Override
    public void setXLabel(String label) {
        chart.getXAxis().setLabel(label);
    }

    @Override
    public void setYLabel(String label) {
        chart.getYAxis().setLabel(label);
    }
}