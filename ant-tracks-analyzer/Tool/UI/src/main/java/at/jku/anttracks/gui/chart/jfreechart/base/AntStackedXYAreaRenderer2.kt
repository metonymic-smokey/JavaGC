package at.jku.anttracks.gui.chart.jfreechart.base

import org.jfree.chart.labels.XYToolTipGenerator
import org.jfree.chart.renderer.xy.StackedXYAreaRenderer2
import org.jfree.chart.urls.XYURLGenerator

class AntStackedXYAreaRenderer2(tooltipGen: XYToolTipGenerator?, urlGen: XYURLGenerator?) : StackedXYAreaRenderer2(tooltipGen, urlGen)