package at.jku.anttracks.gui.chart.extjfx

import at.jku.anttracks.util.filterInRangePadded
import cern.extjfx.chart.data.ChartData
import cern.extjfx.chart.data.DataReducer
import cern.extjfx.chart.data.Range
import javafx.scene.chart.XYChart
import kotlin.math.roundToInt

class AntTracksDataReducer(val preservedDataPointGroupSize: Int) : DataReducer<Number, Number> {

    override fun reduce(data: ChartData<Number, Number>, dataRange: Range<Double>, maxDataPoints: Int): List<XYChart.Data<Number, Number>> =
            data.toList()    // TODO for performance improvement do toList() and filterInRangePadded(...) in one iteration
                    .filterInRangePadded(1, dataRange.lowerBound..dataRange.upperBound, { it.xValue.toDouble() })
                    .chunked(preservedDataPointGroupSize)
                    .distinctBy {
                        getXValuePixelIndex(maxDataPoints.toDouble(),
                                            (it.first().xValue.toDouble() + it.last().xValue.toDouble()) / 2.0,
                                            dataRange)
                    }
                    .flatten()

    private fun getXValuePixelIndex(xAxisPixelWidth: Double,
                                    xValue: Double,
                                    dataRange: Range<Double>): Int {
        if (dataRange.lowerBound == dataRange.upperBound) {
            return 0
        }

        val pixelsPerDataUnit = xAxisPixelWidth / (dataRange.upperBound - dataRange.lowerBound)
        val xInPixels = (xValue - dataRange.lowerBound) * pixelsPerDataUnit
        return xInPixels.roundToInt()
    }

    private fun ChartData<Number, Number>.toList(): List<XYChart.Data<Number, Number>> {
        val ret = mutableListOf<XYChart.Data<Number, Number>>()
        for (i in 0 until size()) {
            ret.add(get(i))
        }
        return ret
    }
}