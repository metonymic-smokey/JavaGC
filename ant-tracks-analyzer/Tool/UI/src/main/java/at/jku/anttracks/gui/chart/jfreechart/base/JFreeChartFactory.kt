package at.jku.anttracks.gui.chart.jfreechart.base

import at.jku.anttracks.gui.chart.base.AntChartPane
import at.jku.anttracks.gui.chart.base.ChartSynchronizer
import org.jfree.chart.JFreeChart
import org.jfree.chart.StandardChartTheme
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.block.BlockBorder
import org.jfree.chart.labels.*
import org.jfree.chart.plot.PlotOrientation
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.renderer.xy.StackedXYAreaRenderer
import org.jfree.chart.renderer.xy.XYItemRenderer
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer
import org.jfree.chart.title.LegendTitle
import org.jfree.chart.title.TextTitle
import org.jfree.chart.ui.RectangleEdge
import org.jfree.chart.ui.TextAnchor
import org.jfree.chart.urls.XYURLGenerator
import org.jfree.chart.util.SortOrder
import org.jfree.data.xy.DefaultTableXYDataset
import org.jfree.data.xy.TableXYDataset
import org.jfree.data.xy.XYDataset
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Shape
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage

object JFreeChartFactory {
    private val currentTheme = StandardChartTheme("JFree")
    private val fontName = "Arial"

    fun createStackedXYAreaChart(chartPane: AntChartPane<out XYDataset, *>,
                                 title: String,
                                 subTitle: String?,
                                 xAxisLabel: String,
                                 yAxisLabel: String,
                                 dataset: TableXYDataset,
                                 labelGenerator: XYItemLabelGenerator?,
                                 toolTipGenerator: XYToolTipGenerator?,
                                 urlGenerator: XYURLGenerator?,
                                 synchronizer: ChartSynchronizer,
                                 supportSelection: Boolean = true): AntJFreeChart {
        // Copied from ChartFactory
        val xAxis = NumberAxis(xAxisLabel)
        xAxis.autoRangeIncludesZero = false
        xAxis.lowerMargin = 0.0
        xAxis.upperMargin = 0.0
        val yAxis = NumberAxis(yAxisLabel)
        //if (urls) {
        //    urlGenerator = new StandardXYURLGenerator();
        //}

        val labelRenderer = object : StackedXYAreaRenderer(StackedXYAreaRenderer.SHAPES) {
            private val serialVersionUID = -3889774254281282048L
            private val NULL_RECTANGLE = Rectangle2D.Float(0.0f, 0.0f, 0.0f, 0.0f)

            override fun getDataBoundsIncludesVisibleSeriesOnly(): Boolean {
                return true
            }

            override fun getItemShape(series: Int, item: Int): Shape {
                if (labelGenerator != null) {
                    val label = labelGenerator.generateLabel(plot.dataset, series, item)
                    if (label != null && !label.trim { it <= ' ' }.isEmpty()) {
                        /*
                    Font tr = new Font("TimesRoman", Font.PLAIN, 18);
                    Font trb = new Font("TimesRoman", Font.BOLD, 18);
                    Font tri = new Font("TimesRoman", Font.ITALIC, 18);
                    Font trbi = new Font("TimesRoman", Font.BOLD+Font.ITALIC, 18);
                    Font h = new Font("Helvetica", Font.PLAIN, 18);
                    Font c = new Font("Courier", Font.PLAIN, 18);
                    Font d = new Font("Dialog", Font.PLAIN, 18);
                    Font z = new Font("ZapfDingbats", Font.PLAIN, 18);
                    */

                        val font = Font("Helvetica", Font.PLAIN, 12)

                        val img = BufferedImage(1000, 1000, BufferedImage.TYPE_INT_ARGB)
                        val g2 = img.createGraphics()

                        try {
                            val vect = font.createGlyphVector(g2.fontRenderContext, label)

                            return vect.getOutline(0f, (-vect.visualBounds.y).toFloat())
                        } finally {
                            g2.dispose()
                        }
                    }
                }
                return NULL_RECTANGLE
            }
        }
        labelRenderer.defaultItemLabelGenerator = labelGenerator
        labelRenderer.defaultItemLabelsVisible = true
        labelRenderer.shapePaint = Color.BLACK
        labelRenderer.defaultStroke = BasicStroke(0.01f)
        labelRenderer.defaultOutlineStroke = BasicStroke(0.01f)

        val mainRenderer = AntStackedXYAreaRenderer2(toolTipGenerator, urlGenerator)
        val lineRenderer = StackedXYAreaRenderer(StackedXYAreaRenderer.LINES)
        mainRenderer.dataBoundsIncludesVisibleSeriesOnly = true
        mainRenderer.plot
        val plot = AntJFreeChartXYPlot(chartPane, dataset, xAxis, yAxis, mainRenderer, synchronizer)
        plot.orientation = PlotOrientation.VERTICAL
        plot.rangeAxis = yAxis  // forces recalculation of the axis range

        val legend = false
        val chart = AntJFreeChart(chartPane, title, Font(fontName, Font.BOLD, 26), plot, legend, synchronizer)
        currentTheme.apply(chart)
        // -----------------------------

        if (subTitle != null) {
            chart.addSubtitle(TextTitle(subTitle, Font(fontName, Font.PLAIN, 18)))
        }

        val chartSeriesColors = chartPane.seriesColors
        for (cId in 0..9) {
            mainRenderer.setSeriesPaint(cId, chartSeriesColors[cId % chartSeriesColors.size])
            mainRenderer.setSeriesPositiveItemLabelPosition(cId, ItemLabelPosition(ItemLabelAnchor.CENTER, TextAnchor.BOTTOM_LEFT, TextAnchor.BOTTOM_LEFT, 45.0))
            lineRenderer.setSeriesPaint(cId, Color.BLACK)
            lineRenderer.setSeriesStroke(cId, BasicStroke(0.75f))
        }
        plot.setRenderer(0, AntRendererForStackedCharts(synchronizer, supportSelection))
        plot.setRenderer(1, labelRenderer)
        plot.setRenderer(2, lineRenderer)
        plot.setRenderer(3, mainRenderer)
        plot.setDataset(0, plot.dataset)
        plot.setDataset(1, plot.dataset)
        plot.setDataset(2, plot.dataset)
        plot.setDataset(3, plot.dataset)
        formatChart(chart, mainRenderer)

        return chart
    }

    fun createLineXYChart(chartPane: AntChartPane<out XYDataset, *>,
                          title: String,
                          subTitle: String?,
                          xAxisLabel: String,
                          yAxisLabel: String,
                          dataset: DefaultTableXYDataset?,
                          labelGenerator: XYItemLabelGenerator?,
                          synchronizer: ChartSynchronizer,
                          supportSelection: Boolean = true): AntJFreeChart {
        val xAxis = NumberAxis(xAxisLabel)
        xAxis.autoRangeIncludesZero = false
        val yAxis = NumberAxis(yAxisLabel)
        val mainRenderer = XYLineAndShapeRenderer(true, false)
        mainRenderer.dataBoundsIncludesVisibleSeriesOnly = true
        mainRenderer.defaultItemLabelGenerator = labelGenerator
        mainRenderer.defaultItemLabelsVisible = true
        val plot = AntJFreeChartXYPlot(chartPane, dataset, xAxis, yAxis, mainRenderer, synchronizer)
        plot.orientation = PlotOrientation.VERTICAL
        mainRenderer.defaultToolTipGenerator = StandardXYToolTipGenerator()
        //renderer.setURLGenerator(new StandardXYURLGenerator());
        val legend = false
        val chart = AntJFreeChart(chartPane, title, JFreeChart.DEFAULT_TITLE_FONT, plot, legend, synchronizer)
        currentTheme.apply(chart)

        if (subTitle != null) {
            chart.addSubtitle(TextTitle(subTitle, Font(fontName, Font.PLAIN, 14)))
        }

        val chartSeriesColors = chartPane.seriesColors

        for (series in 0..9) {
            mainRenderer.setSeriesStroke(series, BasicStroke(4f))
            mainRenderer.setSeriesPaint(series, chartSeriesColors[series % chartSeriesColors.size])
            mainRenderer.useOutlinePaint = true
            mainRenderer.drawOutlines = true
            mainRenderer.setSeriesOutlinePaint(series, Color.BLACK)
            mainRenderer.setSeriesOutlineStroke(series, BasicStroke(1f))
        }
        plot.setRenderer(0, AntRendererForLineCharts(synchronizer, supportSelection))
        plot.setRenderer(1, mainRenderer)
        plot.setDataset(0, plot.dataset)
        plot.setDataset(1, plot.dataset)
        formatChart(chart, mainRenderer)

        return chart
    }

    private fun formatChart(chart: AntJFreeChart, mainRenderer: XYItemRenderer) {
        chart.title.font = Font(fontName, Font.BOLD, 18)

        val plot = (chart.plot as XYPlot).apply {
            isDomainPannable = true
            isRangePannable = false
            //plot.setDomainCrosshairVisible(true);
            //plot.setRangeCrosshairVisible(true);
            domainAxis.lowerMargin = 0.0
            domainAxis.labelFont = Font(fontName, Font.BOLD, 14)
            domainAxis.tickLabelFont = Font(fontName, Font.PLAIN, 12)
            rangeAxis.labelFont = Font(fontName, Font.BOLD, 14)
            rangeAxis.tickLabelFont = Font(fontName, Font.PLAIN, 12)
        }
        //chart.getLegend().setHorizontalAlignment(HorizontalAlignment.CENTER);
        //LegendTitle legend = chart.getLegend();
        //legend.setPosition(RectangleEdge.RIGHT);

        val legend = LegendTitle(mainRenderer).apply {
            backgroundPaint = Color(0.0f, 0.0f, 0.0f, 0.85f) // Black-ish
            itemPaint = Color(1.0f, 1.0f, 1.0f, 1.0f) // WHITE
            frame = BlockBorder(Color.BLACK)
            position = RectangleEdge.LEFT
            itemFont = Font(fontName, Font.BOLD, 12)
            frame = BlockBorder.NONE
            sortOrder = SortOrder.DESCENDING
        }
        // Overridden in AntJFreeChart!
        // The legend is not added as subtitle (which would be the default) but as an annotation on the plot
        chart.addLegend(legend)

        plot.rangeGridlinePaint = Color.BLACK
        plot.backgroundPaint = Color.WHITE
        chart.backgroundPaint = Color(1.0f, 1.0f, 1.0f, 1.0f) // WHITE
    }
}