package at.jku.anttracks.gui.utils.ideagenerators

import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.nodes.GroupingNode
import at.jku.anttracks.gui.classification.classifier.AllocationSiteClassifier
import at.jku.anttracks.gui.classification.classifier.CallSitesClassifier
import at.jku.anttracks.gui.classification.classifier.DataStructureLeavesTransformer
import at.jku.anttracks.gui.classification.classifier.TypeClassifier
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.component.tree.DataStructureDevelopmentClassificationTreeTableView
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.tab.classification.DataStructureDevelopmentClassificationTab
import at.jku.anttracks.gui.model.Description
import at.jku.anttracks.gui.model.Idea
import at.jku.anttracks.gui.model.does
import at.jku.anttracks.gui.model.revertVia
import at.jku.anttracks.gui.utils.ideagenerators.IdeaGeneratorUtil.performLocalClassification
import at.jku.anttracks.gui.utils.toShortMemoryUsageString
import at.jku.anttracks.util.ThreadUtil
import javafx.application.Platform
import javafx.beans.binding.Bindings
import java.util.concurrent.Callable

object DataStructureDevelopmentIdeaGenerator {

    private fun highlightTreeTableEntry(nodeToHighlight: GroupingNode,
                                        treeTableView: DataStructureDevelopmentClassificationTreeTableView,
                                        ensureAbsRetainedSizeGrowthSorting: Boolean) {
        // get updated item for the given grouping node
        val treeItemToHighlight = treeTableView.getTreeItem(nodeToHighlight)

        // highlight the item
        treeTableView.expandRecursivelyUpTo(treeItemToHighlight)
        if (ensureAbsRetainedSizeGrowthSorting) {
            treeTableView.sortByAbsoluteRetainedSizeGrowth()
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
    fun analyze(classificationTab: DataStructureDevelopmentClassificationTab) {
        val dsDevelopmentWithMaxRetainedHGP = classificationTab.info.dataStructures.maxBy { it.sizeInfo!!.absoluteRetainedGrowthRate }

        dsDevelopmentWithMaxRetainedHGP?.also { dsDevelopment ->
            val dsAddressNode = classificationTab.treeTableView.filter { it.value.key.toString().contains("%,d".format(dsDevelopment.endAddress)) }.first().value
            val allocationSiteClassifierChain = ClassifierChain(classificationTab.info.heapEvolutionInfo.availableClassifier["Allocation Site"],
                                                                classificationTab.info.heapEvolutionInfo.availableClassifier["Call Sites"])
            val gcRootClassifierChain = ClassifierChain(classificationTab.info.heapEvolutionInfo.availableClassifier["GC Roots: Closest"])
            val dataStructureLeavesClassifierChain = ClassifierChain(classificationTab.info.heapEvolutionInfo.availableClassifier[DataStructureLeavesTransformer.NAME].also {
                (it as DataStructureLeavesTransformer).setClassifiers(
                        ClassifierChain(classificationTab.info.heapEvolutionInfo.availableClassifier[TypeClassifier::class],
                                        classificationTab.info.heapEvolutionInfo.availableClassifier[AllocationSiteClassifier::class],
                                        classificationTab.info.heapEvolutionInfo.availableClassifier[CallSitesClassifier::class]))
            })


            classificationTab.ideas.add(Idea("Growing data structure",
                                             Description("There is a ")
                                                     .appendCode(dsAddressNode.key.toString().substringBeforeLast("[").substringAfterLast("."))
                                                     .appendDefault(" for which the memory it keeps alive has grown by over ")
                                                     .appendEmphasized(toShortMemoryUsageString(dsDevelopment.sizeInfo!!.absoluteRetainedGrowthRate))
                                                     .appendDefault(" in the analyzed timeframe. This corresponds to over ")
                                                     .appendEmphasized("${dsDevelopment.sizeInfo!!.retainedHeapGrowthPortion.toInt()}% ")
                                                     .appendDefault("of the total heap memory growth.")
                                                     .linebreak()
                                                     .appendDefault("To locate the data structure in your code, you might want to check out ")
                                                     .appendEmphasized("where and why ")
                                                     .appendDefault("this data structure was allocated and also take a look at the ")
                                                     .appendEmphasized("GC roots ")
                                                     .appendDefault("that keep this data structure alive. ")
                                                     .linebreak()
                                                     .appendDefault("Alternatively you might want to see the ")
                                                     .appendEmphasized("data structure leaves")
                                                     .appendDefault(", that is, the actual data objects that are contained in the data structure."),
                                             listOf("Show allocation and call sites" does {
                                                 performLocalClassification(dsAddressNode,
                                                                            allocationSiteClassifierChain,
                                                                            classificationTab.treeTableView,
                                                                            classificationTab) {
                                                     highlightTreeTableEntry(dsAddressNode,
                                                                             classificationTab.treeTableView,
                                                                             true)
                                                     classificationTab.treeTableView.expandRecursivelyFrom(dsAddressNode, true)
                                                 }
                                             },
                                                    "Show GC roots" does {
                                                        performLocalClassification(dsAddressNode,
                                                                                   gcRootClassifierChain,
                                                                                   classificationTab.treeTableView,
                                                                                   classificationTab) {
                                                            highlightTreeTableEntry(dsAddressNode,
                                                                                    classificationTab.treeTableView,
                                                                                    true)
                                                            classificationTab.treeTableView.expandRecursivelyFrom(dsAddressNode, true)
                                                        }
                                                    },
                                                    "Show data structure leaves" does {
                                                        performLocalClassification(dsAddressNode,
                                                                                   dataStructureLeavesClassifierChain,
                                                                                   classificationTab.treeTableView,
                                                                                   classificationTab) {
                                                            analyzeDataStructureLeaves(dsAddressNode, classificationTab)
                                                            highlightTreeTableEntry(dsAddressNode, classificationTab.treeTableView, true)
                                                            classificationTab.treeTableView.expandRecursivelyFrom(dsAddressNode, true)
                                                        }
                                                    }),
                                             null,
                                             classificationTab,
                                             {
                                                 highlightTreeTableEntry(dsAddressNode,
                                                                         classificationTab.treeTableView,
                                                                         true)
                                             } revertVia { classificationTab.treeTableView.selectionModel.clearSelection() },
                                             Bindings.createBooleanBinding(Callable {
                                                 !classificationTab.treeTableView.root.value.hasDeepChild(dsAddressNode)
                                             }, *classificationTab.treeTableView.getRecursiveParentTreeItems(dsAddressNode).map { it.children }.toTypedArray())))

        }
    }

    private fun analyzeDataStructureLeaves(dsAddressNode: GroupingNode, classificationTab: DataStructureDevelopmentClassificationTab) {
        if (dsAddressNode.children.first().children.size > 1) {
            // there is more than one kind of data structure leaves thus we help the user to look at the right one
            val treeTableView = classificationTab.treeTableView
            val topLeafTypeNode = dsAddressNode.children.first().children.maxBy { it.retainedSizeProperty().get() }
            val topLeafTypeNodeInStartClassificationTree = classificationTab.info.startClassificationTree.root.getChildWithFullKey(topLeafTypeNode!!.fullKey)

            classificationTab.ideas.add(Idea("Interesting data structure leaves",
                                             Description("Over this timeframe, ")
                                                     .appendEmphasized("%,d ".format(topLeafTypeNode.objectCount - (topLeafTypeNodeInStartClassificationTree?.objectCount ?: 0)))
                                                     .appendDefault("objects of type ")
                                                     .appendCode(topLeafTypeNode.key.toString().substringAfterLast("."))
                                                     .appendDefault(" have been added to this data structure. Consequently the memory that is kept alive by these objects has increased by ")
                                                     .appendEmphasized(toShortMemoryUsageString(topLeafTypeNode.retainedSizeProperty().get() - (topLeafTypeNodeInStartClassificationTree?.retainedSizeProperty()?.get()
                                                             ?: 0)))
                                                     .appendDefault(". ")
                                                     .linebreak()
                                                     .appendDefault("They are the biggest contributor to this data structure's growing memory consumption. ")
                                                     .appendDefault("You might want to check out where and why these objects have been allocated."),
                                             listOf("Expand allocation & call sites" does {
                                                 highlightTreeTableEntry(topLeafTypeNode, classificationTab.treeTableView, true)
                                                 classificationTab.treeTableView.expandRecursivelyFrom(topLeafTypeNode, true)
                                             }),
                                             null,
                                             classificationTab,
                                             {
                                                 highlightTreeTableEntry(topLeafTypeNode,
                                                                         classificationTab.treeTableView,
                                                                         true)
                                             } revertVia { classificationTab.treeTableView.selectionModel.clearSelection() },
                                             Bindings.createBooleanBinding(Callable {
                                                 !classificationTab.treeTableView.root.value.hasDeepChild(topLeafTypeNode)
                                             }, *treeTableView.getRecursiveParentTreeItems(topLeafTypeNode).map { it.children }.toTypedArray())))
        }
    }
}