package at.jku.anttracks.gui.utils

import at.jku.anttracks.heap.statistics.Statistics
import at.jku.anttracks.parser.EventType
import org.apache.commons.math3.stat.regression.SimpleRegression
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

class DetectedTimeWindows(statistics: List<Statistics>) {

    // class for detection results
    data class TimeWindow(val fromTime: Long,
                          val toTime: Long,
                          val metric: Double)

    private val cleanedStatistics = statistics.dropWhile { it.info.meta != EventType.GC_START } // start at the first GC_START event
    private val gcCount = statistics.count { it.info.meta == EventType.GC_END }

    private val minWindowSizeInGCsForLongMemoryLeakWindow = max((gcCount * 0.05).toInt(), 5)
    private val maxWindowSizeInGCsForGCMetricWindows = Byte.MAX_VALUE.toInt()   // TODO the current diffing algorithm still uses bytes to store the object age thus we cant look at larger windows

    var longMemoryLeakAnalysisWindow: TimeWindow? = null
    var shortMemoryLeakAnalysisWindow: TimeWindow? = null
    var linRegMemoryLeakAnalysisWindow: TimeWindow? = null
    var highGCFrequencyWindow: TimeWindow? = null
    var highChurnRateWindow: TimeWindow? = null
    var highGCOverheadWindow: TimeWindow? = null

    init {
        detectHeuristicMemoryLeakAnalysisWindows()
//        detectLinearRegressionMemoryLeakAnalysisWindow()
//        detectHighGCFrequencyWindow()
        detectHighChurnRateWindow()
//        detectHighGCOverheadWindow()
    }

    private fun detectHeuristicMemoryLeakAnalysisWindows() {
        // in the case of a memory leak, the size of the reachable memory is continuously increasing
        val dataPoints = cleanedStatistics.filter { it.info.meta == EventType.GC_END && it.info.reachableBytes != null }.map { it.info.time to it.info.reachableBytes!! }

        val heuristicTime = measureTimeMillis {
            if (dataPoints.size < 2) {
                // not enough data points for memory leak detection...
                return
            }

            // now find the longest series of data points where 1) the y value fulfills the growth condition and 2) the final data point is included
            var dataPointsToDrop = 0
            var currentMaxValue = Long.MIN_VALUE
           for(i in 1 until dataPoints.size) {
               val current = dataPoints[i].second
                // keep track of maximum value up to the current point
                // maximum can be used to detect drops in reachable bytes which we want to cut off from the final memory leak window
                if (current > currentMaxValue) {
                    currentMaxValue = current
                }

                // compare each data point with its successor data point
                if (!fulfillsGrowthCondition(currentVal = current,
                                             previousVal = dataPoints[i - 1].second,
                                             firstVal = dataPoints[dataPointsToDrop].second,
                                             currentMaxValue = currentMaxValue)) {
                    // if a current data point does not fulfill the growth condition, we can drop all data points up to that point
                    dataPointsToDrop = i
                    currentMaxValue = 0
                }
            }
            val reachableBytesGrowthDataPoints = dataPoints.drop(dataPointsToDrop)
            // the reachable bytes growth window only contains sampling points -> check how many gcs it really covers
            val gcCountOfReachableBytesGrowthDataPoints = cleanedStatistics.filter { it.info.time >= reachableBytesGrowthDataPoints.first().first }.count { it.info.meta == EventType.GC_END }

            if (gcCountOfReachableBytesGrowthDataPoints >= minWindowSizeInGCsForLongMemoryLeakWindow) {
                // the window fulfilling the basic memory leak conditions is large enough
                val reachableBytesGrowth = reachableBytesGrowthDataPoints.last().second - reachableBytesGrowthDataPoints.first().second
                longMemoryLeakAnalysisWindow = TimeWindow(reachableBytesGrowthDataPoints.first().first,
                                                          cleanedStatistics.last().info.time,
                                                          reachableBytesGrowth.toDouble() / ((cleanedStatistics.last().info.time - reachableBytesGrowthDataPoints.first().first) / 1_000.0))

                if (gcCountOfReachableBytesGrowthDataPoints >= MIN_NUMBER_OF_GCs_FOR_SHORT_MEMORY_LEAK_DETECTION) {
                    // the window fulfilling the basic memory leak conditions is quite large
                    // try to find a subwindow that yielded the strongest growth in reachable memory
//                    detectShortMemoryLeakAnalysisWindow(reachableBytesGrowthDataPoints)
                }
            }
        }

        println("Heuristic memory leak window detection time: $heuristicTime ms")

        // try also linear regression based window detection
        detectLinearRegressionMemoryLeakAnalysisWindow(dataPoints)
    }

    private fun fulfillsGrowthCondition(currentVal: Long, previousVal: Long, firstVal: Long, currentMaxValue: Long): Boolean {
        if (currentVal >= previousVal) {
            // monotone growth
            return true
        }

        // there has been a negative growth
        // however, the growth condition would still be fulfilled, if the growth over the whole (current) window is still positive
        // AND if there hasn't been a substantial drop (still at least 75%) from the current max value (i.e. no isolated peak which should be cut off)
        return currentVal.toDouble() / firstVal > 1 && currentVal.toDouble() / currentMaxValue >= 0.75
    }

    private fun detectShortMemoryLeakAnalysisWindow(reachableBytesDataPoints: List<Pair<Long, Long>>) {
        val minWindowSizeInSamplingPointsForShortMemoryLeakWindow = max((reachableBytesDataPoints.size * 0.1).roundToInt(), 2)
        val maxWindowSizeInSamplingPointsForShortMemoryLeakWindow = (reachableBytesDataPoints.size * 0.5).roundToInt()

        if (minWindowSizeInSamplingPointsForShortMemoryLeakWindow > maxWindowSizeInSamplingPointsForShortMemoryLeakWindow) {
            // nothing to do...
            return
        }

        // build dataset by representing each long memory leak window data point (except the first) as: (previous datapoint time, this datapoint time, reachable bytes growth)
        val dataset = mutableListOf<TimeWindow>()
        var previousTime = reachableBytesDataPoints.first().first
        var previousReachableBytes = reachableBytesDataPoints.first().second
        for (i in 1 until reachableBytesDataPoints.size) {
            val time = reachableBytesDataPoints[i].first
            val reachableBytesGrowth = reachableBytesDataPoints[i].second - previousReachableBytes
            dataset.add(TimeWindow(previousTime, time, reachableBytesGrowth.toDouble()))
            previousTime = time
            previousReachableBytes = reachableBytesDataPoints[i].second
        }

        // now take each datapoint as a possible starting point for a time window
        // build all possible time windows by expanding to other data points (but respect the min and max width constraints!)
        // finally select the window that maximises the reachable bytes growth
        val finalWindowCandidates = mutableListOf<TimeWindow>()

        for (i in 0..dataset.size - minWindowSizeInSamplingPointsForShortMemoryLeakWindow) {
            // remember the current expansion
            var currentToTime = dataset[i].toTime
            var currentReachableBytesGrowth = 0.0

            // remember the best expansion we've seen so far
            var maxReachableBytesGrowthPerSecond = -Double.MAX_VALUE    // Double.MIN_VALUE is not negative!
            var maxReachableBytesGrowthPerSecondGCToTime = Long.MIN_VALUE

            // now keep expanding until we either hit the end of the dataset or reached the maximum allowed window size
            for (j in 0..min(maxWindowSizeInSamplingPointsForShortMemoryLeakWindow, dataset.size - 1 - i)) {
                // update the numbers...
                currentToTime = dataset[i + j].toTime
                currentReachableBytesGrowth += dataset[i + j].metric
                val currentReachableBytesGrowthPerSecond = currentReachableBytesGrowth / ((currentToTime - dataset[i].fromTime) / 1_000.0)

                if (currentReachableBytesGrowthPerSecond > maxReachableBytesGrowthPerSecond && j + 2 >= minWindowSizeInSamplingPointsForShortMemoryLeakWindow) {
                    // we have a valid window whose reachable bytes growth is higher than any we've seen so far -> remember it
                    maxReachableBytesGrowthPerSecond = currentReachableBytesGrowthPerSecond
                    maxReachableBytesGrowthPerSecondGCToTime = currentToTime
                }
            }

            // remember the best window that starts from the current datapoint
            finalWindowCandidates.add(TimeWindow(dataset[i].fromTime, maxReachableBytesGrowthPerSecondGCToTime, maxReachableBytesGrowthPerSecond))
        }

        // we now have one window for each datapoint
        // each window represents the best possible window when starting with this datapoint
        // thus now we simply select the window that has the highest reachable bytes growth
        // additionally note that a short memory leak analysis window is only detected if the growth over it is at least 30% stronger than the growth over the long memory leak analysis window
        shortMemoryLeakAnalysisWindow = finalWindowCandidates.maxBy { it.metric }?.takeIf { it.metric > longMemoryLeakAnalysisWindow!!.metric * 1.3 }
    }

    private fun detectLinearRegressionMemoryLeakAnalysisWindow(dataPoints: List<Pair<Long, Long>>) {
        val linRegTime = measureTimeMillis {
            var currentWindowGCCount: Int
            var bestK = -Double.MAX_VALUE
            var bestStartIndex = Integer.MIN_VALUE
            val regression = SimpleRegression()
            regression.addData(dataPoints.last().first.toDouble(), dataPoints.last().second.toDouble())

            for (i in dataPoints.size - 2 downTo dataPoints.size) {
                regression.addData(dataPoints[i].first.toDouble(), dataPoints[i].second.toDouble())
                currentWindowGCCount = cleanedStatistics.filter { it.info.meta == EventType.GC_END && it.info.time >= dataPoints[i].first }.count()
                if (regression.slope > bestK && currentWindowGCCount >= minWindowSizeInGCsForLongMemoryLeakWindow) {
                    bestK = regression.slope
                    bestStartIndex = i
                }
            }

            if (bestStartIndex >= 0) {
                val reachableBytesGrowth = dataPoints.last().second - dataPoints[bestStartIndex].second
                linRegMemoryLeakAnalysisWindow = TimeWindow(dataPoints[bestStartIndex].first,
                                                            cleanedStatistics.last().info.time,
                                                            reachableBytesGrowth.toDouble() / ((cleanedStatistics.last().info.time - dataPoints[bestStartIndex].first) / 1_000.0))
            }
        }
        println("Linear regression window detection time: $linRegTime ms")
    }

    private fun detectHighGCFrequencyWindow() {
        val highGCFrequencyTime = measureTimeMillis {
            // build dataset by representing each mutator and gc as: (mutator start time, gc end time, gc count)
            val dataset = mutableListOf<TimeWindow>()
            var mutatorStartTime = 0L
            for (i in 0..cleanedStatistics.size - 2 step 2) {
                val gcEndTime = cleanedStatistics[i + 1].info.time
                val gcCount = 1
                dataset.add(TimeWindow(mutatorStartTime, gcEndTime, gcCount.toDouble()))
                mutatorStartTime = gcEndTime
            }

            // now take each mutator+gc datapoint as a possible starting point for a time window
            // build all possible time windows by expanding to other data points (but respect the min and max width constraints!)
            // finally select the window that maximises the gc frequency
            val finalWindowCandidates = mutableListOf<TimeWindow>()

            // note that we ignore datapoints from which no window can be built because they don't have enough successors to satisfy the minimum window width constraint
            for (i in 0..dataset.size - 1 - MIN_WINDOW_SIZE_IN_GCs_FOR_GC_METRIC_WINDOW) {
                // remember the current expansion
                var currentGCEndTime = dataset[i].toTime
                var currentGCCount = dataset[i].metric

                // remember the best expansion we've seen so far
                var maxGCFrequency = Double.MIN_VALUE
                var maxGCFrequencyEndTime = Long.MIN_VALUE

                // now keep expanding until we either hit the end of the dataset or reached the maximum allowed window size
                for (j in 1..min(maxWindowSizeInGCsForGCMetricWindows, dataset.size - 1 - i)) {
                    // update the numbers...
                    currentGCEndTime = dataset[i + j].toTime
                    currentGCCount += dataset[i + j].metric
                    val currentGCFrequency = currentGCCount / ((currentGCEndTime - dataset[i].fromTime) / 1_000.0)

                    if (currentGCFrequency > maxGCFrequency && j >= MIN_WINDOW_SIZE_IN_GCs_FOR_GC_METRIC_WINDOW) {
                        // we have a valid window whose gc frequency is higher than any we've seen so far -> remember it
                        maxGCFrequency = currentGCFrequency
                        maxGCFrequencyEndTime = currentGCEndTime
                    }
                }

                // remember the best window starting from the current datapoint
                finalWindowCandidates.add(TimeWindow(dataset[i].fromTime, maxGCFrequencyEndTime, maxGCFrequency))
            }

            // we now have one window for each datapoint
            // each window represents the best possible window when starting with this datapoint
            // thus now we simply select the window that has the highest gc overhead
            highGCFrequencyWindow = finalWindowCandidates.maxBy { it.metric }?.takeIf { it.metric >= HIGH_GC_FREQUENCY_THRESHOLD }
        }

        println("High GC frequency window time: $highGCFrequencyTime ms")
    }

    private fun detectHighChurnRateWindow() {
        val highChurnRateTime = measureTimeMillis {
            // build dataset by representing each mutator and gc as: (mutator start time, gc end time, garbage in bytes)
            val dataset = mutableListOf<TimeWindow>()
            var mutatorStartTime = 0L
            for (i in 0..cleanedStatistics.size - 2 step 2) {
                val gcEndTime = cleanedStatistics[i + 1].info.time
                val garbageBytes = cleanedStatistics[i].totalBytes - cleanedStatistics[i + 1].totalBytes
                dataset.add(TimeWindow(mutatorStartTime, gcEndTime, garbageBytes.toDouble()))
                mutatorStartTime = gcEndTime
            }

            // now take each mutator+gc datapoint as a possible starting point for a time window
            // build all possible time windows by expanding to other data points (but respect the min and max width constraints!)
            // finally select the window that maximises the churn rate (= garbage bytes per second)
            val finalWindowCandidates = mutableListOf<TimeWindow>()

            // note that we ignore datapoints from which no window can be built because they don't have enough successors to satisfy the minimum window width constraint
            for (i in 0..dataset.size - 1 - MIN_WINDOW_SIZE_IN_GCs_FOR_GC_METRIC_WINDOW) {
                // remember the current expansion
                var currentGCEndTime = dataset[i].toTime
                var currentGarbageBytes = dataset[i].metric

                // remember the best expansion we've seen so far
                var maxChurnRate = Double.MIN_VALUE
                var maxChurnRateEndTime = Long.MIN_VALUE

                // now keep expanding until we either hit the end of the dataset or reached the maximum allowed window size
                for (j in 1..min(maxWindowSizeInGCsForGCMetricWindows, dataset.size - 1 - i)) {
                    // update the numbers...
                    currentGCEndTime = dataset[i + j].toTime
                    currentGarbageBytes += dataset[i + j].metric
                    val currentChurnRate = currentGarbageBytes / ((currentGCEndTime - dataset[i].fromTime) / 1_000.0)

                    if (currentChurnRate > maxChurnRate && j >= MIN_WINDOW_SIZE_IN_GCs_FOR_GC_METRIC_WINDOW) {
                        // we have a valid window whose gc frequency is higher than any we've seen so far -> remember it
                        maxChurnRate = currentChurnRate
                        maxChurnRateEndTime = currentGCEndTime
                    }
                }

                // remember the best window starting from the current datapoint
                finalWindowCandidates.add(TimeWindow(dataset[i].fromTime, maxChurnRateEndTime, maxChurnRate))
            }

            // we now have one window for each datapoint
            // each window represents the best possible window when starting with this datapoint
            // thus now we simply select the window that has the highest highest churn rate
            highChurnRateWindow = finalWindowCandidates.maxBy { it.metric }?.takeIf { it.metric >= HIGH_CHURN_RATE_THRESHOLD }
        }

        println("High memory churn rate window time: $highChurnRateTime ms")
    }

    private fun detectHighGCOverheadWindow() {
        // build dataset by representing each mutator and gc as: (mutator start time, gc end time, gc duration)
        val dataset = mutableListOf<TimeWindow>()
        var mutatorStartTime = 0L
        for (i in 0..cleanedStatistics.size - 2 step 2) {
            val gcEndTime = cleanedStatistics[i + 1].info.time
            val gcDuration = gcEndTime - cleanedStatistics[i].info.time
            dataset.add(TimeWindow(mutatorStartTime, gcEndTime, gcDuration.toDouble()))
            mutatorStartTime = gcEndTime
        }

        // now take each mutator+gc datapoint as a possible starting point for a time window
        // build all possible time windows by expanding to other data points (but respect the min and max width constraints!)
        // finally select the window that maximises the gc overhead
        val finalWindowCandidates = mutableListOf<TimeWindow>()

        // note that we ignore datapoints from which no window can be built because they don't have enough successors to satisfy the minimum window width constraint
        for (i in 0..dataset.size - 1 - MIN_WINDOW_SIZE_IN_GCs_FOR_GC_METRIC_WINDOW) {
            // remember the current expansion
            var currentGCEndTime = dataset[i].toTime
            var currentGCDuration = dataset[i].metric

            // remember the best expansion we've seen so far
            var maxGCOverhead = Double.MIN_VALUE
            var maxGCOverheadGCEndTime = Long.MIN_VALUE

            // now keep expanding until we either hit the end of the dataset or reached the maximum allowed window size
            for (j in 1..min(maxWindowSizeInGCsForGCMetricWindows, dataset.size - 1 - i)) {
                // update the numbers...
                currentGCEndTime = dataset[i + j].toTime
                currentGCDuration += dataset[i + j].metric
                val currentGCOverhead = currentGCDuration / (currentGCEndTime - dataset[i].fromTime)

                if (currentGCOverhead > maxGCOverhead && j >= MIN_WINDOW_SIZE_IN_GCs_FOR_GC_METRIC_WINDOW) {
                    // we have a valid window whose gc overhead is higher than any we've seen so far -> remember it
                    maxGCOverhead = currentGCOverhead
                    maxGCOverheadGCEndTime = currentGCEndTime
                }
            }

            // remember the best window starting from the current datapoint
            finalWindowCandidates.add(TimeWindow(dataset[i].fromTime, maxGCOverheadGCEndTime, maxGCOverhead))
        }

        // we now have one window for each datapoint
        // each window represents the best possible window when starting with this datapoint
        // thus now we simply select the window that has the highest gc overhead
        highGCOverheadWindow = finalWindowCandidates.maxBy { it.metric }?.takeIf { it.metric >= HIGH_GC_OVERHEAD_THRESHOLD }
    }

    companion object {
        const val HIGH_GC_FREQUENCY_THRESHOLD = 10
        const val HIGH_GC_OVERHEAD_THRESHOLD = 0.1
        const val HIGH_CHURN_RATE_THRESHOLD = 0.0001 // TODO set a threshold for an absolute churn rate (or use relative after all?)

        private val MIN_NUMBER_OF_GCs_FOR_SHORT_MEMORY_LEAK_DETECTION = 25
        private val MIN_WINDOW_SIZE_IN_GCs_FOR_GC_METRIC_WINDOW = 10
    }
}