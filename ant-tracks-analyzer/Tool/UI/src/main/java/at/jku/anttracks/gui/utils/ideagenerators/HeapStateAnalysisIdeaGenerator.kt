package at.jku.anttracks.gui.utils.ideagenerators

import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.nodes.GroupingNode
import at.jku.anttracks.gui.classification.DEFAULT_CLASSIFIERS
import at.jku.anttracks.gui.classification.classifier.*
import at.jku.anttracks.gui.classification.filter.OnlyDataStructureHeadsFilter
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.HeapStateClassificationTab
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.component.classificationtreetableview.ClassificationTreeTableView
import at.jku.anttracks.gui.model.*
import at.jku.anttracks.gui.utils.ideagenerators.IdeaGeneratorUtil.performLocalClassification
import at.jku.anttracks.util.ThreadUtil
import at.jku.anttracks.util.toString
import javafx.application.Platform
import javafx.beans.binding.Bindings
import java.util.concurrent.Callable

object HeapStateAnalysisIdeaGenerator {

    private fun highlightTreeTableEntry(nodeToHighlight: GroupingNode, treeTableView: ClassificationTreeTableView, ensureRetainedSizeSorting: Boolean) {
        // get updated item for the given grouping node
        val treeItemToHighlight = treeTableView.getTreeItem(nodeToHighlight)

        // highlight the item
        treeTableView.expandRecursivelyUpTo(treeItemToHighlight)
        if (ensureRetainedSizeSorting) {
            treeTableView.sortByRetainedSize()
        }

        ThreadUtil.runDeferred({
                                   Platform.runLater {
                                       if (!treeTableView.isItemInView(treeItemToHighlight)) {
                                           treeTableView.scrollTo(treeItemToHighlight)
                                       }
                                       treeTableView.selectionModel.clearSelection()
                                       treeTableView.selectionModel.select(treeItemToHighlight)
                                   }
                               }, ThreadUtil.DeferredPeriod.LONG)
    }

    @JvmStatic
    fun analyzeDefaultClassifiedTree(heapStatisticsTab: HeapStateClassificationTab) {
        val treeTableView = heapStatisticsTab.treeTableView
        val classifiers = treeTableView.classificationTree.classifiers
        val filters = treeTableView.classificationTree.filters
        val detailsInfo = heapStatisticsTab.statisticsInfo.heapStateInfo

        if (classifiers.list.map { it::class } == DEFAULT_CLASSIFIERS && !filters.contains(detailsInfo.availableFilter[OnlyDataStructureHeadsFilter::class])) {
            // use 'filtered' node as root in case any other filters have been applied
            val root = if (treeTableView.root.children.firstOrNull()?.value?.fullKey?.equals("Filtered") ?: false) {
                treeTableView.root.children.first()
            } else {
                treeTableView.root
            }

            // decide whether to perform bottom-up or top-down analysis
            val typeWithBiggestShallowSize = root.children.maxBy { it.value.getByteCount(detailsInfo.fastHeapSupplier.get()) }?.value
            val typeShallowSizePortion = (typeWithBiggestShallowSize?.getByteCount(detailsInfo.fastHeapSupplier.get())
                    ?: 0) / detailsInfo.fastHeapSupplier.get()!!.byteCount.toDouble()

            if (typeShallowSizePortion >= 0.15) {
                // objects of a single type make up at least 15% of the heap => start bottom up analysis from here
                bottomUpAnalysis(typeWithBiggestShallowSize!!, typeShallowSizePortion, heapStatisticsTab)

            } else {
                // try top down analysis
                heapStatisticsTab.ideas.add(Idea("Try data structures",
                                                 Description("The heap composition seems normal. ")
                                                         .linebreak()
                                                         .appendDefault("Maybe the data structure view of this heapstate will offer any starting points for further analysis."),
                                                 listOf("Switch to data structure view" does { heapStatisticsTab.switchToDataStructureView() }),
                                                 listOf(heapStatisticsTab.configurationPane.dataStructureSwitch at Idea.BulbPosition.TOP_RIGHT),
                                                 heapStatisticsTab))
            }
        }
    }

    @JvmStatic
    fun topDownAnalysis(heapStatisticsTab: HeapStateClassificationTab) {
        val treeTableView = heapStatisticsTab.treeTableView
        val detailsInfo = heapStatisticsTab.statisticsInfo.heapStateInfo
        val heapByteCount = detailsInfo.fastHeapSupplier.get()!!.byteCount
        val dataStructureLeavesClassifierChain = ClassifierChain(detailsInfo.availableClassifier[DataStructureLeavesTransformer::class].also {
            (it as DataStructureLeavesTransformer).setClassifiers(ClassifierChain(detailsInfo.availableClassifier[TypeClassifier::class],
                                                                                  detailsInfo.availableClassifier[AllocationSiteClassifier::class],
                                                                                  detailsInfo.availableClassifier[CallSitesClassifier::class]))
        })
        val gcRootsClassifierChain = ClassifierChain(detailsInfo.availableClassifier[ClosestGCRootsClassifier::class])

        // top down analysis: find the data structure type/allocsite that keeps the most memory alive, then go down to the leaves (or check out roots if user wants to)
        val topDSTypeNode = treeTableView.root.children.firstOrNull()?.children?.maxBy { it.value.retainedSizeProperty().get() }?.value
        val topDSTypeNodeRetainedSizePortion = (topDSTypeNode?.retainedSizeProperty()?.get() ?: 0) / heapByteCount.toDouble()

        if (topDSTypeNodeRetainedSizePortion >= 0.15) {
            // there is a data structure type that keeps at least 15% of the heap alive
            val topDSAllocSiteNode = treeTableView.getTreeItem(topDSTypeNode)!!.children.maxBy { it.value.retainedSizeProperty().get() }?.value
            val topDSAllocSiteNodeRetainedSizePortion = (topDSAllocSiteNode?.retainedSizeProperty()?.get() ?: 0) / heapByteCount.toDouble()

            if (topDSAllocSiteNodeRetainedSizePortion >= 0.1) {
                // there is a data structure alloc site that keeps at least 10% of the heap alive
                val isConstructorAllocSite = topDSAllocSiteNode!!.key.toString().contains("<init>")
                val clazz = topDSAllocSiteNode.key.toString().substringBeforeLast("::")
                val method = topDSAllocSiteNode.key.toString().substringBeforeLast("(")

                heapStatisticsTab.ideas.add(Idea("Large data structures!",
                                                 Description("The retained size describes ")
                                                         .appendEmphasized("object ownership")
                                                         .appendDefault(", that means that objects / object groups with a high retained size keep alive a lot of other objects.")
                                                         .linebreak()
                                                         .appendDefault("Over ")
                                                         .appendEmphasized("${(topDSAllocSiteNodeRetainedSizePortion * 100).toInt()}% ")
                                                         .appendDefault("of this heap is kept alive by ")
                                                         .appendEmphasized("${topDSAllocSiteNode.objectCount.toString("%,d")} ")
                                                         .appendDefault("data structures of type ")
                                                         .appendCode(topDSTypeNode!!.key.toString().substringAfterLast("."))
                                                         .appendDefault(" that have been allocated in ")
                                                         .appendDefault(if (isConstructorAllocSite) "the constructor of class " else " the method ")
                                                         .appendCode(if (isConstructorAllocSite) clazz else method)
                                                         .appendDefault(". ")
                                                         .linebreak()
                                                         .appendDefault("Inspect the ")
                                                         .appendEmphasized("GC roots ")
                                                         .appendDefault("to see why these data structures are kept alive. The ")
                                                         .appendEmphasized("data structures leaves ")
                                                         .appendDefault("on the other hand will tell you why these data structures consume so much memory."),
                                                 listOf("Inspect GC roots" does {
                                                     performLocalClassification(topDSAllocSiteNode,
                                                                                gcRootsClassifierChain,
                                                                                treeTableView,
                                                                                heapStatisticsTab) {
                                                         highlightTreeTableEntry(topDSAllocSiteNode, treeTableView, true)
                                                         treeTableView.expandRecursivelyFrom(topDSAllocSiteNode, true)
                                                         analyzeRootPointers(topDSAllocSiteNode, heapStatisticsTab)
                                                     }
                                                 },
                                                        "Inspect data structure leaves" does {
                                                            performLocalClassification(topDSAllocSiteNode,
                                                                                       dataStructureLeavesClassifierChain,
                                                                                       treeTableView,
                                                                                       heapStatisticsTab) {
                                                                highlightTreeTableEntry(topDSAllocSiteNode, treeTableView, true)
                                                                treeTableView.expandRecursivelyFrom(topDSAllocSiteNode, true)
                                                                analyzeDataStructureLeaves(topDSAllocSiteNode, heapStatisticsTab)
                                                            }
                                                        }
                                                 ),
                                                 null,
                                                 heapStatisticsTab,
                                                 { highlightTreeTableEntry(topDSAllocSiteNode, treeTableView, true) } revertVia { treeTableView.selectionModel.clearSelection() },
                                                 Bindings.createBooleanBinding(Callable {
                                                     !treeTableView.root.value.hasDeepChild(topDSAllocSiteNode)
                                                 }, *treeTableView.getRecursiveParentTreeItems(topDSAllocSiteNode).map { it.children }.toTypedArray())))

            } else {
                // there is no large alloc site, start analysis from type node instead
                heapStatisticsTab.ideas.add(Idea("Large data structures!",
                                                 Description("Over ")
                                                         .appendEmphasized("${(topDSTypeNodeRetainedSizePortion * 100).toInt()}% ")
                                                         .appendDefault("of this heap is kept alive by ")
                                                         .appendEmphasized("${topDSTypeNode!!.objectCount.toString("%,d")} ")
                                                         .appendDefault("data structures of type ")
                                                         .appendCode(topDSTypeNode.key.toString().substringAfterLast("."))
                                                         .appendDefault(". ")
                                                         .linebreak()
                                                         .appendDefault("Inspect the ")
                                                         .appendEmphasized("GC roots ")
                                                         .appendDefault("to see why these data structures are kept alive. The ")
                                                         .appendEmphasized("data structures leaves ")
                                                         .appendDefault("on the other hand will tell you why these data structures consume so much memory."),
                                                 listOf("Inspect GC roots" does {
                                                     performLocalClassification(topDSTypeNode, gcRootsClassifierChain, treeTableView, heapStatisticsTab) {
                                                         highlightTreeTableEntry(topDSTypeNode, treeTableView, true)
                                                         treeTableView.expandRecursivelyFrom(topDSTypeNode, true)
                                                         analyzeRootPointers(topDSTypeNode, heapStatisticsTab)
                                                     }
                                                 },
                                                        "Inspect data structure leaves" does {
                                                            performLocalClassification(topDSTypeNode, dataStructureLeavesClassifierChain, treeTableView, heapStatisticsTab) {
                                                                highlightTreeTableEntry(topDSTypeNode, treeTableView, true)
                                                                treeTableView.expandRecursivelyFrom(topDSTypeNode, true)
                                                                analyzeDataStructureLeaves(topDSTypeNode, heapStatisticsTab)
                                                            }
                                                        }
                                                 ),
                                                 null,
                                                 heapStatisticsTab,
                                                 { highlightTreeTableEntry(topDSTypeNode, treeTableView, true) } revertVia { treeTableView.selectionModel.clearSelection() },
                                                 Bindings.createBooleanBinding(Callable {
                                                     !treeTableView.root.value.hasDeepChild(topDSTypeNode)
                                                 }, *treeTableView.getRecursiveParentTreeItems(topDSTypeNode).map { it.children }.toTypedArray())))
            }
        }
    }

    private fun analyzeDataStructureLeaves(inspectedDataStructureNode: GroupingNode, heapStatisticsTab: HeapStateClassificationTab) {
        val treeTableView = heapStatisticsTab.treeTableView
        val heapByteCount = heapStatisticsTab.statisticsInfo.heapStateInfo.fastHeapSupplier.get()!!.byteCount

        val topLeafTypeNode = inspectedDataStructureNode.children.firstOrNull()?.children?.maxBy { it.retainedSizeProperty().get() }
        val topLeafTypeNodeRetainedSizePortion = (topLeafTypeNode?.retainedSizeProperty()?.get() ?: 0) / heapByteCount.toDouble()

        if (topLeafTypeNodeRetainedSizePortion >= 0.1) {
            heapStatisticsTab.ideas.add(Idea("Interesting data structure leaves",
                                             Description("These data structures together store ")
                                                     .appendEmphasized("${topLeafTypeNode!!.objectCount.toString("%,d")} ")
                                                     .appendDefault("objects of type ")
                                                     .appendCode(topLeafTypeNode.key.toString().substringAfterLast("."))
                                                     .appendDefault(" which keep over ")
                                                     .appendEmphasized("${(topLeafTypeNodeRetainedSizePortion * 100).toInt()}% ")
                                                     .appendDefault("of the heap alive! ")
                                                     .linebreak()
                                                     .appendDefault("Expand the table item to see allocation and call sites and thus find out out where and why these objects have been allocated in your code."),
                                             listOf("Expand allocation & call sites" does {
                                                 highlightTreeTableEntry(topLeafTypeNode, treeTableView, true)
                                                 treeTableView.expandRecursivelyFrom(topLeafTypeNode, true)
                                             }),
                                             null,
                                             heapStatisticsTab,
                                             { highlightTreeTableEntry(topLeafTypeNode, treeTableView, true) } revertVia { treeTableView.selectionModel.clearSelection() },
                                             Bindings.createBooleanBinding(Callable {
                                                 !treeTableView.root.value.hasDeepChild(topLeafTypeNode)
                                             }, *treeTableView.getRecursiveParentTreeItems(topLeafTypeNode).map { it.children }.toTypedArray())))
        }
    }

    private fun bottomUpAnalysis(topTypeNode: GroupingNode, topTypeNodeShallowSizePortion: Double, heapStatisticsTab: HeapStateClassificationTab) {
        val treeTableView = heapStatisticsTab.treeTableView
        val detailsInfo = heapStatisticsTab.statisticsInfo.heapStateInfo
        val heap = detailsInfo.fastHeapSupplier.get()
        val containingDataStructuresClassifierChain = ClassifierChain(detailsInfo.availableClassifier[ContainingDataStructuresTransformer::class].also {
            (it as ContainingDataStructuresTransformer).classifiers = ClassifierChain(detailsInfo.availableClassifier[TypeClassifier::class],
                                                                                      detailsInfo.availableClassifier[AllocationSiteClassifier::class])
            it.considerIndirectlyContainedObjects = true
        })

        // check if there is an allocation site that still covers a large part of the heap
        val topAllocSiteNode = topTypeNode.children.maxBy { it.getByteCount(heap) }
        val topAllocSiteNodeShallowSizePortion = (topAllocSiteNode?.getByteCount(heap) ?: 0) / heap!!.byteCount.toDouble()

        if (topAllocSiteNodeShallowSizePortion >= 0.15) {
            // there is an alloc site that still covers at least 15% of the total heap size => start bottom up analysis from here
            heapStatisticsTab.ideas.add(Idea("Suspicious heap composition",
                                             Description("Over ")
                                                     .appendEmphasized("${(topAllocSiteNodeShallowSizePortion * 100.0).toInt()}% ")
                                                     .appendDefault("of this heap is occupied by objects of type ")
                                                     .appendCode(topTypeNode.key.toString().substringAfterLast("."))
                                                     .appendDefault(" allocated in ")
                                                     .appendCode(topAllocSiteNode!!.key.toString().substringBeforeLast("("))
                                                     .appendDefault(". ")
                                                     .linebreak()
                                                     .appendDefault("Memory leaks are often caused by indefinitely growing data structures!"),
                                             listOf("Inspect data structures" does {
                                                 performLocalClassification(topAllocSiteNode, containingDataStructuresClassifierChain, treeTableView, heapStatisticsTab) {
                                                     highlightTreeTableEntry(topAllocSiteNode, treeTableView, true)
                                                     treeTableView.expandRecursivelyFrom(topAllocSiteNode, true)
                                                     analyzeContainingDataStructures(topAllocSiteNode, heapStatisticsTab)
                                                 }
                                             }),
                                             null,
                                             heapStatisticsTab,
                                             { highlightTreeTableEntry(topAllocSiteNode, treeTableView, false) } revertVia { treeTableView.selectionModel.clearSelection() },
                                             Bindings.createBooleanBinding(Callable {
                                                 !treeTableView.root.value.hasDeepChild(topAllocSiteNode)
                                             }, *treeTableView.getRecursiveParentTreeItems(topAllocSiteNode).map { it.children }.toTypedArray())))

        } else {
            // there is no fitting allocation site...start analysis from the type node instead
            heapStatisticsTab.ideas.add(Idea("Suspicious heap composition",
                                             Description("Over ")
                                                     .appendEmphasized("${(topTypeNodeShallowSizePortion * 100.0).toInt()}% ")
                                                     .appendDefault("of the heap memory is occupied by objects of type ")
                                                     .appendCode(topTypeNode.key.toString().substringAfterLast("."))
                                                     .appendDefault(". ")
                                                     .linebreak()
                                                     .appendDefault("Memory leaks are often caused by indefinitely growing data structures!"),
                                             listOf("Inspect data structures" does {
                                                 performLocalClassification(topTypeNode, containingDataStructuresClassifierChain, treeTableView, heapStatisticsTab) {
                                                     highlightTreeTableEntry(topTypeNode, treeTableView, true)
                                                     treeTableView.expandRecursivelyFrom(topTypeNode, true)
                                                     analyzeContainingDataStructures(topTypeNode,
                                                                                     heapStatisticsTab)
                                                 }
                                             }),
                                             null,
                                             heapStatisticsTab,
                                             { highlightTreeTableEntry(topTypeNode, treeTableView, false) } revertVia { treeTableView.selectionModel.clearSelection() },
                                             Bindings.createBooleanBinding(Callable {
                                                 !treeTableView.root.value.hasDeepChild(topTypeNode)
                                             }, *treeTableView.getRecursiveParentTreeItems(topTypeNode).map { it.children }.toTypedArray())))
        }

    }

    private fun analyzeContainingDataStructures(dsContainedNode: GroupingNode, heapStatisticsTab: HeapStateClassificationTab) {
        val treeTableView = heapStatisticsTab.treeTableView
        val gcRootsClassifierChain = ClassifierChain(heapStatisticsTab.statisticsInfo.heapStateInfo.availableClassifier[ClosestGCRootsClassifier::class])
        val heapByteCount = heapStatisticsTab.statisticsInfo.heapStateInfo.fastHeapSupplier.get()!!.byteCount

        // check if certain data structures 'own' a lot of memory
        val topDSTypeNode = dsContainedNode.children.firstOrNull()?.children?.maxBy { it.retainedSizeProperty().get() }
        val topDSTypeNodeRetainedSizePortion = (topDSTypeNode?.retainedSizeProperty()?.get() ?: 0) / heapByteCount.toDouble()

        if (topDSTypeNodeRetainedSizePortion >= 0.15) {
            // try to further reduce the interesting data structures by checking out allocation sites
            val topDSAllocSiteNode = topDSTypeNode!!.children.maxBy { it.retainedSizeProperty().get() }
            val topDSAllocSiteNodeRetainedSizePortion = (topDSAllocSiteNode?.retainedSizeProperty()?.get() ?: 0) / heapByteCount.toDouble()

            if (topDSAllocSiteNodeRetainedSizePortion >= 0.10) {
                // there is an allocation whose retained size covers at least 10% of the total heap size
                // suggest to further look at the roots of these data structures
                heapStatisticsTab.ideas.add(Idea("Interesting data structures",
                                                 Description("Over ")
                                                         .appendEmphasized("${(topDSAllocSiteNodeRetainedSizePortion * 100.0).toInt()}% ")
                                                         .appendDefault("of this heap is kept alive by ")
                                                         .appendEmphasized("${topDSAllocSiteNode!!.objectCount.toString("%,d")} ")
                                                         .appendDefault("data structures of type ")
                                                         .appendCode(topDSTypeNode.key.toString().substringAfterLast("."))
                                                         .appendDefault(" allocated in ")
                                                         .appendCode(topDSAllocSiteNode.key.toString().substringBeforeLast("("))
                                                         .appendDefault(". ")
                                                         .linebreak()
                                                         .appendDefault("These data structures are alive because they are directly or indirectly referenced by some GC roots."),
                                                 listOf("Inspect GC roots" does {
                                                     performLocalClassification(topDSAllocSiteNode, gcRootsClassifierChain, treeTableView, heapStatisticsTab) {
                                                         highlightTreeTableEntry(topDSAllocSiteNode, treeTableView, true)
                                                         treeTableView.expandRecursivelyFrom(topDSAllocSiteNode, true)
                                                         analyzeRootPointers(topDSAllocSiteNode, heapStatisticsTab)
                                                     }
                                                 }),
                                                 null,
                                                 heapStatisticsTab,
                                                 { highlightTreeTableEntry(topDSAllocSiteNode, treeTableView, true) } revertVia { treeTableView.selectionModel.clearSelection() },
                                                 Bindings.createBooleanBinding(Callable {
                                                     !treeTableView.root.value.hasDeepChild(topDSAllocSiteNode)
                                                 }, *treeTableView.getRecursiveParentTreeItems(topDSAllocSiteNode).map { it.children }.toTypedArray())))

            } else {
                // no allocation whose retained size covers at least 15% of the total heap size
                // suggest to further look at the roots of all data structures of the previously detected type
                heapStatisticsTab.ideas.add(Idea("Interesting data structures",
                                                 Description("Over ")
                                                         .appendEmphasized("${(topDSTypeNodeRetainedSizePortion * 100.0).toInt()}% ")
                                                         .appendDefault("of this heap is kept alive by ")
                                                         .appendEmphasized("${topDSTypeNode.objectCount.toString("%,d")} ")
                                                         .appendDefault("data structures of type ")
                                                         .appendCode(topDSTypeNode.key.toString().substringAfterLast("."))
                                                         .appendDefault(". ")
                                                         .linebreak()
                                                         .appendDefault("These data structures are alive because they are directly or indirectly referenced by some GC roots."),
                                                 listOf("Inspect GC roots" does {
                                                     performLocalClassification(topDSTypeNode, gcRootsClassifierChain, treeTableView, heapStatisticsTab) {
                                                         highlightTreeTableEntry(topDSTypeNode, treeTableView, true)
                                                         treeTableView.expandRecursivelyFrom(topDSTypeNode, true)
                                                         analyzeRootPointers(topDSTypeNode,
                                                                             heapStatisticsTab)
                                                     }
                                                 }),
                                                 null,
                                                 heapStatisticsTab,
                                                 { highlightTreeTableEntry(topDSTypeNode, treeTableView, true) } revertVia { treeTableView.selectionModel.clearSelection() },
                                                 Bindings.createBooleanBinding(Callable {
                                                     !treeTableView.root.value.hasDeepChild(topDSTypeNode)
                                                 }, *treeTableView.getRecursiveParentTreeItems(topDSTypeNode).map { it.children }.toTypedArray())))
            }

        } else {
            // the data structures are not interesting...instead suggest to directly check the roots of the previously identified object group
            heapStatisticsTab.ideas.add(Idea("No interesting data structures",
                                             Description("None of the data structures that contain these objects own a substantial part of the heap memory. ")
                                                     .linebreak()
                                                     .appendDefault("However, maybe most of these objects share a common GC root which consequently keeps a lot of memory alive."),
                                             listOf("Inspect GC roots" does {
                                                 performLocalClassification(dsContainedNode, gcRootsClassifierChain, treeTableView, heapStatisticsTab) {
                                                     highlightTreeTableEntry(dsContainedNode, treeTableView, true)
                                                     treeTableView.expandRecursivelyFrom(dsContainedNode, true)
                                                     analyzeRootPointers(dsContainedNode, heapStatisticsTab)
                                                 }
                                             }),
                                             null,
                                             heapStatisticsTab,
                                             {
                                                 highlightTreeTableEntry(dsContainedNode.children.first(),
                                                                         treeTableView,
                                                                         false)
                                             } revertVia { treeTableView.selectionModel.clearSelection() },
                                             Bindings.createBooleanBinding(Callable {
                                                 !treeTableView.root.value.hasDeepChild(dsContainedNode)
                                             }, *treeTableView.getRecursiveParentTreeItems(dsContainedNode).map { it.children }.toTypedArray())))
        }
    }

    private fun analyzeRootPointers(rootPointedNode: GroupingNode, heapStatisticsTab: HeapStateClassificationTab) {
        val treeTableView = heapStatisticsTab.treeTableView
        val heapByteCount = heapStatisticsTab.statisticsInfo.heapStateInfo.fastHeapSupplier.get()!!.byteCount

        // check if gc root objects 'own' a lot of memory
        val topRootNodes = treeTableView.filter(treeTableView.getTreeItem(rootPointedNode)) { treeItem ->
            val fullRootKey = treeItem.value.fullKeyAsString
            // interesting roots own at least 10% of the heap, are classification tree leaves and are variable roots
            treeItem.value.retainedSizeProperty().get() / heapByteCount.toDouble() >= 0.10 &&
                    treeItem.children.isEmpty() &&
                    (fullRootKey.contains("local variable") || fullRootKey.contains("static field") || fullRootKey.contains("JNI"))
        }.map { it.value }

        topRootNodes.forEach { topRootNode ->
            val fullRootKey = topRootNode.fullKeyAsString
            heapStatisticsTab.ideas.add(Idea("Interesting GC root",
                                             Description("There is a ${
                                                 when {
                                                     fullRootKey.contains("local variable") -> "local variable"
                                                     fullRootKey.contains("static field") -> "static field"
                                                     fullRootKey.contains("JNI") -> "JNI object"
                                                     else -> "GC root"
                                                 }
                                             } that keeps at least ")
                                                     .appendEmphasized("${(topRootNode.retainedSizeProperty().get() / heapByteCount.toDouble()).times(100).toInt()}% ")
                                                     .appendDefault("of the heap alive! ")
                                                     .linebreak()
                                                     .appendDefault("You can free this memory by eliminating this GC root in your code."),
                                             null,
                                             null,
                                             heapStatisticsTab,
                                             { highlightTreeTableEntry(topRootNode, treeTableView, true) } revertVia { treeTableView.selectionModel.clearSelection() },
                                             Bindings.createBooleanBinding(Callable {
                                                 !treeTableView.root.value.hasDeepChild(topRootNode)
                                             }, *treeTableView.getRecursiveParentTreeItems(topRootNode).map { it.children }.toTypedArray())))
        }

        if (topRootNodes.isEmpty()) {
            if (!heapStatisticsTab.isInDataStructureView()) {
                heapStatisticsTab.ideas.add(Idea("No interesting GC roots",
                                                 Description("None of the GC roots seems to keep alive a substantial portion of the heap. ")
                                                         .linebreak()
                                                         .appendDefault("Now you could try to take a look at all the data structures in this heap and see whether any of them keeps a lot of memory alive."),
                                                 listOf("Switch to data structure view" does { heapStatisticsTab.switchToDataStructureView() }),
                                                 listOf(heapStatisticsTab.configurationPane.dataStructureSwitch at Idea.BulbPosition.TOP_RIGHT),
                                                 heapStatisticsTab))
            }
        }
    }
}