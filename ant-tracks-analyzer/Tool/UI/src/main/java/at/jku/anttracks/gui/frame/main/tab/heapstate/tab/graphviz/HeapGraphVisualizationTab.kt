package at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz

import at.jku.anttracks.gui.component.actiontab.ActionTabAction
import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.extensions.containedObjects
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.extensions.getPathsToMostInterestingRootsTyped
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.nodes.*
import at.jku.anttracks.gui.model.*
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.roots.RootPtr
import at.jku.anttracks.heap.roots.StaticFieldRoot
import at.jku.anttracks.util.toBitSet
import at.jku.anttracks.util.toString
import com.sun.javafx.webkit.WebConsoleListener
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.concurrent.Worker
import javafx.fxml.FXML
import javafx.scene.control.TextArea
import javafx.scene.web.WebView
import java.io.OutputStreamWriter
import java.util.stream.Collectors
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class HeapGraphVisualizationTab : ApplicationBaseTab() {
    @FXML
    lateinit var webView: WebView

    @FXML
    lateinit var textArea: TextArea

    var bridge: GraphVisBridge? = null
    lateinit var heap: IndexBasedHeap

    var initAction: String? = null

    private lateinit var initialObjectGroup: IntArray

    init {
        FXMLUtil.load(this, HeapGraphVisualizationTab::class.java)
    }

    fun init(appInfo: AppInfo, heap: IndexBasedHeap, objectGroup: IntArray, initAction: String? = null) {
        super.init(appInfo,
                   SimpleStringProperty("Graph Visualization"),
                   SimpleStringProperty(""),
                   SimpleStringProperty("Visualizes the heap as a graph.\nClicking graph nodes opens a menu to interact with the graph."),
                   Consts.GRAPH_ICON,
                   listOf(
                           ActionTabAction("Print HTML", "Print HTML", "Debug", SimpleBooleanProperty(true), null
                           ) {
                               val transformer = TransformerFactory.newInstance().newTransformer()
                               transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
                               transformer.setOutputProperty(OutputKeys.METHOD, "xml")
                               transformer.setOutputProperty(OutputKeys.INDENT, "yes")
                               transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                               transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")

                               transformer.transform(DOMSource(webView.engine.document),
                                                     StreamResult(OutputStreamWriter(System.out, "UTF-8")))
                           },
                           ActionTabAction("Reset", "Reset graph visualization to initial state", "Reset", SimpleBooleanProperty(true), null) { webView.engine.reload() }
                   ),
                   true)
        this.heap = heap
        this.initialObjectGroup = objectGroup
        this.initAction = initAction

        // reload page, when tab is selected the first time
        selected.addListener { _, _, newValue ->
            run {
                if (newValue && bridge == null) {
                    webView.engine.reload()
                }
            }
        }

        // initGraph, when page is loaded and tab is selected
        webView.engine.loadWorker.stateProperty().addListener { _, _, newValue ->
            run {
                if (newValue == Worker.State.SUCCEEDED && selected.value) {
                    bridge = initGraph()
                }
            }
        }
        WebConsoleListener.setDefaultListener { _, message, lineNumber, _ -> println("$message[at $lineNumber]") }

        textArea.isEditable = false

        // load page
        webView.isContextMenuEnabled = false
        webView.engine.load(this::class.java.getResource("page/index.html").toExternalForm())
    }

    private fun initGraph(): GraphVisBridge {
        fun objectToString(id: Int) = with(heap.getObjectInfo(id)) {
            "type: ${type}\ncallSites:${allocationSite.callSites.joinToString(separator = " <- ") { it.shortest }}\n"
        }

        val bridge = GraphVisBridge(webView.engine, heap)

        ideas.clear()

        bridge.setOnMouseEntered { node ->
            run {
                textArea.text = when (node) {
                    is GraphVisObjectNode -> objectToString(node.objectId)
                    is GraphVisObjectGroupNode -> {
                        val str = node.containedNodes.stream().limit(100).mapToObj(::objectToString).collect(Collectors.joining("\n-----\n"))
                        str + if (node.containedNodes.cardinality() > 100) " \n..." else ""
                    }
                    is GraphVisRootNode -> node.rootInfo.toGraphString()
                    is GraphVisRootGroupNode -> {
                        val str = node.rootInfos.sortedBy { it.rootType.byteVal }.take(100).joinToString(separator = "\n-----\n") { it.toGraphString() }
                        str + if (node.rootInfos.size > 100) "\n\n..." else ""
                    }
                    else -> ""
                }
            }
        }

        bridge.setOnMouseLeave { _ ->
            // Do not clear text on hover end
            /*
            run {
                textArea.text = ""
            }
             */
        }

        val update = bridge.createUpdate()
        if (initialObjectGroup.isEmpty()) {
            update.addNodes(GraphVisDefaultNode(bridge))
            update.execute()
        } else {
            val bitset = initialObjectGroup.toBitSet()
            // TODO This may confuse users if they select e.g. 1000 objects but only 890 are shown on the screen
            // bitset.and(heap.getIndirectlyReachableObjectsByTypes(*INTERESTING_ROOT_TYPES))
            val objectGroup = GraphVisObjectGroupNode(bitset, bridge)
            when (this.initAction) {
                PATH_TO_MOST_INTERESTING_ROOTS_TYPED -> {
                    update.addNodes(objectGroup)
                    update.addNodes(bridge.heap.getPathsToMostInterestingRootsTyped(bitset).map { GraphVisObjectGroupNode(it, bridge) })
                    update.labelsBasedOnExecutingNodeNode = true
                }
                else -> {
                }
            }
            update.addNodes(objectGroup)
            update.execute(objectGroup.id)
            bridge.recalculateAndThenRepaintLinkWeights(objectGroup.getUniqueIdentifier(), true)

            when (this.initAction) {
                PATH_TO_MOST_INTERESTING_ROOTS_TYPED -> {
                    var curLinkToVerbalize = bridge.getLinks().find { it.target == objectGroup }
                    val baseGroupType = heap.getType(curLinkToVerbalize!!.target.containedObjects().nextSetBit(0)).simpleName

                    val description = Description()
                    var first = true
                    while (curLinkToVerbalize != null) {
                        when (curLinkToVerbalize.source) {
                            is GraphVisObjectGroupNode -> {
                                if (!first) {
                                    description.a("These ")
                                }

                                val toAmount = curLinkToVerbalize.target.containedObjects().cardinality().toString("%,d")
                                val toType = heap.getType(curLinkToVerbalize.target.containedObjects().nextSetBit(0)).simpleName

                                val fromAmount = curLinkToVerbalize.source.containedObjects().cardinality().toString("%,d")
                                val fromType = heap.getType(curLinkToVerbalize.source.containedObjects().nextSetBit(0)).simpleName
                                description.e(toAmount).a(" ").c(toType).a(" objects are kept alive by ").e(fromAmount).a(" ").c(fromType).a(" objects.").ln()
                            }
                            is GraphVisRootNode -> {
                                val toAmount = curLinkToVerbalize.target.containedObjects().cardinality().toString("%,d")
                                val toType = heap.getType(curLinkToVerbalize.target.containedObjects().nextSetBit(0)).simpleName

                                val root = (curLinkToVerbalize.source as GraphVisRootNode).rootInfo
                                val rootText = when (root.rootType) {
                                    RootPtr.RootType.STATIC_FIELD_ROOT -> {
                                        val staticRoot = root as StaticFieldRoot
                                        Description("static field called ").c(staticRoot.fieldName()).a(" in the class ").c(staticRoot.clazz()).a(".")
                                    }
                                    else -> Description("GC root (hover GC root node for more information).")
                                }

                                description.a("Finally, these ").e(toAmount).a(" ").c(toType).a(" objects are kept alive by a single ").concat(rootText).ln()
                            }
                            is GraphVisRootGroupNode -> {
                                val toAmount = curLinkToVerbalize.target.containedObjects().cardinality().toString("%,d")
                                val toType = heap.getType(curLinkToVerbalize.target.containedObjects().nextSetBit(0)).simpleName

                                val roots = (curLinkToVerbalize.source as GraphVisRootGroupNode).rootInfos

                                val rootText = when (roots.first().rootType) {
                                    RootPtr.RootType.STATIC_FIELD_ROOT -> {
                                        val d = Description().e(roots.size.toString()).a(" static fields. These static fields are")
                                        roots.filterIsInstance(StaticFieldRoot::class.java).forEachIndexed { i, rp ->
                                            if (i > 0) {
                                                d.a(" and ")
                                            }
                                            d.a(" the field ").c(rp.fieldName()).a(" in the class ").c(rp.clazz())
                                        }
                                        d.a(" (hover \"${curLinkToVerbalize.source.getLabel()}\" node for more information).")

                                    }
                                    else -> Description().e(roots.size.toString()).a(" GC roots (hover GC root node for more information).")
                                }

                                description.a("Finally, these ").e(toAmount).a(" ").c(toType).a(" objects are kept alive by ").concat(rootText).ln()
                            }
                        }

                        curLinkToVerbalize = bridge.getLinks().filter { it.target == curLinkToVerbalize!!.source }.sortedByDescending { it.weight }.firstOrNull()
                        first = false
                    }

                    val pathToMostInterestingGCRootsIdea = Idea(
                            "Path to most interesting GC root(s)",
                            description
                                    .ln()
                                    .a("Please switch to your IDE now and inspect the code.")
                                    .ln()
                                    .ln()
                                    .a("To reduce the number of ")
                                    .c(baseGroupType)
                                    .a(" objects (i.e., to enable the GC to collect them), you have to cut the reference path to these objects somewhere.")
                                    .a(" You can achieve this by setting references to ")
                                    .c("null")
                                    .a(" or by")
                                    .e(" removing objects from their containing data structures.")
                                    .a(" Also check why the ")
                                    .c(baseGroupType)
                                    .a(" objects are added to those data structures in the first place. Are they contained in the mentioned data structures on purpose?"),
                            listOf(),
                            listOf(webView at Idea.BulbPosition.TOP_RIGHT),
                            this,
                            { } revertVia { })
                    ideas.add(pathToMostInterestingGCRootsIdea)
                }
                else -> {
                }
            }
        }

        return bridge
    }

    override fun cleanupOnClose() {
    }

    override fun appInfoChangeAction(type: IAppInfo.ChangeType) {
    }
}