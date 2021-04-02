package at.jku.anttracks.gui.utils.ideagenerators

import at.jku.anttracks.classification.nodes.GroupingNode
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.component.tree.ShortLivedObjectsClassificationTreeTableView
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.tab.classification.ShortLivedObjectsClassificationTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.tab.overview.ShortLivedObjectsOverviewTab
import at.jku.anttracks.gui.model.*
import at.jku.anttracks.gui.utils.NodeUtil
import at.jku.anttracks.util.ThreadUtil
import javafx.application.Platform
import javafx.scene.control.TreeItem

object ShortLivedObjectsAnalysisIdeaGenerator {
    var showTypeIdea = false
    var showAllocationSiteIdea = false
    var showIDEIdea = false

    private fun highlightTreeTableEntry(treeItemToHighlight: TreeItem<GroupingNode>, treeTableView: ShortLivedObjectsClassificationTreeTableView, sortByBytes: Boolean) {
        treeTableView.expandRecursivelyUpTo(treeItemToHighlight)
        if (sortByBytes) {
            treeTableView.sortByBytes()
        } else {
            treeTableView.sortByObjects()
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
    fun analyzeOverviewTab(overviewTab: ShortLivedObjectsOverviewTab) {
        val totalGarbageObjects = overviewTab.objectGarbagePerTypeChart.data.sumByDouble { it.pieValue }
        val totalGarbageMemory = overviewTab.memoryGarbagePerTypeChart.data.sumByDouble { it.pieValue }
        val topGarbageObjectsType = overviewTab.objectGarbagePerTypeChart.data.filter { it.name != "Other" && it.pieValue / totalGarbageObjects >= 0.15 }.maxBy { it.pieValue }
        val topGarbageObjectsAllocSite = overviewTab.objectGarbagePerAllocationSiteChart.data.filter { it.name != "Other" && it.pieValue / totalGarbageObjects >= 0.15 }
                .maxBy { it.pieValue }
        val topGarbageMemoryType = overviewTab.memoryGarbagePerTypeChart.data.filter { it.name != "Other" && it.pieValue / totalGarbageMemory >= 0.15 }.maxBy { it.pieValue }
        val topGarbageMemoryAllocSite = overviewTab.memoryGarbagePerAllocationSiteChart.data.filter { it.name != "Other" && it.pieValue / totalGarbageMemory >= 0.15 }.maxBy { it.pieValue }

        if (overviewTab.info.gcFrequency / overviewTab.appInfo.gcFrequency >= 1.5 || overviewTab.info.gcFrequency >= 10) {
            // the gc frequency is high over the selected window
            // suggest the user to inspect types/allocsites that cause a lot of garbage memory, as there lies big potential in avoiding garbage collections
            /*if (topGarbageMemoryType != null) {
                // a single type makes up at least 15% of all garbage memory
                val garbagePercent = (topGarbageMemoryType.pieValue / totalGarbageMemory * 100).toInt()
                overviewTab.ideas.add(Idea("Suspicious garbage composition!",
                                           Description("The garbage collection frequency is very high over this timeframe, if possible you should try to reduce it!\n")
                                                   a "Over " e "$garbagePercent%" a " of the garbage memory was occupied by objects of type " c topGarbageMemoryType.name a ".\n"
                                                   a "By avoiding the allocation of these objects, you can reduce the number of garbage collections!",
                                           listOf("Show where garbage of this type was allocated" does { NodeUtil.fireClickEvent(topGarbageMemoryType.node) }),
                                           listOf(overviewTab.memoryGarbagePerTypeChart at Idea.BulbPosition.TOP_RIGHT),
                                           overviewTab))

            } else if (topGarbageMemoryAllocSite != null) {
                // a single alloc site makes up at least 15% of all garbage memory
                overviewTab.ideas.add(Idea("Suspicious garbage composition!",
                                           Description("The garbage collection frequency is very high over this timeframe, if possible you should try to reduce it! ")
                                                   .linebreak()
                                                   .appendDefault("Over ")
                                                   .appendEmphasized("${(topGarbageMemoryAllocSite.pieValue / totalGarbageMemory * 100).toInt()}% ")
                                                   .appendDefault("of the garbage memory was occupied by objects of type ")
                                                   .appendCode(topGarbageMemoryAllocSite.name)
                                                   .appendDefault(". By avoiding the allocation of these objects, you can reduce the number of garbage collections!"),
                                           listOf("Show what type of garbage was allocated in this method" does { NodeUtil.fireClickEvent(topGarbageMemoryAllocSite.node) }),
                                           listOf(overviewTab.gcFrequencyChart at Idea.BulbPosition.TOP_RIGHT,
                                                  overviewTab.memoryGarbagePerAllocationSiteChart at Idea.BulbPosition.TOP_RIGHT,
                                                  topGarbageMemoryAllocSite.node at Idea.BulbPosition.TOP_RIGHT),
                                           overviewTab))
            }*/

            if (topGarbageObjectsType != null) {
                // the gc frequency is okay but there is a type that produces at least 15% of all garbage objects -> suggest to reduce allocations for speedup
                overviewTab.ideas.add(Idea("Suspicious garbage composition!",
                                           Description("The garbage collection frequency is very high over this timeframe. You should try to reduce it.")
                                                   .ln()
                                                   .appendDefault("Over ")
                                                   .appendEmphasized("${(topGarbageObjectsType.pieValue / totalGarbageObjects * 100).toInt()}% ")
                                                   .appendDefault("of the garbage collected objects were of type ")
                                                   .appendCode(topGarbageObjectsType.name)
                                                   .appendDefault(". ")
                                                   .linebreak()
                                                   .appendDefault("Are all these object allocations really necessary?")
                                                   .appendDefault(" By avoiding the allocation of these objects, you can reduce the number of garbage collections and thus speed up your application!"),
                                           listOf("Inspect garbage objects" does { NodeUtil.fireClickEvent(topGarbageObjectsType.node) }),
                                           listOf(overviewTab.objectGarbagePerTypeChart at Idea.BulbPosition.TOP_RIGHT),
                                           overviewTab))

            }
        } else if (topGarbageObjectsType != null) {
            // the gc frequency is okay but there is a type that produces at least 15% of all garbage objects -> suggest to reduce allocations for speedup
/*                overviewTab.ideas.add(Idea("Suspicious garbage composition!",
                                           Description("Your application has a relatively low GC frequency over this timeframe. However maybe you can avoid some unnecessary allocations and speed up your application this way. ")
                                                   .linebreak()
                                                   .appendDefault("Over ")
                                                   .appendEmphasized("${(topGarbageObjectsType.pieValue / totalGarbageObjects * 100).toInt()}% ")
                                                   .appendDefault("of the garbage objects were of type ")
                                                   .appendCode(topGarbageObjectsType.name)
                                                   .appendDefault(". ")
                                                   .linebreak()
                                                   .appendDefault("Are all these objects really necessary?"),
                                           listOf("Show where garbage of this type was allocated" does { NodeUtil.fireClickEvent(topGarbageObjectsType.node) }),
                                           listOf(overviewTab.objectGarbagePerTypeChart at Idea.BulbPosition.TOP_RIGHT,
                                                  topGarbageObjectsType.node at Idea.BulbPosition.TOP_RIGHT),
                                           overviewTab))

            } else if (topGarbageObjectsAllocSite != null) {
                // the gc frequency is okay but there is an allocation site that produces at least 15% of all garbage objects -> suggest to reduce allocations for speedup
                overviewTab.ideas.add(Idea("Suspicious garbage composition!",
                                           Description("Your application has a relatively low GC frequency over this timeframe. However maybe you can avoid some unnecessary allocations and speed up your application this way. ")
                                                   .linebreak()
                                                   .appendDefault("Over ")
                                                   .appendEmphasized("${(topGarbageObjectsAllocSite.pieValue / totalGarbageObjects * 100).toInt()}% ")
                                                   .appendDefault("of the garbage objects were allocated in the method ")
                                                   .appendCode(topGarbageObjectsAllocSite.name)
                                                   .appendDefault(". ")
                                                   .linebreak()
                                                   .appendDefault("Are all these objects really necessary?"),
                                           listOf("Show what type of garbage was allocated in this method" does { NodeUtil.fireClickEvent(topGarbageObjectsAllocSite.node) }),
                                           listOf(overviewTab.objectGarbagePerAllocationSiteChart at Idea.BulbPosition.TOP_RIGHT, topGarbageObjectsAllocSite.node at Idea.BulbPosition.TOP_RIGHT),
                                           overviewTab))

            } else {
                // the gc frequency over the timeframe is normal and there are no single types or alloc sites that produce a substantial part of the garbage
                overviewTab.ideas.add(Idea("This looks okay",
                                           Description("The GC frequency over this timeframe is relatively low and no single type or allocation site alone seems to be responsible for a substantial portion of the produced garbage. ")
                                                   .linebreak()
                                                   .appendDefault("If your looking to improve the garbage collection behaviour of your application you should choose a different time window!"),
                                           null,
                                           null,
                                           overviewTab))
            }*/
        }
    }

    @JvmStatic
    fun analyzeClassificationTab(classificationTab: ShortLivedObjectsClassificationTab) {
        val treeTableView = classificationTab.treeTableView
        val totalByteCount = classificationTab.info.garbageGroupedByType.root.getByteCount(null).toDouble()
        val totalObjectCount = classificationTab.info.garbageGroupedByType.root.objectCount.toDouble()

        if (treeTableView.classificationTree.classifiers.list.take(4) == classificationTab.info.typeClassifierChain.list) {
            // classifiers are age -> type -> alloc site -> call sites
            val zeroGcsSurvivedTreeItem = treeTableView.filter {
                //it.parent != null && it.parent.value.key.toString() == "Overall" &&
                it.value.key == "0 GCs survived"
            }.first()

            // val topGarbageMemoryAllocSite = interestingByteCountTreeItems.maxBy { it.value.getByteCount(null) }
            // val topGarbageObjectsAllocSite = interestingObjectCountTreeItems.maxBy { it.value.objectCount }

            classificationTab.ideas.add(Idea("0 GCs survived",
                                             Description("Allocating and discarding objects shortly afterwards puts unnecessary burden on the garabage collector.")
                                                     .a(" Especially objects that do not survive a single garbage collection are the major contributors to unnecessary memory churn.")
                                                     .ln()
                                                     .a("Over ")
                                                     .appendEmphasized("${(zeroGcsSurvivedTreeItem.value.objectCount / totalObjectCount * 100.0).toInt()}% ")
                                                     .appendDefault(" of all garbage collected objects in the selected time window did not survive a single garbage collection.")
                                                     .linebreak()
                                                     .appendDefault("We suggest to check the types of these objects, since you may want to reduce the number of allocations of these objects.")
                                                     .appendDefault("To do so, expand this table entry."),
                                             listOf("Expand types" does {
                                                 highlightTreeTableEntry(zeroGcsSurvivedTreeItem, treeTableView, false)
                                                 treeTableView.expandRecursivelyFrom(zeroGcsSurvivedTreeItem, true)

                                                 if (!showTypeIdea) {
                                                     showTypeIdea = true

                                                     val topType = zeroGcsSurvivedTreeItem.children.sortedByDescending { it.value.objectCount }.first()

                                                     classificationTab.ideas.add(Idea("Most garbage per type",
                                                                                      Description("Objects of type ")
                                                                                              .c(topType.value.key.toString())
                                                                                              .a(" account for over ")
                                                                                              .e("${(topType.value.objectCount / zeroGcsSurvivedTreeItem.value.objectCount * 100.0).toInt()}%")
                                                                                              .a(" of objects that died without surviving a single garbage collection.")
                                                                                              .ln()
                                                                                              .a("We suggest to inspect the allocation sites of these ")
                                                                                              .c(topType.value.key.toString())
                                                                                              .a(" objects, i.e., the methods in which these objects have been allocated. " +
                                                                                                         "This gives you information on where to find these allocations in the source code."),
                                                                                      listOf("Expand allocation sites" does {
                                                                                          highlightTreeTableEntry(topType, treeTableView, false)
                                                                                          treeTableView.expandRecursivelyFrom(topType, true)

                                                                                          if (!showAllocationSiteIdea) {
                                                                                              showAllocationSiteIdea = true

                                                                                              val topAllocSite = topType.children.sortedByDescending { it.value.objectCount }.first()

                                                                                              classificationTab.ideas.add(Idea(
                                                                                                      "Allocation-heaviest method for type ${topType.value.key.toString()}",
                                                                                                      Description()
                                                                                                              .e("${(topAllocSite.value.objectCount / topType.value.objectCount * 100.0).toInt()}%")
                                                                                                              .a(" of ")
                                                                                                              .c(topType.value.key.toString())
                                                                                                              .a(" objects have been allocated in the method ")
                                                                                                              .c(topAllocSite.value.key.toString())
                                                                                                              .ln()
                                                                                                              .a("Since allocations are sometimes hidden within utility methods, as a last step, we " +
                                                                                                                         "suggest to also inspect the call sites, i.e., the methods that called the " +
                                                                                                                         "allocating method."),
                                                                                                      listOf("Expand allocation sites" does {
                                                                                                          highlightTreeTableEntry(topAllocSite, treeTableView, false)
                                                                                                          treeTableView.expandRecursivelyFrom(topAllocSite, false)

                                                                                                          if (!showIDEIdea) {
                                                                                                              showIDEIdea = true

                                                                                                              classificationTab.ideas.add(Idea(
                                                                                                                      "Inspect memory churn in the IDE",
                                                                                                                      Description()
                                                                                                                              .a("You now know that the most garbage is caused by objects of type ")
                                                                                                                              .c(topType.value.key.toString())
                                                                                                                              .a(" that have been been allocated in the method ")
                                                                                                                              .c(topAllocSite.value.key.toString())
                                                                                                                              .a(". In addition to that, the tree table now also shows the call " +
                                                                                                                                         "chains that led to these allocations. Types, allocation " +
                                                                                                                                         "sites and call sites written in")
                                                                                                                              .e(" bold ")
                                                                                                                              .a(" highlight types and methods within your code base, i.e., ")
                                                                                                                              .e("information of special interest.")
                                                                                                                              .ln()
                                                                                                                              .ln()
                                                                                                                              .a("Please switch to your IDE now and inspect the code.")
                                                                                                                              .ln()
                                                                                                                              .ln()
                                                                                                                              .a("In the source code, look for the allocations of the objects " +
                                                                                                                                         "mentioned above and try to reduce the number of these " +
                                                                                                                                         "allocations.")
                                                                                                                              .ln()
                                                                                                                              .a("Typical root causes of memory churn are: " +
                                                                                                                                         "(1) allocations inside ")
                                                                                                                              .e("heavily-executed loops")
                                                                                                                              .a(", (2) the careless use of ")
                                                                                                                              .e("boxed primitives")
                                                                                                                              .a(" as generic types, e.g., ")
                                                                                                                              .c("ArrayList<Integer>")
                                                                                                                              .a(", or (3) the careless use of ")
                                                                                                                              .e("streams")
                                                                                                                              .a(" (such as unnecessary ")
                                                                                                                              .c("map()")
                                                                                                                              .a(" operations or ")
                                                                                                                              .c("filter()")
                                                                                                                              .a(" operations that are applied too late)."),
                                                                                                                      null,
                                                                                                                      null,
                                                                                                                      classificationTab,
                                                                                                                      null))
                                                                                                          }
                                                                                                      }),
                                                                                                      null,
                                                                                                      classificationTab,
                                                                                                      {
                                                                                                          highlightTreeTableEntry(topAllocSite,
                                                                                                                                  treeTableView,
                                                                                                                                  false)
                                                                                                      } revertVia { treeTableView.selectionModel.clearSelection() }))

                                                                                          }
                                                                                      }),
                                                                                      null,
                                                                                      classificationTab,
                                                                                      {
                                                                                          highlightTreeTableEntry(topType,
                                                                                                                  treeTableView,
                                                                                                                  false)
                                                                                      } revertVia { treeTableView.selectionModel.clearSelection() })
                                                     )
                                                 }
                                             }),
                                             null,
                                             classificationTab,
                                             { highlightTreeTableEntry(zeroGcsSurvivedTreeItem, treeTableView, false) } revertVia { treeTableView.selectionModel.clearSelection() }))

/*            topGarbageMemoryAllocSite?.also {
                classificationTab.ideas.add(Idea("Reduce the amount of garbage collected memory",
                                                 Description("Over ")
                                                         .appendEmphasized("${(it.value.getByteCount(null) / totalByteCount * 100).toInt()}% ")
                                                         .appendDefault("of the total garbage collected memory was occupied by objects of type ")
                                                         .appendCode(it.parent.parent.value.key.toString())
                                                         .appendDefault(" allocated in method ")
                                                         .appendCode(it.value.key.toString())
                                                         .appendDefault(". ")
                                                         .linebreak()
                                                         .appendDefault("These objects did not survive a single garbage collection! ")
                                                         .linebreak()
                                                         .appendDefault("Expand this table entry to find out from where the allocating method was called."),
                                                 listOf("Expand call stack" does {
                                                     highlightTreeTableEntry(it, treeTableView, true)
                                                     treeTableView.expandRecursivelyFrom(it, true)
                                                 }),
                                                 null,
                                                 classificationTab,
                                                 { highlightTreeTableEntry(it, treeTableView, true) } revertVia { treeTableView.selectionModel.clearSelection() }))
            }*/

        } else if (treeTableView.classificationTree.classifiers.list.take(4) == classificationTab.info.allocSiteClassifierChain.list) {
            // classifiers are age -> alloc site -> type -> call sites
            /*val interestingByteCountTreeItems = treeTableView.filter {
                it.parent != null && it.parent.value.key.toString() == "0 GCs survived" && it.value.getByteCount(null) / totalByteCount >= 0.1
            }

            val topGarbageMemoryType = interestingByteCountTreeItems.maxBy { it.value.getByteCount(null) }
            val topGarbageObjectsType = interestingObjectCountTreeItems.maxBy { it.value.objectCount }

            topGarbageObjectsType?.also {
                classificationTab.ideas.add(Idea("Reduce the number of garbage collected objects",
                                                 Description("Over ")
                                                         .appendEmphasized("${(it.value.objectCount / totalObjectCount * 100).toInt()}% ")
                                                         .appendDefault("of all the garbage collected objects were allocated in method ")
                                                         .appendCode(it.parent.parent.value.key.toString())
                                                         .appendDefault(" and of type ")
                                                         .appendCode(it.value.key.toString())
                                                         .appendDefault(". ")
                                                         .linebreak()
                                                         .appendDefault("These objects did not survive a single garbage collection! ")
                                                         .linebreak()
                                                         .appendDefault("Expand this table entry to find out from where the allocating method was called."),
                                                 listOf("Expand call stack" does {
                                                     highlightTreeTableEntry(it, treeTableView, false)
                                                     treeTableView.expandRecursivelyFrom(it, true)
                                                 }),
                                                 null,
                                                 classificationTab,
                                                 { highlightTreeTableEntry(it, treeTableView, false) } revertVia { treeTableView.selectionModel.clearSelection() }))
            }
*/
/*            topGarbageMemoryType?.also {
                classificationTab.ideas.add(Idea("Reduce the amount of garbage collected memory",
                                                 Description("Over ")
                                                         .appendEmphasized("${(it.value.getByteCount(null) / totalByteCount * 100).toInt()}% ")
                                                         .appendDefault("of the total garbage collected memory was occupied by objects allocated in method ")
                                                         .appendCode(it.parent.parent.value.key.toString())
                                                         .appendDefault(" and of type ")
                                                         .appendCode(it.value.key.toString())
                                                         .appendDefault(". ")
                                                         .linebreak()
                                                         .appendDefault("These objects did not survive a single garbage collection! ")
                                                         .linebreak()
                                                         .appendDefault("Expand this table entry to find out from where the allocating method was called."),
                                                 listOf("Expand call stack" does {
                                                     highlightTreeTableEntry(it, treeTableView, true)
                                                     treeTableView.expandRecursivelyFrom(it, true)
                                                 }),
                                                 null,
                                                 classificationTab,
                                                 { highlightTreeTableEntry(it, treeTableView, true) } revertVia { treeTableView.selectionModel.clearSelection() }))
            }*/

        }
    }
}