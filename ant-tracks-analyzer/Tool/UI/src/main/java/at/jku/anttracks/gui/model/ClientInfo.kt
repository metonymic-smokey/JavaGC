
package at.jku.anttracks.gui.model

import at.jku.anttracks.gui.frame.main.MainFrame
import at.jku.anttracks.gui.utils.OperationManager
import com.sun.net.httpserver.HttpServer
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.binder.jvm.*
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.jmx.JmxConfig
import io.micrometer.jmx.JmxMeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import javafx.application.Application
import javafx.stage.Stage
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress

object ClientInfo {
    lateinit var application: Application
    lateinit var mainFrame: MainFrame
    lateinit var stage: Stage

    @JvmField
    var meterRegistry: CompositeMeterRegistry = CompositeMeterRegistry()
    @JvmField
    val jmxMeterRegistry = JmxMeterRegistry(JmxConfig.DEFAULT, Clock.SYSTEM)
    @JvmField
    val prometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    @JvmField
    var isExtendedChartVisibility = false
    @JvmField
    var consistencyCheck: Boolean = false
    @JvmField
    var logSettingsFile: String? = null
    @JvmField
    var printStatisticsPath: String? = null
    @JvmField
    var assertionsEnabled: Boolean = false

    lateinit var operationManager: OperationManager

    private val ideaGaugeMetric = "anttracks.clientinfo.ideas"
    private val testCounterMetric = "anttracks_clientinfo_countertest"
    private val testTag = "testtag"

    @JvmField
    var traceDirectory: String = System.getProperty("user.home")
    @JvmField
    var hprofDirectory: String = System.getProperty("user.home")
    @JvmField
    var featureDirectory: String = System.getProperty("user.home")
    @JvmField
    var databaseDirectory: String = "./.database"
    @JvmField
    var jsonExportDirectory: String = System.getProperty("user.home")

    init {
        meterRegistry.add(jmxMeterRegistry)
        meterRegistry.add(prometheusMeterRegistry)

        // https://static.javadoc.io/io.micrometer/micrometer-core/1.1.0/io/micrometer/core/instrument/binder/MeterBinder.html?is-external=true
        ClassLoaderMetrics().bindTo(meterRegistry)
        DiskSpaceMetrics(File(".")).bindTo(meterRegistry)
        //ExecutorServiceMetrics().bindTo(meterRegistry)
        FileDescriptorMetrics().bindTo(meterRegistry)
        JvmGcMetrics().bindTo(meterRegistry)
        JvmMemoryMetrics().bindTo(meterRegistry)
        JvmThreadMetrics().bindTo(meterRegistry)
        ProcessorMetrics().bindTo(meterRegistry)
        UptimeMetrics().bindTo(meterRegistry)

        Metrics.addRegistry(meterRegistry)

        try {
            val server = HttpServer.create(InetSocketAddress(9091), 0)
            server.createContext("/metrics") { httpExchange ->
                val response = prometheusMeterRegistry.scrape()
                httpExchange.sendResponseHeaders(200, response.length.toLong())
                val os = httpExchange.responseBody
                os.write(response.toByteArray())
                os.close()
            }

            Thread(Runnable { server.start() }).run()
        } catch (e: IOException) {
            System.err.println("Error starting HTTP Server to host MicroMeter metrics (e.g., to display in Grafana), most probably because port 9091 is already in use (is another instance of " +
                                       "AntTracks already running?)!")
            e.printStackTrace()
        }

        // init gauge
//        Metrics.gauge(ideaGaugeMetric, ideas) { ideaMap -> ideaMap.flatMap { it.value }.size.toDouble() }

        Metrics.counter(testCounterMetric, testTag, "tag1").increment()
        Metrics.counter(testCounterMetric, testTag, "tag1").increment()
        Metrics.counter(testCounterMetric, testTag, "tag2").increment()

        /*
        graphDB = GraphDatabaseFactory().newEmbeddedDatabase(File(databaseDirectory))
        addShutdownHook { graphDB.shutdown() }
        */
    }

    @JvmStatic
    fun init(application: Application,
             mainFrame: MainFrame,
             stage: Stage,
             operationManager: OperationManager) {
        this.application = application
        this.mainFrame = mainFrame
        this.stage = stage
        this.operationManager = operationManager
    }

    @JvmStatic
    fun getCurrentAppInfo() = mainFrame.appInfo
}


