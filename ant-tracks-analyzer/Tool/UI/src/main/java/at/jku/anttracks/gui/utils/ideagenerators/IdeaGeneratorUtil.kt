package at.jku.anttracks.gui.utils.ideagenerators

import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.nodes.GroupingNode
import at.jku.anttracks.classification.nodes.ListGroupingNode
import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.component.classificationtreetableview.ClassificationTreeTableView
import at.jku.anttracks.util.ThreadUtil

object IdeaGeneratorUtil {
    fun performLocalClassification(nodeToClassify: GroupingNode,
                                   classifiers: ClassifierChain,
                                   treeTableView: ClassificationTreeTableView,
                                   classificationTab: ApplicationBaseTab,
                                   nextAnalysisStep: (() -> Unit)? = null) {
        val treeItemToClassify = treeTableView.getTreeItem(nodeToClassify)
        val classificationTask = treeTableView.localClassificationTask(treeItemToClassify,
                                                                       treeItemToClassify.value as ListGroupingNode,
                                                                       classifiers,
                                                                       -1,
                                                                       { nextAnalysisStep?.invoke() })
        classificationTab.tasks.add(classificationTask)
        ThreadUtil.startTask(classificationTask)
    }
}