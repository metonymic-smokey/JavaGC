package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.component.tree

import at.jku.anttracks.classification.nodes.GroupingNode
import at.jku.anttracks.gui.component.treetableview.cell.SampledValueWithReferenceTreeCell
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.model.ShortLivedObjectsInfo
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.component.classificationtreetableview.ClassificationTreeTableView
import at.jku.anttracks.gui.model.SampledValueWithReference
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.gui.utils.toShortMemoryUsageString
import at.jku.anttracks.util.toString
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleLongProperty
import javafx.scene.control.TreeSortMode
import javafx.scene.control.TreeTableColumn
import kotlin.math.roundToLong

class ShortLivedObjectsClassificationTreeTableView : ClassificationTreeTableView() {

    private val collectedObjectsCountColumn = TreeTableColumn<GroupingNode, SampledValueWithReference>("Collected objects")

    private val collectedMemoryCountColumn = TreeTableColumn<GroupingNode, SampledValueWithReference>("Collected memory")

    private val collectedObjectsPerGCCountColumn = TreeTableColumn<GroupingNode, SampledValueWithReference>("Collected objects per GC")

    private val collectedMemoryPerGCCountColumn = TreeTableColumn<GroupingNode, SampledValueWithReference>("Collected memory per GC")

    // TODO add more columns with interesting metrics?

    init {
        FXMLUtil.load(this, ShortLivedObjectsClassificationTreeTableView::class.java)
    }

    fun init(info: ShortLivedObjectsInfo) {
        // local classification or object inspection is not allowed in this tree table, thus the function parameters are not needed
        super.init(info.heapEvolutionInfo.appInfo, info.heapEvolutionInfo, null, null, null, null, null)

        // total collected objects...
        collectedObjectsCountColumn.setCellFactory { SampledValueWithReferenceTreeCell { value -> value.roundToLong().toString("%,d") } }
        collectedObjectsCountColumn.setCellValueFactory {
            getSampledValueWithReferenceCVF(
                    SimpleDoubleProperty(it.value.value.objectCount.toDouble()),
                    SimpleDoubleProperty(getParent(it.value.value).objectCount.toDouble()),
                    it.value.value.isCalculatedOnSampling,
                    SimpleDoubleProperty(it.value.value.nonSampledObjectCount.toDouble()))
        }

        collectedMemoryCountColumn.setCellFactory { SampledValueWithReferenceTreeCell { value -> toShortMemoryUsageString(value.toLong()) } }
        collectedMemoryCountColumn.setCellValueFactory {
            getSampledValueWithReferenceCVF(
                    SimpleLongProperty(it.value.value.getByteCount(null)),
                    SimpleLongProperty(getParent(it.value.value).getByteCount(null)),
                    it.value.value.isCalculatedOnSampling,
                    SimpleDoubleProperty(it.value.value.getNonSampledByteCount(null).toDouble()))
        }

        // collected objects per gc...
        collectedObjectsPerGCCountColumn.setCellFactory { SampledValueWithReferenceTreeCell { value -> value.roundToLong().toString("%,d") } }
        collectedObjectsPerGCCountColumn.setCellValueFactory {
            // lets not show object counts = 0 in the table...
            getSampledValueWithReferenceCVF(
                    SimpleDoubleProperty(it.value.value.objectCount / Math.max(1.0, info.completedGCsCount.toDouble())),
                    SimpleDoubleProperty(getParent(it.value.value).objectCount / Math.max(1.0, info.completedGCsCount.toDouble())),
                    it.value.value.isCalculatedOnSampling,
                    null)
        }

        collectedMemoryPerGCCountColumn.setCellFactory { SampledValueWithReferenceTreeCell(false) { value -> toShortMemoryUsageString(value.toLong()) } }
        collectedMemoryPerGCCountColumn.setCellValueFactory {
            getSampledValueWithReferenceCVF(
                    SimpleDoubleProperty(it.value.value.getByteCount(null) / Math.max(1.0, info.completedGCsCount.toDouble())),
                    SimpleDoubleProperty(getParent(it.value.value).getByteCount(null) / Math.max(1.0, info.completedGCsCount.toDouble())),
                    it.value.value.isCalculatedOnSampling,
                    null)

        }

        // show only name and gc columns
        columns.clear()
        columns.addAll(nameColumn, collectedObjectsCountColumn, collectedMemoryCountColumn, collectedObjectsPerGCCountColumn, collectedMemoryPerGCCountColumn)
        columns.forEach { it.isVisible = true }

        // set widths
        nameColumn.prefWidthProperty().bind(widthProperty().multiply(6).divide(10).subtract(20))
        collectedObjectsCountColumn.prefWidthProperty().bind(widthProperty().multiply(2).divide(10))
        collectedMemoryCountColumn.prefWidthProperty().bind(widthProperty().multiply(2).divide(10))
        collectedObjectsPerGCCountColumn.prefWidthProperty().bind(widthProperty().multiply(2).divide(10))
        collectedMemoryPerGCCountColumn.prefWidthProperty().bind(widthProperty().multiply(2).divide(10))

        // sort by number of collected objects per gc
        collectedObjectsCountColumn.sortType = TreeTableColumn.SortType.DESCENDING
        collectedMemoryCountColumn.sortType = TreeTableColumn.SortType.DESCENDING
        collectedObjectsPerGCCountColumn.sortType = TreeTableColumn.SortType.DESCENDING
        collectedMemoryPerGCCountColumn.sortType = TreeTableColumn.SortType.DESCENDING
        sortMode = TreeSortMode.ALL_DESCENDANTS
    }

    override fun applyDefaultSortingSettings() {
        sortByBytes()
    }

    fun getMatchingTopLevelItems(name: String) = root.children.filter { it.value.key.toString().contains(name) }

    fun sortByObjects() {
        sortOrder.setAll(collectedObjectsCountColumn)
    }

    fun sortByBytes() {
        sortOrder.setAll(collectedMemoryCountColumn)
    }
}