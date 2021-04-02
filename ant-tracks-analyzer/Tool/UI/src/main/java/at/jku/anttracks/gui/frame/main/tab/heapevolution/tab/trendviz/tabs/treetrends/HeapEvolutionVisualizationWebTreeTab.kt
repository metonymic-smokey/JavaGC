package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.treetrends

import at.jku.anttracks.gui.classification.component.selectionpane.ClassificationSelectionPane
import at.jku.anttracks.gui.classification.filter.OnlyDataStructureHeadsFilter
import at.jku.anttracks.gui.component.actiontab.ActionTabAction
import at.jku.anttracks.gui.frame.main.component.applicationbase.WebSocketEnabledTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.model.HeapEvolutionVisualizationInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.treetrends.web.HeapEvolutionVisualizationWebTreeBridge
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.component.configurationpane.SelectedClassifiersConfigurationPane
import at.jku.anttracks.gui.model.ClientInfo
import at.jku.anttracks.gui.model.Description
import at.jku.anttracks.gui.model.IAppInfo
import at.jku.anttracks.gui.model.Idea
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.sun.javafx.webkit.WebConsoleListener
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.concurrent.Worker
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.web.WebView
import org.controlsfx.control.PopOver

class HeapEvolutionVisualizationWebTreeTab : WebSocketEnabledTab() {
    @FXML
    lateinit var selectedClassifiersPane: SelectedClassifiersConfigurationPane

    @FXML
    lateinit var webView: WebView

    private lateinit var heapEvolutionVisualizationInfo: HeapEvolutionVisualizationInfo
    private val loaded = SimpleBooleanProperty(false)
    val listener: SimpleObjectProperty<() -> Unit> = SimpleObjectProperty {
        if (ClientInfo.mainFrame.mainTabbedPane.activeTab == this) {
            webView.engine.reload()
        }
    }

    override val webSocketHandlers: List<WebSocketCapabilityHandler> by lazy {
        listOf(
                WebSocketCapabilityHandler(WebSocketCapability.GET_ALL_TREES) {
                    heapEvolutionVisualizationInfo.groupings
                            .map { (time, root) -> root.asAntTracksJSON(null, heapEvolutionVisualizationInfo.selectedClassifiers, time) }
                            .fold(JsonArray()) { array: JsonArray, tree: JsonElement -> array.apply { add(tree) } }

                })
    }

    override val componentDescriptions by lazy {
        listOf<Triple<Node, Description, PopOver.ArrowLocation?>>()
    }

    override val initialTabIdeas by lazy {
        listOf<Idea>()
    }

    init {
        FXMLUtil.load(this, HeapEvolutionVisualizationWebTreeTab::class.java)
    }

    fun init(info: HeapEvolutionVisualizationInfo) {
        super.init(info.appInfo,
                   SimpleStringProperty("Tree trend visualization"),
                   SimpleStringProperty("Tree visualizations (sunburst, icicle, tree map, stacked bar chart) based on the used classification"),
                   SimpleStringProperty("Visualizes the evolution of the heap over time. The objects are grouped according to the selected classifiers.\n" +
                                                "You can drill-down into object groups by clicking on the visualization elements."),
                   Consts.HEAP_TREND_ICON,
                   listOf(
                           ActionTabAction(
                                   "Reload page",
                                   "If you experience any visualization / resize problems, try to reload the page.",
                                   "",
                                   loaded,
                                   null
                           ) {
                               webView.engine.reload()
                           }
                   ),
                   false)
        this.heapEvolutionVisualizationInfo = info

        ClientInfo.mainFrame.mainTabbedPane.selectedTabListeners.add(listener.get())

        initializeConfigurationPane()
    }

    private fun initializeConfigurationPane() {
        selectedClassifiersPane.init(heapEvolutionVisualizationInfo.appInfo,
                                     heapEvolutionVisualizationInfo,
                                     ClassificationSelectionPane.ClassificationSelectionListener.NO_OP_CLASSIFIER_LISTENER,
                                     ClassificationSelectionPane.ClassificationSelectionListener.NOOP_FILTER_LISTENER)
        selectedClassifiersPane.filterSelectionPane.resetSelected(heapEvolutionVisualizationInfo.selectedFilters.filter { it.name != OnlyDataStructureHeadsFilter.NAME })
        selectedClassifiersPane.classifierSelectionPane.resetSelected(heapEvolutionVisualizationInfo.selectedClassifiers.list)
        selectedClassifiersPane.switchToAnalysisMode()
        selectedClassifiersPane.dataStructureSwitch.isDisable = true

        // Save some vertical screen space
        selectedClassifiersPane.filterSelectionPane.expandedProperty().set(false)
        selectedClassifiersPane.classifierSelectionPane.expandedProperty().set(false)
    }

    fun notifyDataFinished() {
        val josnTrees =
                heapEvolutionVisualizationInfo.groupings
                        .map { (time, tree) -> tree.asAntTracksJSON(null, heapEvolutionVisualizationInfo.selectedClassifiers, time) }
                        .sortedBy { jsonObject -> (jsonObject as JsonObject)["time"].toString().toLong() }
                        .fold(JsonArray()) { arr, tree -> arr.add(tree); arr }

        val bridge = HeapEvolutionVisualizationWebTreeBridge()

        Platform.runLater {
            val engine = webView.engine
            WebConsoleListener.setDefaultListener { webView, message, lineNumber, sourceId -> println("$message [at line $lineNumber]") }
            engine.loadWorker.stateProperty().addListener { observable, oldValue, newValue ->
                Platform.runLater {
                    if (newValue == Worker.State.SUCCEEDED) {
                        /*
                        https://docs.oracle.com/javase/8/javafx/api/javafx/scene/web/WebEngine.html

                        **Mapping JavaScript values to Java objects**
                        *
                        JavaScript values are represented using the obvious Java classes: null becomes Java null; a boolean becomes a java.lang.Boolean; and a string becomes a java.lang.String. A number can be java.lang.Double or a java.lang.Integer, depending. The undefined value maps to a specific unique String object whose value is "undefined".

                        If the result is a JavaScript object, it is wrapped as an instance of the JSObject class. (As a special case, if the JavaScript object is a JavaRuntimeObject as discussed in the next section, then the original Java object is extracted instead.) The JSObject class is a proxy that provides access to methods and properties of its underlying JavaScript object. The most commonly used JSObject methods are getMember (to read a named property), setMember (to set or define a property), and call (to call a function-valued property).

                        A DOM Node is mapped to an object that both extends JSObject and implements the appropriate DOM interfaces. To get a JSObject object for a Node just do a cast:

                         JSObject jdoc = (JSObject) webEngine.getDocument();


                        In some cases the context provides a specific Java type that guides the conversion. For example if setting a Java String field from a JavaScript expression, then the JavaScript value is converted to a string.
                         */

                        // What is the difference between window, screen, and document in Javascript?
                        // https://stackoverflow.com/questions/9895202/what-is-the-difference-between-window-screen-and-document-in-javascript
                        //val window = engine.executeScript("window") as JSObject
                        //val screen = engine.executeScript("window.screen") as JSObject
                        //val document: Document = engine.executeScript("window.document")
                        //val document: Document = engine.document
                        //val documentAsJSON = document as JSObject

                        // Install bridge between WebView and the application
                        /*
                        https://docs.oracle.com/javase/8/javafx/api/javafx/scene/web/WebEngine.html

                        **Mapping Java objects to JavaScript values**

                        The arguments of the JSObject methods setMember and call pass Java objects to the JavaScript environment.
                        This is roughly the inverse of the JavaScript-to-Java mapping described above: Java String, Number, or Boolean objects are converted to the obvious JavaScript values.
                        A JSObject object is converted to the original wrapped JavaScript object. Otherwise a JavaRuntimeObject is created.
                        This is a JavaScript object that acts as a proxy for the Java object, in that accessing properties of the JavaRuntimeObject causes the Java field or method with the same name to be accessed.
                        */

                        bridge.apply {
                            init(engine)
                            injectIntoEngine()
                            //redirection of console not needed due to WebConsoleListener.setDefaultListener
                            //redirectConsole()
                        }

                        val width = bridge.getWindowWidth()
                        if (width != 0.0) {
                            bridge.initTreeViz(josnTrees.toString())
                            val curListener = listener.get()
                            if (curListener != null) {
                                ClientInfo.mainFrame.mainTabbedPane.selectedTabListeners.remove(curListener)
                                listener.set { null }
                            }
                            loaded.set(true)
                        }
                    }
                }
            }
            webView.engine.load(javaClass.getResource("/treeViz/index.html").toExternalForm())
        }
    }

    override fun cleanupOnClose() {
        // TODO
    }

    override fun appInfoChangeAction(type: IAppInfo.ChangeType) {
        // TODO
    }
}