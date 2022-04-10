package at.jku.anttracks.gui.utils.ideagenerators

import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.component.table.PermBornDiedTempTreeTableView
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.model.PermBornDiedTempData
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.tab.classification.PermBornDiedTempClassificationTab
import at.jku.anttracks.gui.model.Description
import at.jku.anttracks.gui.model.Idea
import at.jku.anttracks.gui.model.does
import at.jku.anttracks.gui.model.revertVia
import at.jku.anttracks.util.ThreadUtil
import javafx.application.Platform
import javafx.scene.control.TreeItem

object PermBornDiedTempIdeaGenerator {

    private fun highlightTreeTableEntry(treeItemToHighlight: TreeItem<PermBornDiedTempData>, treeTableView: PermBornDiedTempTreeTableView) {
        treeTableView.expandRecursivelyUpTo(treeItemToHighlight)
        treeTableView.sortByBytes()

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
    fun analyze(classificationTab: PermBornDiedTempClassificationTab) {
        val treeTableView = classificationTab.treeTableView
        val info = classificationTab.info
        val heapByteCount = info.currentIndexBasedHeap.byteCount
        val root = if (treeTableView.root.children.firstOrNull()?.value?.id?.equals("Filtered") ?: false) {
            treeTableView.root.children.first()
        } else {
            treeTableView.root
        }

        if (info.minorGCOverhead >= 0.05) {
            // there is substantial gc overhead caused by minor collections
            // minor gcs are slowed down by many born objects as those have to be copied a few times before reaching the old generation
            val typeTreeItemWithMaxBornBytes = root.children.maxBy { it.value.born.bytes }
            val typeTreeItemWithMaxBornBytesHeapPortion = (typeTreeItemWithMaxBornBytes?.value?.born?.bytes ?: 0.0) / heapByteCount
            if (typeTreeItemWithMaxBornBytesHeapPortion >= 0.075) {
                // born objects of a single type make up at least 10% of the final heap
                // check whether there is an allocation site for this type that still fulfills this constraint
                val allocSiteTreeItemWithMaxBornBytes = typeTreeItemWithMaxBornBytes!!.children.maxBy { it.value.born.bytes }
                val allocSiteTreeItemWithMaxBornBytesHeapPortion = (allocSiteTreeItemWithMaxBornBytes?.value?.born?.bytes ?: 0.0) / heapByteCount

                if (allocSiteTreeItemWithMaxBornBytesHeapPortion >= 0.05) {
                    // born objects from a single allocation site make up at least 5% of the final heap
                    classificationTab.ideas.add(Idea("Speed up minor GCs!",
                                                     Description("In this timeframe, your app spends over ")
                                                             .appendEmphasized("${(info.minorGCOverhead * 100).toInt()}% ")
                                                             .appendDefault("of its runtime with minor GCs. Minor GCs take longer when many new objects are allocated and survive (BORN), because these objects have to be copied during multiple GCs before they are finally promoted to the old generation. ")
                                                             .linebreak()
                                                             .appendDefault("BORN objects of type ")
                                                             .appendCode(typeTreeItemWithMaxBornBytes.value.toString().substringAfterLast("."))
                                                             .appendDefault(" allocated in the method ")
                                                             .appendCode(allocSiteTreeItemWithMaxBornBytes!!.value.toString().substringBefore("("))
                                                             .appendDefault(" make up over ")
                                                             .appendEmphasized("${allocSiteTreeItemWithMaxBornBytesHeapPortion.times(100).toInt()}% ")
                                                             .appendDefault("of the final heap!")
                                                             .linebreak()
                                                             .appendDefault("Expand this table item to find out from where this method was called."),
                                                     listOf("Expand call stack" does {
                                                         highlightTreeTableEntry(allocSiteTreeItemWithMaxBornBytes, treeTableView)
                                                         treeTableView.expandRecursivelyFrom(allocSiteTreeItemWithMaxBornBytes, true)
                                                     }),
                                                     null,
                                                     classificationTab,
                                                     {
                                                         highlightTreeTableEntry(allocSiteTreeItemWithMaxBornBytes,
                                                                                 treeTableView)
                                                     } revertVia { treeTableView.selectionModel.clearSelection() }))

                } else {
                    classificationTab.ideas.add(Idea("Speed up minor GCs!",
                                                     Description("In this timeframe, your app spends over ")
                                                             .appendEmphasized("${(info.minorGCOverhead * 100).toInt()}% ")
                                                             .appendDefault("of its runtime with minor GCs. Minor GCs take longer when many new objects are allocated and survive (BORN), because these objects have to be copied during multiple GCs before they are finally promoted to the old generation. ")
                                                             .linebreak()
                                                             .appendDefault("BORN objects of type ")
                                                             .appendCode(typeTreeItemWithMaxBornBytes.value.toString().substringAfterLast("."))
                                                             .appendDefault(" make up over ")
                                                             .appendEmphasized("${allocSiteTreeItemWithMaxBornBytesHeapPortion.times(100).toInt()}% ")
                                                             .appendDefault("of the final heap!"),
                                                     null,
                                                     null,
                                                     classificationTab,
                                                     {
                                                         highlightTreeTableEntry(typeTreeItemWithMaxBornBytes,
                                                                                 treeTableView)
                                                     } revertVia { treeTableView.selectionModel.clearSelection() }))
                }
            }
        }

        if (info.majorGCOverhead >= 0.05) {
            // there is substantial gc overhead caused by major collections
            // major gcs are slowed down by both born and perm objects (i.e. all surviving objects)
            val typeTreeItemWithMaxSurvivedBytes = root.children.maxBy { it.value.born.bytes + it.value.perm.bytes }
            val typeTreeItemWithMaxSurvivedBytesHeapPortion = ((typeTreeItemWithMaxSurvivedBytes?.value?.born?.bytes ?: 0.0) + (typeTreeItemWithMaxSurvivedBytes?.value?.perm?.bytes
                    ?: 0.0)) / heapByteCount
            if (typeTreeItemWithMaxSurvivedBytesHeapPortion >= 0.15) {
                // a single type is responsible for at least 15% of all survived bytes over this timeframe
                // check whether there is an allocation site for this type that still fulfills this constraint
                val allocSiteTreeItemWithMaxSurvivedBytes = typeTreeItemWithMaxSurvivedBytes!!.children.maxBy { it.value.born.bytes + it.value.perm.bytes }
                val allocSiteTreeItemWithMaxBornSurvivedHeapPortion = ((allocSiteTreeItemWithMaxSurvivedBytes?.value?.born?.bytes
                        ?: 0.0) + (allocSiteTreeItemWithMaxSurvivedBytes?.value?.perm?.bytes ?: 0.0)) / heapByteCount

                if (allocSiteTreeItemWithMaxBornSurvivedHeapPortion >= 0.1) {
                    // a single allocation site is responsible for at least 10% of all survived bytes over this timeframe
                    classificationTab.ideas.add(Idea("Speed up major GCs!",
                                                     Description("In this timeframe, your app spends over ")
                                                             .appendEmphasized("${info.majorGCOverhead.times(100).toInt()}% ")
                                                             .appendDefault("of its runtime with major GCs. Major GCs take longer when many objects in the old generation (PERM and BORN) survive, because they have to be copied during each major GC. ")
                                                             .linebreak()
                                                             .appendDefault("In this timeframe, over ")
                                                             .appendEmphasized("${allocSiteTreeItemWithMaxBornSurvivedHeapPortion.times(100).toInt()}% ")
                                                             .appendDefault("of the PERM and BORN objects are of type ")
                                                             .appendCode(typeTreeItemWithMaxSurvivedBytes.value.toString().substringAfterLast("."))
                                                             .appendDefault(" allocated in the method ")
                                                             .appendCode(allocSiteTreeItemWithMaxSurvivedBytes!!.value.toString().substringBefore("("))
                                                             .appendDefault(".")
                                                             .linebreak()
                                                             .appendDefault("Expand this table item to find out from where this method was called."),
                                                     listOf("Expand call stack" does {
                                                         highlightTreeTableEntry(allocSiteTreeItemWithMaxSurvivedBytes, treeTableView)
                                                         treeTableView.expandRecursivelyFrom(allocSiteTreeItemWithMaxSurvivedBytes, true)
                                                     }),
                                                     null,
                                                     classificationTab,
                                                     {
                                                         highlightTreeTableEntry(allocSiteTreeItemWithMaxSurvivedBytes,
                                                                                 treeTableView)
                                                     } revertVia { treeTableView.selectionModel.clearSelection() }))

                } else {
                    classificationTab.ideas.add(Idea("Speed up major GCs!",
                                                     Description("In this timeframe, your app spends over ")
                                                             .appendEmphasized("${info.majorGCOverhead.times(100).toInt()}% ")
                                                             .appendDefault("of its runtime with major GCs. Major GCs take longer when many objects in the old generation (PERM and BORN) survive, because they have to be copied during each major GC. ")
                                                             .linebreak()
                                                             .appendDefault("In this timeframe, over ")
                                                             .appendEmphasized("${typeTreeItemWithMaxSurvivedBytesHeapPortion.times(100).toInt()}% ")
                                                             .appendDefault("of the PERM and BORN objects are of type ")
                                                             .appendCode(typeTreeItemWithMaxSurvivedBytes.value.toString().substringAfterLast("."))
                                                             .appendDefault("."),
                                                     null,
                                                     null,
                                                     classificationTab,
                                                     {
                                                         highlightTreeTableEntry(typeTreeItemWithMaxSurvivedBytes,
                                                                                 treeTableView)
                                                     } revertVia { treeTableView.selectionModel.clearSelection() }))
                }
            }
        }
    }
}