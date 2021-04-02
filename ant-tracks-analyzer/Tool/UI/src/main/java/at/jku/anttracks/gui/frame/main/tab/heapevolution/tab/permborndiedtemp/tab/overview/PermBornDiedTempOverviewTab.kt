package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.tab.overview

import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane
import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane.Companion.SynchronizationOption.Action.*
import at.jku.anttracks.gui.chart.extjfx.chartpanes.desynchable
import at.jku.anttracks.gui.chart.extjfx.chartpanes.permborndiedtemp.ReducedPermBornDiedTempChartPane
import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.model.PermBornDiedTempData
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.model.PermBornDiedTempInfo
import at.jku.anttracks.gui.model.*
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.parser.heapevolution.HeapEvolutionData
import at.jku.anttracks.parser.heapevolution.HeapEvolutionUpdateListener
import javafx.beans.property.SimpleStringProperty
import javafx.fxml.FXML
import org.controlsfx.control.PopOver

class PermBornDiedTempOverviewTab : ApplicationBaseTab() {
    @FXML
    private lateinit var objectsChartPane: ReducedPermBornDiedTempChartPane

    @FXML
    private lateinit var bytesChartPane: ReducedPermBornDiedTempChartPane

    override val componentDescriptions by lazy {
        listOf(Triple(objectsChartPane,
                      Description("This chart shows you the number of Perm, Born, Died and Temp objects at each beginning and end of a garbage collection.")
                              .linebreak()
                              .appendDefault("Note that the number of Perm objects remains constant over the timeframe - that is because by definition Perm objects have been alive in the heap at both ends of the timeframe.")
                              .linebreak()
                              .appendDefault("The number of Born objects on the other hand can only rise - that is because by definition they have to be alive at the end of the timeframe. The chart shows you at which point in the timeframe they have been allocated.")
                              .linebreak()
                              .appendDefault("Analogously the number of Died objects can only decrease and the number of Temp objects can rise and fall."),
                      PopOver.ArrowLocation.RIGHT_TOP),
               Triple(bytesChartPane,
                      Description("This chart shows you the same thing as the one above just that instead of counting the objects themselves, we count the bytes of memory they occupy."),
                      PopOver.ArrowLocation.LEFT_TOP))
    }

    override val initialTabIdeas by lazy {
        listOf(Idea("Perm/Born/Died/Temp overview tab",
                    Description("In this tab you can observe the development of Perm, Born, Died and Temp object sets over the timeframe you previously selected.")
                            .linebreak()
                            .appendEmphasized("Perm objects ")
                            .appendDefault("have been alive at the start of the timeframe and are still alive at the end.")
                            .linebreak()
                            .appendEmphasized("Born objects ")
                            .appendDefault("have been born within this timeframe and are still alive at the end.")
                            .linebreak()
                            .appendEmphasized("Died objects ")
                            .appendDefault("were alive at the start of the timeframe but died in it.")
                            .linebreak()
                            .appendEmphasized("Temp objects ")
                            .appendDefault("were born within this timeframe and died in it.")
                            .linebreak()
                            .appendDefault("To see which objects belong to each of those sets, go to the classification tab!"),
                    listOf("Show classification tab" does { ClientInfo.mainFrame.selectTab(this.parentTab!!.childTabs.last()) },
                           "I don't get those charts!" does { showComponentDescriptions() }),
                    null,
                    this))
    }

    private lateinit var info: PermBornDiedTempInfo

    init {
        FXMLUtil.load(this, PermBornDiedTempOverviewTab::class.java)
    }

    fun init(info: PermBornDiedTempInfo) {
        super.init(info.heapEvolutionInfo.appInfo,
                   SimpleStringProperty("Overview"),
                   SimpleStringProperty(""),
                   SimpleStringProperty("Visualize development of Perm/Born/Died/Temp object groups in various charts"),
                   Consts.CHART_ICON,
                   listOf(),
                   true)
        this.info = info
        objectsChartPane.init()
        bytesChartPane.init(ReducedXYChartPane.Companion.Unit.BYTES)
        ReducedXYChartPane.synchronize(listOf(objectsChartPane, bytesChartPane),
                                       ZOOM desynchable true,
                                       PAN desynchable true,
                                       SELECTION desynchable false)
    }

    fun getHeapEvolutionUpdateListener() = object : HeapEvolutionUpdateListener {
        val permBornDiedTempData: MutableList<PermBornDiedTempData> = mutableListOf()

        override fun gcEnd(heapEvolutionData: HeapEvolutionData) {
            // update charts
            if (heapEvolutionData.currentTime > permBornDiedTempData.lastOrNull()?.time ?: Long.MIN_VALUE) {
                // update all previous perm values
                val permSize = Size(heapEvolutionData.permObjectCount.toDouble(), heapEvolutionData.permByteCount.toDouble())
                permBornDiedTempData.forEach {
                    it.perm = Size(permSize)
                }

                // update all previous born data points and calculate new born size
                var currentBornSize = Size()
                for (i in 0..permBornDiedTempData.size - 1) {
                    val bornAtGCNumberI =
                            if (heapEvolutionData.isGCStart(permBornDiedTempData[i].time)) {
                                val gcStartIndex = i / 2
                                val data = heapEvolutionData.bornPerGCInfo[(gcStartIndex + heapEvolutionData.startGCId).toShort()]!!
                                Size(data.objects.toDouble(), data.bytes.toDouble())
                            } else {
                                Size()
                            }

                    permBornDiedTempData[i].born = Size.add(currentBornSize, bornAtGCNumberI)
                    currentBornSize = permBornDiedTempData[i].born
                }

                // update all previous died data points
                val firstDataPoint = permBornDiedTempData.firstOrNull()
                if (firstDataPoint != null) {
                    val totalDiedSize = Size(heapEvolutionData.diedObjectCount.toDouble(), heapEvolutionData.diedByteCount.toDouble())
                    val diedGrowth = Size.sub(totalDiedSize, firstDataPoint.died)

                    // update first died data point to total died objects
                    firstDataPoint.died = totalDiedSize

                    // increase all other died data points by died growth
                    permBornDiedTempData.drop(1).forEach { it.died = Size.add(it.died, diedGrowth) }
                }

                // update temp data points at last gc start
                val totalTempSize = Size(heapEvolutionData.tempObjectCount.toDouble(), heapEvolutionData.tempByteCount.toDouble())
                val tempGrowth = Size.sub(totalTempSize,
                                          Size(permBornDiedTempData.sumByDouble { it.temp.objects },
                                               permBornDiedTempData.sumByDouble { it.temp.bytes }))
                permBornDiedTempData.lastOrNull()?.temp = tempGrowth

                // create new data point (temp and died at 0 because we're at a gc end)
                val newDataPoint = PermBornDiedTempData("",
                                                        permSize,
                                                        currentBornSize,
                                                        Size(0.0, 0.0),
                                                        Size(0.0, 0.0),
                                                        heapEvolutionData.currentTime)

                // add new data point
                permBornDiedTempData.add(newDataPoint)
                // update charts
                objectsChartPane.plot(permBornDiedTempData)
                bytesChartPane.plot(permBornDiedTempData)
            }
        }

        override fun gcStart(heapEvolutionData: HeapEvolutionData) {
            // update charts
            if (heapEvolutionData.currentTime > permBornDiedTempData.lastOrNull()?.time ?: Long.MIN_VALUE) {
                // updated perm and born
                val permSize = Size(heapEvolutionData.permObjectCount.toDouble(), heapEvolutionData.permByteCount.toDouble())
                val bornSize = Size(heapEvolutionData.bornObjectCount.toDouble(), heapEvolutionData.bornByteCount.toDouble())
                // died and temp at zero
                val diedSize = Size(0.0, 0.0)
                val tempSize = Size(0.0, 0.0)

                // create new data point
                val newDataPoint = PermBornDiedTempData("", permSize, bornSize, diedSize, tempSize, heapEvolutionData.currentTime)

                // update all previous perm values
                permBornDiedTempData.forEach {
                    it.perm = Size(permSize)
                }

                // add new data point
                permBornDiedTempData.add(newDataPoint)
                // update charts
                objectsChartPane.plot(permBornDiedTempData)
                bytesChartPane.plot(permBornDiedTempData)
            }
        }
    }

    override fun cleanupOnClose() {

    }

    override fun appInfoChangeAction(type: IAppInfo.ChangeType) {

    }
}
