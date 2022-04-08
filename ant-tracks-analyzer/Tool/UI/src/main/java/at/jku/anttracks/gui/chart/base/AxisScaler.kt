package at.jku.anttracks.gui.chart.base

import at.jku.anttracks.gui.chart.jfreechart.xy.util.AddALotXYSeries
import javafx.collections.ObservableList
import javafx.scene.chart.XYChart
import org.jfree.data.xy.DefaultTableXYDataset
import org.jfree.data.xy.XYDataItem
import org.jfree.data.xy.XYDataset
import org.jfree.data.xy.XYSeriesCollection
import java.util.*

interface AxisScaler<DS> {
    fun getXUnitDivisor(level: Int): Long

    fun getYUnitDivisor(level: Int): Long

    fun getXUnit(level: Int): String

    fun getYUnit(level: Int): String

    fun maxXUnitDownscalLevel(): Int

    fun maxYUnitDownscaleLevel(): Int

    fun updateXAxisLabel(label: String)

    fun updateYAxisLabel(label: String)

    fun processDataSetByDownscalingXUnits(dataset: DS): at.jku.anttracks.util.Tuple<Int, DS>

    fun processDataSetByDownscalingYUnits(dataset: DS): at.jku.anttracks.util.Tuple<Int, DS>

    fun postProcessDataset(dataset: DS): DS {
        val x = processDataSetByDownscalingXUnits(dataset)
        updateXAxisLabel(getXUnit(x.a))
        val y = processDataSetByDownscalingYUnits(x.b)
        updateYAxisLabel(getYUnit(y.a))
        return y.b
    }

    companion object {
        val OBJECT_UNITS = arrayOf("", "Thousands", "Million", "Billion")
        val OBJECT_DIVISOR: Long = 1000
        val MEMORY_UNITS = arrayOf("bytes", "Kb", "Mb", "Gb", "Tb", "Pb")
        val MEMORY_DIVISOR: Long = 1000
        val TIME_UNITS = arrayOf("ms", "sec", "min", "hour", "day", "week")
        val TIME_DIVISORS = longArrayOf(1000, 60, 60, 24, 7, Integer.MAX_VALUE.toLong())
    }
}

abstract class JFreeChartAxisScaler<DS : XYDataset>(var chart: at.jku.anttracks.gui.chart.base.AntChartPane<DS, *>) : AxisScaler<DS> {
    override fun updateXAxisLabel(label: String) {
        chart.setXLabel(label)
    }

    override fun updateYAxisLabel(label: String) {
        chart.setYLabel(label)
    }

    override fun processDataSetByDownscalingXUnits(dataset: DS): at.jku.anttracks.util.Tuple<Int, DS> {
        return at.jku.anttracks.util.Tuple(0, dataset)
    }

    override fun processDataSetByDownscalingYUnits(dataset: DS): at.jku.anttracks.util.Tuple<Int, DS> {
        var maxY = chart.getMaxYValue(dataset)

        var currentDivisionLevel = 0
        var totalDivisor = 1.0
        var divisor: Long

        currentDivisionLevel = 0
        while (true) {
            divisor = getYUnitDivisor(currentDivisionLevel)
            if (currentDivisionLevel == maxYUnitDownscaleLevel() || maxY < divisor) break

            maxY /= divisor
            totalDivisor *= divisor
            currentDivisionLevel++
        }

        var seriesCollection: XYSeriesCollection? = null
        var table: DefaultTableXYDataset? = null

        if (dataset is XYSeriesCollection) {
            seriesCollection = dataset
        } else if (dataset is DefaultTableXYDataset) {
            table = dataset
        } else {
            throw Exception("Unsupported dataset type")
        }

        repeat(dataset.seriesCount) {
            val newData = ArrayList<XYDataItem>()
            for (i in 0 until dataset.getItemCount(0)) {
                if (seriesCollection != null) {
                    val newY = seriesCollection.getSeries(0).getY(i).toDouble() / totalDivisor
                    newData.add(XYDataItem(seriesCollection.getSeries(0).getX(i).toDouble(), newY))
                } else if (table != null) {
                    val newY = table.getSeries(0).getY(i).toDouble() / totalDivisor
                    newData.add(XYDataItem(table.getSeries(0).getX(i).toDouble(), newY))
                } else {
                    //throw new Exception("Unsupported dataset type");
                }
            }
            if (seriesCollection != null) {
                val newSeries = AddALotXYSeries(dataset.getSeriesKey(0), true)
                newSeries.setData(newData, false)
                // Remove front-mose series and append it with new data at the end
                (dataset as XYSeriesCollection).removeSeries(0)
                (dataset as XYSeriesCollection).addSeries(newSeries)
            } else if (table != null) {
                val newSeries = AddALotXYSeries(dataset.getSeriesKey(0), false)
                newSeries.setData(newData, false)

                // Remove front-mose series and append it with new data at the end
                (dataset as DefaultTableXYDataset).removeSeries(0)
                (dataset as DefaultTableXYDataset).addSeries(newSeries)
            } else {
                //throw new Exception("Unsupported dataset type");
            }
        }

        return at.jku.anttracks.util.Tuple<Int, DS>(currentDivisionLevel, dataset)
    }

    class AliveDeadScaler<DS : XYDataset>(chart: at.jku.anttracks.gui.chart.base.AntChartPane<DS, *>,
                                          val unit: ApplicationChartFactory.AliveDeadMemoryConsumptionUnit) : JFreeChartAxisScaler<DS>(chart) {
        override fun maxXUnitDownscalLevel(): Int = AxisScaler.TIME_UNITS.size - 1

        override fun getXUnitDivisor(level: Int): Long = AxisScaler.TIME_DIVISORS[level]

        override fun getXUnit(level: Int): String = "Time [" + AxisScaler.TIME_UNITS[level] + "]"

        override fun maxYUnitDownscaleLevel(): Int = when (unit) {
            ApplicationChartFactory.AliveDeadMemoryConsumptionUnit.RELATIVE -> 0
            ApplicationChartFactory.AliveDeadMemoryConsumptionUnit.OBJECTS -> AxisScaler.OBJECT_UNITS.size - 1
            ApplicationChartFactory.AliveDeadMemoryConsumptionUnit.BYTES -> AxisScaler.MEMORY_UNITS.size - 1
        }

        override fun getYUnitDivisor(level: Int): Long = when (unit) {
            ApplicationChartFactory.AliveDeadMemoryConsumptionUnit.RELATIVE -> 1
            ApplicationChartFactory.AliveDeadMemoryConsumptionUnit.OBJECTS -> AxisScaler.OBJECT_DIVISOR
            ApplicationChartFactory.AliveDeadMemoryConsumptionUnit.BYTES -> AxisScaler.MEMORY_DIVISOR
        }

        override fun getYUnit(level: Int): String = when (unit) {
            ApplicationChartFactory.AliveDeadMemoryConsumptionUnit.RELATIVE -> "Objects [%]"
            ApplicationChartFactory.AliveDeadMemoryConsumptionUnit.OBJECTS -> if (level == 0) "Objects" else "Objects [" + AxisScaler.OBJECT_UNITS[level] + "]"
            ApplicationChartFactory.AliveDeadMemoryConsumptionUnit.BYTES -> "Memory [" + AxisScaler.MEMORY_UNITS[level] + "]"
        }
    }

    class GCScaler<DS : XYDataset>(chart: at.jku.anttracks.gui.chart.base.AntChartPane<DS, *>) : JFreeChartAxisScaler<DS>(chart) {
        override fun getXUnitDivisor(level: Int): Long = AxisScaler.TIME_DIVISORS[level]

        override fun getYUnitDivisor(level: Int): Long = AxisScaler.TIME_DIVISORS[level]

        override fun getXUnit(level: Int): String = "Time [" + AxisScaler.TIME_UNITS[level] + "]"

        override fun getYUnit(level: Int): String = "Pause [" + AxisScaler.TIME_UNITS[level] + "]"

        override fun maxXUnitDownscalLevel(): Int = AxisScaler.TIME_UNITS.size - 1

        override fun maxYUnitDownscaleLevel(): Int = AxisScaler.TIME_UNITS.size - 1
    }

    class ObjectsScaler<DS : XYDataset>(chart: at.jku.anttracks.gui.chart.base.AntChartPane<DS, *>) : JFreeChartAxisScaler<DS>(chart) {
        override fun maxXUnitDownscalLevel(): Int = AxisScaler.TIME_UNITS.size - 1

        override fun getXUnitDivisor(level: Int): Long = AxisScaler.TIME_DIVISORS[level]

        override fun getXUnit(level: Int): String = "Time [" + AxisScaler.TIME_UNITS[level] + "]"

        override fun getYUnit(level: Int): String = if (level == 0) "Objects" else "Objects [" + AxisScaler.OBJECT_UNITS[level] + "]"

        override fun maxYUnitDownscaleLevel(): Int = AxisScaler.OBJECT_UNITS.size - 1

        override fun getYUnitDivisor(level: Int): Long = AxisScaler.OBJECT_DIVISOR
    }

    class BytesScaler<DS : XYDataset>(chart: at.jku.anttracks.gui.chart.base.AntChartPane<DS, *>) : JFreeChartAxisScaler<DS>(chart) {
        override fun maxYUnitDownscaleLevel(): Int = AxisScaler.MEMORY_UNITS.size - 1

        override fun getYUnitDivisor(level: Int): Long = AxisScaler.MEMORY_DIVISOR

        override fun getYUnit(level: Int): String = "Memory [" + AxisScaler.MEMORY_UNITS[level] + "]"

        override fun maxXUnitDownscalLevel(): Int = AxisScaler.TIME_UNITS.size - 1

        override fun getXUnitDivisor(level: Int): Long = AxisScaler.TIME_DIVISORS[level]

        override fun getXUnit(level: Int): String = "Time [" + AxisScaler.TIME_UNITS[level] + "]"
    }

    class NoAxisScaler<DS : XYDataset>(chart: at.jku.anttracks.gui.chart.base.AntChartPane<DS, *>) : JFreeChartAxisScaler<DS>(chart) {
        override fun getXUnitDivisor(level: Int): Long = 1

        override fun getYUnitDivisor(level: Int): Long = 1

        override fun getXUnit(level: Int): String = ""

        override fun getYUnit(level: Int): String = ""

        override fun maxXUnitDownscalLevel(): Int = 0

        override fun maxYUnitDownscaleLevel(): Int = 0

        override fun updateXAxisLabel(label: String) {
            // Do not change default label
        }

        override fun updateYAxisLabel(label: String) {
            // Do not change default label
        }
    }
}

abstract class JavaFXAxisScaler(protected var chart: at.jku.anttracks.gui.chart.base.AntChartPane<ObservableList<XYChart.Series<Number, Number>>, *>) : AxisScaler<ObservableList<XYChart.Series<Number, Number>>> {
    override fun updateXAxisLabel(label: String) {
        chart.setXLabel(label)
    }

    override fun updateYAxisLabel(label: String) {
        chart.setYLabel(label)
    }

    override fun processDataSetByDownscalingXUnits(dataset: ObservableList<XYChart.Series<Number, Number>>): at.jku.anttracks.util.Tuple<Int, ObservableList<XYChart.Series<Number, Number>>> {
        return at.jku.anttracks.util.Tuple(0, dataset)
    }

    override fun processDataSetByDownscalingYUnits(dataset: ObservableList<XYChart.Series<Number, Number>>): at.jku.anttracks.util.Tuple<Int, ObservableList<XYChart.Series<Number, Number>>> {
        var maxY = 0.0
        for (series in dataset) {
            for (point in series.data) {
                if (maxY < point.xValue.toDouble()) {
                    maxY = point.xValue.toDouble()
                }
            }
        }

        var currentDivisionLevel = 0
        var totalDivisor = 1.0
        var divisor: Long

        currentDivisionLevel = 0
        while (true) {
            divisor = getYUnitDivisor(currentDivisionLevel)
            if (currentDivisionLevel == maxYUnitDownscaleLevel() || maxY < divisor) break

            maxY /= divisor
            totalDivisor *= divisor
            currentDivisionLevel++
        }

        for (series in dataset) {
            for (point in series.data) {
                point.yValue = point.yValue.toDouble() / totalDivisor
            }
        }

        return at.jku.anttracks.util.Tuple(currentDivisionLevel, dataset)
    }

    class AliveDeadScaler(chart: at.jku.anttracks.gui.chart.base.AntChartPane<ObservableList<XYChart.Series<Number, Number>>, *>,
                          var unit: ApplicationChartFactory.AliveDeadMemoryConsumptionUnit) : JavaFXAxisScaler(chart) {
        override fun maxXUnitDownscalLevel(): Int = AxisScaler.TIME_UNITS.size - 1

        override fun getXUnitDivisor(level: Int): Long = AxisScaler.TIME_DIVISORS[level]

        override fun getXUnit(level: Int): String = "Time [" + AxisScaler.TIME_UNITS[level] + "]"

        override fun maxYUnitDownscaleLevel(): Int = when (unit) {
            ApplicationChartFactory.AliveDeadMemoryConsumptionUnit.RELATIVE -> 0
            ApplicationChartFactory.AliveDeadMemoryConsumptionUnit.OBJECTS -> AxisScaler.OBJECT_UNITS.size - 1
            ApplicationChartFactory.AliveDeadMemoryConsumptionUnit.BYTES -> AxisScaler.MEMORY_UNITS.size - 1
        }

        override fun getYUnitDivisor(level: Int): Long = when (unit) {
            ApplicationChartFactory.AliveDeadMemoryConsumptionUnit.RELATIVE -> 1
            ApplicationChartFactory.AliveDeadMemoryConsumptionUnit.OBJECTS -> AxisScaler.OBJECT_DIVISOR
            ApplicationChartFactory.AliveDeadMemoryConsumptionUnit.BYTES -> AxisScaler.MEMORY_DIVISOR
        }

        override fun getYUnit(level: Int): String = when (unit) {
            ApplicationChartFactory.AliveDeadMemoryConsumptionUnit.RELATIVE -> "Objects [%]"
            ApplicationChartFactory.AliveDeadMemoryConsumptionUnit.OBJECTS -> if (level == 0) "Objects" else "Objects [" + AxisScaler.OBJECT_UNITS[level] + "]"
            ApplicationChartFactory.AliveDeadMemoryConsumptionUnit.BYTES -> "Memory [" + AxisScaler.MEMORY_UNITS[level] + "]"
        }
    }

    class GCScaler(chart: at.jku.anttracks.gui.chart.base.AntChartPane<ObservableList<XYChart.Series<Number, Number>>, *>) : JavaFXAxisScaler(chart) {
        override fun getXUnitDivisor(level: Int): Long = AxisScaler.TIME_DIVISORS[level]

        override fun getYUnitDivisor(level: Int): Long = AxisScaler.TIME_DIVISORS[level]

        override fun getXUnit(level: Int): String = "Time [" + AxisScaler.TIME_UNITS[level] + "]"

        override fun getYUnit(level: Int): String = "Pause [" + AxisScaler.TIME_UNITS[level] + "]"

        override fun maxXUnitDownscalLevel(): Int = AxisScaler.TIME_UNITS.size - 1

        override fun maxYUnitDownscaleLevel(): Int = AxisScaler.TIME_UNITS.size - 1
    }

    class ObjectsScaler(chart: at.jku.anttracks.gui.chart.base.AntChartPane<ObservableList<XYChart.Series<Number, Number>>, *>) : JavaFXAxisScaler(chart) {
        override fun maxXUnitDownscalLevel(): Int = AxisScaler.TIME_UNITS.size - 1

        override fun getXUnitDivisor(level: Int): Long = AxisScaler.TIME_DIVISORS[level]

        override fun getXUnit(level: Int): String = "Time [" + AxisScaler.TIME_UNITS[level] + "]"

        override fun getYUnit(level: Int): String = if (level == 0) "Objects" else "Objects [" + AxisScaler.OBJECT_UNITS[level] + "]"

        override fun maxYUnitDownscaleLevel(): Int = AxisScaler.OBJECT_UNITS.size - 1

        override fun getYUnitDivisor(level: Int): Long = AxisScaler.OBJECT_DIVISOR
    }

    class BytesScaler(chart: at.jku.anttracks.gui.chart.base.AntChartPane<ObservableList<XYChart.Series<Number, Number>>, *>) : JavaFXAxisScaler(chart) {
        override fun maxYUnitDownscaleLevel(): Int = AxisScaler.MEMORY_UNITS.size - 1

        override fun getYUnitDivisor(level: Int): Long = AxisScaler.MEMORY_DIVISOR

        override fun getYUnit(level: Int): String = "Memory [" + AxisScaler.MEMORY_UNITS[level] + "]"

        override fun maxXUnitDownscalLevel(): Int = AxisScaler.TIME_UNITS.size - 1

        override fun getXUnitDivisor(level: Int): Long = AxisScaler.TIME_DIVISORS[level]

        override fun getXUnit(level: Int): String = "Time [" + AxisScaler.TIME_UNITS[level] + "]"
    }

    class NoAxisScaler(chart: AntChartPane<ObservableList<XYChart.Series<Number, Number>>, *>) : JavaFXAxisScaler(chart) {
        override fun getXUnitDivisor(level: Int): Long = 1

        override fun getYUnitDivisor(level: Int): Long = 1

        override fun getXUnit(level: Int): String = ""

        override fun getYUnit(level: Int): String = ""

        override fun maxXUnitDownscalLevel(): Int = 0

        override fun maxYUnitDownscaleLevel(): Int = 0

        override fun updateXAxisLabel(label: String) {
            // Do not change default label
        }

        override fun updateYAxisLabel(label: String) {
            // Do not change default label
        }
    }
}

class NoAxisScaler<DS> : AxisScaler<DS> {
    override fun getXUnitDivisor(level: Int): Long = 1

    override fun getYUnitDivisor(level: Int): Long = 1

    override fun getXUnit(level: Int): String = ""

    override fun getYUnit(level: Int): String = ""

    override fun maxXUnitDownscalLevel(): Int = 0

    override fun maxYUnitDownscaleLevel(): Int = 0

    override fun updateXAxisLabel(label: String) {
        // Do not change default label
    }

    override fun updateYAxisLabel(label: String) {
        // Do not change default label
    }

    override fun processDataSetByDownscalingXUnits(dataset: DS): at.jku.anttracks.util.Tuple<Int, DS> {
        return at.jku.anttracks.util.Tuple(0, dataset)
    }

    override fun processDataSetByDownscalingYUnits(dataset: DS): at.jku.anttracks.util.Tuple<Int, DS> {
        return at.jku.anttracks.util.Tuple(0, dataset)
    }
}