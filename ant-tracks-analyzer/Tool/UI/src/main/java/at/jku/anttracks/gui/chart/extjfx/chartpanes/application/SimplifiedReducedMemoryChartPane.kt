package at.jku.anttracks.gui.chart.extjfx.chartpanes.application

import at.jku.anttracks.gui.chart.extjfx.AntTracksDataReducer
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.parser.EventType
import javafx.collections.FXCollections
import javafx.scene.chart.XYChart

open class SimplifiedReducedMemoryChartPane : ReducedMemoryChartPane() {
    override val yUnits = listOf(Companion.Unit.BYTES,
                                 Companion.Unit.OBJECTS,
                                 Companion.Unit.REACHABLE_BYTES)
    override val dataReducer = AntTracksDataReducer(1)

    override fun createDataset(data: AppInfo): List<XYChart.Series<Number, Number>> {
        var lastReachableBytes: Long = 0
        return listOf(XYChart.Series<Number, Number>("Occupied memory",
                                                     FXCollections.observableArrayList(data.statistics.filter { it.info.meta == EventType.GC_END }.map {
                                                         XYChart.Data<Number, Number>(it.info.time,
                                                                                      when (selectedYUnitProperty.get()) {
                                                                                          Companion.Unit.BYTES -> it.old.memoryConsumption.bytes + it.survivor.memoryConsumption.bytes
                                                                                          Companion.Unit.OBJECTS -> it.old.memoryConsumption.objects + it.survivor.memoryConsumption.objects
                                                                                          Companion.Unit.REACHABLE_BYTES -> {
                                                                                              if (it.info.reachableBytes != null) {
                                                                                                  lastReachableBytes = it.info.reachableBytes!!
                                                                                                  it.info.reachableBytes
                                                                                              } else {
                                                                                                  lastReachableBytes
                                                                                              }
                                                                                          }
                                                                                          else -> throw IllegalStateException()
                                                                                      })
                                                     })))
    }

    override fun doAfterDatasetReduction() {
        // don't show gc indicators
    }
}