
package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.component.table

import at.jku.anttracks.classification.nodes.ListGroupingNode
import at.jku.anttracks.diff.PermBornDiedTempGrouping
import at.jku.anttracks.gui.chart.base.ApplicationChartFactory
import at.jku.anttracks.gui.component.treetableview.AntTreeTableView
import at.jku.anttracks.gui.component.treetableview.AutomatedTreeItem
import at.jku.anttracks.gui.component.treetableview.cell.KeyTreeCell
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.model.PermBornDiedTempData
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.model.PermBornDiedTempInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.model.PlotablePermBornDiedTempData
import at.jku.anttracks.gui.model.Description
import at.jku.anttracks.gui.model.Percentage
import at.jku.anttracks.gui.model.ValueWithReference
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.value.ChangeListener
import javafx.collections.FXCollections
import javafx.scene.Node
import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuItem
import javafx.scene.control.TreeSortMode
import javafx.scene.control.TreeTableColumn
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.util.Callback
import java.math.BigDecimal
import java.util.*

class PermBornDiedTempTreeTableView : AntTreeTableView<PermBornDiedTempData>() {

    internal var plotInfo = PlotInfo()
    internal var displayMode = DisplayMode.Plotting

    private val titleFunction: (PermBornDiedTempGrouping) -> String = { c -> c.key.toString() }
    private val CHILD_FUNCTION: (PermBornDiedTempGrouping) -> Collection<PermBornDiedTempGrouping> = { c -> c.children }
    private val subTreeLevelFunction: (PermBornDiedTempGrouping) -> Int = { c -> c.subTreeLevel }
    // Root has no classifier, every other Grouping has a classifier
    private val iconFunction: (PermBornDiedTempGrouping) -> Node = { c ->
        val iconNodes = ArrayList<ImageView>()
        if (c.classifier != null) {
            var iconNode = info.heapEvolutionInfo.getDummyClassifier(c.classifier)!!.getIconNode(c.key)
            if (iconNode != null) {
                iconNodes.add(ImageView(iconNode.image))
            }
            if (c.subTreeLevel > 0) {
                var current = c.parent

                while (current.parent != null) {
                    if (current.parent.subTreeLevel != current.subTreeLevel) {
                        iconNode = info.heapEvolutionInfo.getDummyClassifier(current.classifier)!!.getIconNode(current.key)
                        if (iconNode != null) {
                            iconNodes.add(0, ImageView(iconNode.image))
                        }
                    }
                    current = current.parent
                }
            }
        }

        HBox(2.0, *iconNodes.toTypedArray())
    }
    private val dataFunction: (PermBornDiedTempGrouping) -> PermBornDiedTempData = { c ->
        val data = PermBornDiedTempData(c.key.toString(),
                                        (if (c.perm != null) c.perm.objectCount else 0).toDouble(),
                                        (if (c.born != null) c.born.objectCount else 0).toDouble(),
                                        (if (c.died != null) c.died.objectCount else 0).toDouble(),
                                        (if (c.temp != null) c.temp.objectCount else 0).toDouble(),
                                        (if (c.perm != null) c.perm.getByteCount(info.heapEvolutionInfo.startHeap) else 0).toDouble(),
                                        (if (c.born != null) c.born.getByteCount(if (c.born is ListGroupingNode) {
                                            info.currentIndexBasedHeap
                                        } else {
                                            null
                                        }) else 0).toDouble(),
                                        (if (c.died != null) c.died.getByteCount(info.heapEvolutionInfo.startHeap) else 0).toDouble(),
                                        (if (c.temp != null) c.temp.getByteCount(null) else 0).toDouble(),
                                        0)

        plotInfo.maxDiedObjects = Math.max(data.died.objects, plotInfo.maxDiedObjects)
        plotInfo.maxDiedBytes = Math.max(data.died.bytes, plotInfo.maxDiedBytes)
        plotInfo.maxAfterObjects = Math.max(data.after.objects, plotInfo.maxAfterObjects)
        plotInfo.maxAfterBytes = Math.max(data.after.bytes, plotInfo.maxAfterBytes)
        plotInfo.maxTempObjects = Math.max(data.temp.objects, plotInfo.maxTempObjects)
        plotInfo.maxTempBytes = Math.max(data.temp.bytes, plotInfo.maxTempBytes)

        data
    }

    internal var nameColumn = TreeTableColumn<PermBornDiedTempData, Description>("Name")

    internal var objectsColumn = TreeTableColumn<PermBornDiedTempData, ValueWithReference>("Objects")
    internal var beforeObjectsCountColumn = TreeTableColumn<PermBornDiedTempData, ValueWithReference>("Before")
    internal var permObjectsCountColumn = TreeTableColumn<PermBornDiedTempData, ValueWithReference>("Perm")
    internal var bornObjectsCountColumn = TreeTableColumn<PermBornDiedTempData, ValueWithReference>("Born")
    internal var diedObjectsCountColumn = TreeTableColumn<PermBornDiedTempData, ValueWithReference>("Died")
    internal var tempObjectsCountColumn = TreeTableColumn<PermBornDiedTempData, ValueWithReference>("Temp")
    internal var afterObjectsCountColumn = TreeTableColumn<PermBornDiedTempData, ValueWithReference>("After")

    internal var bytesColumn = TreeTableColumn<PermBornDiedTempData, ValueWithReference>("Bytes")
    internal var beforeBytesCountColumn = TreeTableColumn<PermBornDiedTempData, ValueWithReference>("Before")
    internal var permBytesCountColumn = TreeTableColumn<PermBornDiedTempData, ValueWithReference>("Perm")
    internal var bornBytesCountColumn = TreeTableColumn<PermBornDiedTempData, ValueWithReference>("Born")
    internal var diedBytesCountColumn = TreeTableColumn<PermBornDiedTempData, ValueWithReference>("Died")
    internal var tempBytesCountColumn = TreeTableColumn<PermBornDiedTempData, ValueWithReference>("Temp")
    internal var afterBytesCountColumn = TreeTableColumn<PermBornDiedTempData, ValueWithReference>("After")

    internal var diedPermBornObjectsPlottingColumn = TreeTableColumn<PermBornDiedTempData, PlotablePermBornDiedTempData>("Died / Perm / Born")
    internal var tempObjectsPlottingColumn = TreeTableColumn<PermBornDiedTempData, PlotablePermBornDiedTempData>("Temp")
    internal var diedPermBornBytesPlottingColumn = TreeTableColumn<PermBornDiedTempData, PlotablePermBornDiedTempData>("Died / Perm / Born")
    internal var tempBytesPlottingColumn = TreeTableColumn<PermBornDiedTempData, PlotablePermBornDiedTempData>("Temp")

    private lateinit var info: PermBornDiedTempInfo

    enum class DisplayMode {
        Plotting,
        Metric
    }

    class PlotInfo {
        var maxDiedObjects = 0.0
        var maxDiedBytes = 0.0
        var maxAfterObjects = 0.0
        var maxAfterBytes = 0.0
        var maxTempObjects = 0.0
        var maxTempBytes = 0.0
    }

    init {
        FXMLUtil.load(this, PermBornDiedTempTreeTableView::class.java)
    }

    fun init(info: PermBornDiedTempInfo, showTTVOnScreen: Runnable) {
        super.init(showTTVOnScreen, java.util.function.Function { permBornDiedTempData -> permBornDiedTempData.id })
        this.prefHeight = 0.0  // fixes cutoff in configuration pane - tree table shrinks in favor of classifier selection

        this.info = info

        val entry = MenuItem("Switch display mode", Consts.EXCHANGE_PACK.asNewNode)
        entry.setOnAction { ae ->
            when (displayMode) {
                DisplayMode.Plotting -> {
                    displayMode = DisplayMode.Metric
                    initColumns()
                }
                DisplayMode.Metric -> {
                    displayMode = DisplayMode.Plotting
                    initColumns()
                }
            }
        }
        contextMenu = ContextMenu(entry)

        initColumns()
    }

    private fun initColumns() {
        when (displayMode) {
            DisplayMode.Plotting -> initPlottingView()
            DisplayMode.Metric -> initMetricView()
        }

        // Ensure that cell factories are correct after column change
        val columns = FXCollections.observableArrayList(nameColumn,
                                                        objectsColumn,
                                                        beforeObjectsCountColumn,
                                                        permObjectsCountColumn,
                                                        bornObjectsCountColumn,
                                                        diedObjectsCountColumn,
                                                        tempObjectsCountColumn,
                                                        afterObjectsCountColumn,
                                                        bytesColumn,
                                                        beforeBytesCountColumn,
                                                        permBytesCountColumn,
                                                        bornBytesCountColumn,
                                                        diedBytesCountColumn,
                                                        tempBytesCountColumn,
                                                        afterBytesCountColumn,
                                                        diedPermBornObjectsPlottingColumn,
                                                        tempObjectsPlottingColumn,
                                                        diedPermBornBytesPlottingColumn,
                                                        tempBytesCountColumn)
        setDefaultCellFactories(columns)
        setDefaultComparators(columns)
    }

    fun initPlottingView() {
        // Clear bindings
        nameColumn.prefWidthProperty().unbind()

        objectsColumn.prefWidthProperty().unbind()
        diedPermBornObjectsPlottingColumn.prefWidthProperty().unbind()
        tempObjectsPlottingColumn.prefWidthProperty().unbind()

        bytesColumn.prefWidthProperty().unbind()
        diedPermBornBytesPlottingColumn.prefWidthProperty().unbind()
        tempBytesPlottingColumn.prefWidthProperty().unbind()

        columns.clear()
        // Define columns
        nameColumn.setCellValueFactory { param -> ReadOnlyObjectWrapper(Description(param.value.value.id)) }
        nameColumn.cellFactory = Callback { KeyTreeCell() }

        diedPermBornObjectsPlottingColumn.setCellValueFactory { param ->
            ReadOnlyObjectWrapper(PlotablePermBornDiedTempData(param.value.value,
                                                               plotInfo,
                                                               ApplicationChartFactory
                                                                       .MemoryConsumptionUnit.OBJECTS,
                                                               PlotablePermBornDiedTempData.PlotStyle.PermDiedBorn))
        }
        tempObjectsPlottingColumn.setCellValueFactory { param ->
            ReadOnlyObjectWrapper(PlotablePermBornDiedTempData(param.value.value,
                                                               plotInfo,
                                                               ApplicationChartFactory.MemoryConsumptionUnit.OBJECTS,
                                                               PlotablePermBornDiedTempData.PlotStyle.Temp))
        }
        diedPermBornBytesPlottingColumn.setCellValueFactory { param ->
            ReadOnlyObjectWrapper(PlotablePermBornDiedTempData(param.value.value,
                                                               plotInfo,
                                                               ApplicationChartFactory.MemoryConsumptionUnit
                                                                       .BYTES,
                                                               PlotablePermBornDiedTempData.PlotStyle.PermDiedBorn))
        }
        tempBytesPlottingColumn.setCellValueFactory { param ->
            ReadOnlyObjectWrapper(PlotablePermBornDiedTempData(param.value.value,
                                                               plotInfo,
                                                               ApplicationChartFactory.MemoryConsumptionUnit.BYTES,
                                                               PlotablePermBornDiedTempData.PlotStyle.Temp))
        }

        objectsColumn.columns.clear()
        objectsColumn.columns.add(diedPermBornObjectsPlottingColumn)
        objectsColumn.columns.add(tempObjectsPlottingColumn)

        bytesColumn.columns.clear()
        bytesColumn.columns.add(diedPermBornBytesPlottingColumn)
        bytesColumn.columns.add(tempBytesPlottingColumn)

        columns.add(nameColumn)
        columns.add(objectsColumn)
        columns.add(bytesColumn)

        nameColumn.prefWidthProperty().bind(widthProperty().multiply(6).divide(12) /* aaaand subtract scroll bar width, otherwise binding properties to width does not
                                       work at all */.subtract(20)) // = 11/15

        objectsColumn.prefWidthProperty().bind(widthProperty().multiply(3).divide(12))
        diedPermBornObjectsPlottingColumn.prefWidthProperty().bind(objectsColumn.widthProperty().multiply(3).divide(4))
        tempObjectsPlottingColumn.prefWidthProperty().bind(objectsColumn.widthProperty().multiply(1).divide(4))

        bytesColumn.prefWidthProperty().bind(widthProperty().multiply(3).divide(12))
        diedPermBornBytesPlottingColumn.prefWidthProperty().bind(objectsColumn.widthProperty().multiply(3).divide(4))
        tempBytesPlottingColumn.prefWidthProperty().bind(objectsColumn.widthProperty().multiply(1).divide(4))
    }

    fun initMetricView() {
        // Clear bindings
        nameColumn.prefWidthProperty().unbind()

        objectsColumn.prefWidthProperty().unbind()
        beforeObjectsCountColumn.prefWidthProperty().unbind()
        permObjectsCountColumn.prefWidthProperty().unbind()
        bornObjectsCountColumn.prefWidthProperty().unbind()
        diedObjectsCountColumn.prefWidthProperty().unbind()
        tempObjectsCountColumn.prefWidthProperty().unbind()
        afterObjectsCountColumn.prefWidthProperty().unbind()

        bytesColumn.prefWidthProperty().unbind()
        beforeBytesCountColumn.prefWidthProperty().unbind()
        permBytesCountColumn.prefWidthProperty().unbind()
        bornBytesCountColumn.prefWidthProperty().unbind()
        diedBytesCountColumn.prefWidthProperty().unbind()
        tempBytesCountColumn.prefWidthProperty().unbind()
        afterBytesCountColumn.prefWidthProperty().unbind()

        columns.clear()
        // Define columns
        nameColumn.setCellValueFactory { param -> ReadOnlyObjectWrapper(Description(param.value.value.id)) }
        nameColumn.cellFactory = Callback { KeyTreeCell() }

        beforeObjectsCountColumn.setCellValueFactory { param ->
            ReadOnlyObjectWrapper(ValueWithReference(param.value.value.before.objects,
                                                     getParentDiffGrouping(param.value as AutomatedTreeItem<PermBornDiedTempGrouping, PermBornDiedTempData>).before.objects))
        }
        permObjectsCountColumn.setCellValueFactory { param ->
            ReadOnlyObjectWrapper(ValueWithReference(param.value.value.perm.objects,
                                                     getParentDiffGrouping(param.value as AutomatedTreeItem<PermBornDiedTempGrouping, PermBornDiedTempData>).perm.objects))
        }
        bornObjectsCountColumn.setCellValueFactory { param ->
            ReadOnlyObjectWrapper(ValueWithReference(param.value.value.born.objects,
                                                     getParentDiffGrouping(param.value as AutomatedTreeItem<PermBornDiedTempGrouping, PermBornDiedTempData>).born.objects))
        }
        diedObjectsCountColumn.setCellValueFactory { param ->
            ReadOnlyObjectWrapper(ValueWithReference(param.value.value.died.objects,
                                                     getParentDiffGrouping(param.value as AutomatedTreeItem<PermBornDiedTempGrouping, PermBornDiedTempData>).died.objects))
        }
        tempObjectsCountColumn.setCellValueFactory { param ->
            ReadOnlyObjectWrapper(ValueWithReference(param.value.value.temp.objects,
                                                     getParentDiffGrouping(param.value as AutomatedTreeItem<PermBornDiedTempGrouping, PermBornDiedTempData>).temp.objects))
        }
        afterObjectsCountColumn.setCellValueFactory { param ->
            ReadOnlyObjectWrapper(ValueWithReference(param.value.value.after.objects,
                                                     getParentDiffGrouping(param.value as AutomatedTreeItem<PermBornDiedTempGrouping, PermBornDiedTempData>).after.objects))
        }

        beforeBytesCountColumn.setCellValueFactory { param ->
            ReadOnlyObjectWrapper(ValueWithReference(param.value.value.before.bytes,
                                                     getParentDiffGrouping(param.value as AutomatedTreeItem<PermBornDiedTempGrouping, PermBornDiedTempData>).before.bytes))
        }
        permBytesCountColumn.setCellValueFactory { param ->
            ReadOnlyObjectWrapper(ValueWithReference(param.value.value.perm.bytes,
                                                     getParentDiffGrouping(param.value as AutomatedTreeItem<PermBornDiedTempGrouping, PermBornDiedTempData>).perm
                                                             .bytes))
        }
        bornBytesCountColumn.setCellValueFactory { param ->
            ReadOnlyObjectWrapper(ValueWithReference(param.value.value.born.bytes,
                                                     getParentDiffGrouping(param.value as AutomatedTreeItem<PermBornDiedTempGrouping, PermBornDiedTempData>).born
                                                             .bytes))
        }
        diedBytesCountColumn.setCellValueFactory { param ->
            ReadOnlyObjectWrapper(ValueWithReference(param.value.value.died.bytes,
                                                     getParentDiffGrouping(param.value as AutomatedTreeItem<PermBornDiedTempGrouping, PermBornDiedTempData>).died
                                                             .bytes))
        }
        tempBytesCountColumn.setCellValueFactory { param ->
            ReadOnlyObjectWrapper(ValueWithReference(param.value.value.temp.bytes,
                                                     getParentDiffGrouping(param.value as AutomatedTreeItem<PermBornDiedTempGrouping, PermBornDiedTempData>).temp
                                                             .bytes))
        }
        afterBytesCountColumn.setCellValueFactory { param ->
            ReadOnlyObjectWrapper(ValueWithReference(param.value.value.after.bytes,
                                                     getParentDiffGrouping(param.value as AutomatedTreeItem<PermBornDiedTempGrouping, PermBornDiedTempData>).after.bytes))
        }

        objectsColumn.columns.clear()
        objectsColumn.columns.add(beforeObjectsCountColumn)
        objectsColumn.columns.add(permObjectsCountColumn)
        objectsColumn.columns.add(bornObjectsCountColumn)
        objectsColumn.columns.add(diedObjectsCountColumn)
        objectsColumn.columns.add(tempObjectsCountColumn)
        objectsColumn.columns.add(afterObjectsCountColumn)

        bytesColumn.columns.clear()
        bytesColumn.columns.add(beforeBytesCountColumn)
        bytesColumn.columns.add(permBytesCountColumn)
        bytesColumn.columns.add(bornBytesCountColumn)
        bytesColumn.columns.add(diedBytesCountColumn)
        bytesColumn.columns.add(tempBytesCountColumn)
        bytesColumn.columns.add(afterBytesCountColumn)

        columns.add(nameColumn)
        columns.add(objectsColumn)
        columns.add(bytesColumn)

        // Define column widths
        nameColumn.prefWidthProperty().bind(widthProperty().multiply(4).divide(12) /* aaaand subtract scroll bar width, otherwise binding properties to width does not
                                       work at all */.subtract(20)) // = 11/15

        objectsColumn.prefWidthProperty().bind(widthProperty().multiply(4).divide(12))
        beforeObjectsCountColumn.prefWidthProperty().bind(objectsColumn.widthProperty().divide(6))
        permObjectsCountColumn.prefWidthProperty().bind(objectsColumn.widthProperty().divide(6))
        bornObjectsCountColumn.prefWidthProperty().bind(objectsColumn.widthProperty().divide(6))
        diedObjectsCountColumn.prefWidthProperty().bind(objectsColumn.widthProperty().divide(6))
        tempObjectsCountColumn.prefWidthProperty().bind(objectsColumn.widthProperty().divide(6))
        afterObjectsCountColumn.prefWidthProperty().bind(objectsColumn.widthProperty().divide(6))

        bytesColumn.prefWidthProperty().bind(widthProperty().multiply(4).divide(12))
        beforeBytesCountColumn.prefWidthProperty().bind(bytesColumn.widthProperty().divide(6))
        permBytesCountColumn.prefWidthProperty().bind(bytesColumn.widthProperty().divide(6))
        bornBytesCountColumn.prefWidthProperty().bind(bytesColumn.widthProperty().divide(6))
        diedBytesCountColumn.prefWidthProperty().bind(bytesColumn.widthProperty().divide(6))
        tempBytesCountColumn.prefWidthProperty().bind(bytesColumn.widthProperty().divide(6))
        afterBytesCountColumn.prefWidthProperty().bind(bytesColumn.widthProperty().divide(6))
    }

    fun getPercentage(total: Double, x: Double): Percentage {
        return if (total == 0.0 || x == 0.0) {
            Percentage(0.0)
        } else Percentage(BigDecimal(x / total * 100).setScale(1, BigDecimal.ROUND_HALF_UP).toDouble())
    }

    fun setLoadingMode(isLoading: Boolean) {
        isDisabled = isLoading
    }

    private fun getParentDiffGrouping(item: AutomatedTreeItem<PermBornDiedTempGrouping, PermBornDiedTempData>?): PermBornDiedTempData {
        var currentItem = item
        val subTreeLevel = currentItem!!.subTreeLevel
        var lastMatching: AutomatedTreeItem<PermBornDiedTempGrouping, PermBornDiedTempData>? = null
        while (currentItem != null) {
            if (currentItem.subTreeLevel == subTreeLevel) {
                lastMatching = currentItem
            }
            currentItem = if (currentItem.parent != null) {
                currentItem.parent as AutomatedTreeItem<PermBornDiedTempGrouping, PermBornDiedTempData>?
            } else {
                null
            }
        }
        return lastMatching!!.value
    }

    fun setRoot(grouping: PermBornDiedTempGrouping, preserveSortOrder: Boolean = true) {
        // Reset plotting info
        plotInfo = PlotInfo()
        // Remember expansion
        rememberExpansion()
        // Remember sorting
        val rememberedSortOrder = ArrayList<TreeTableColumn<PermBornDiedTempData, *>>()
        rememberedSortOrder.addAll(this.sortOrder)
        val rememberedSortMode = sortMode
        val rememberedSortTypes = ArrayList<TreeTableColumn.SortType>()
        rememberedSortOrder.forEach { col -> rememberedSortTypes.add(col.sortType) }

        // update table root
        root = AutomatedTreeItem(grouping,
                                 titleFunction,
                                 dataFunction,
                                 CHILD_FUNCTION,
                                 subTreeLevelFunction,
                                 iconFunction,
                                 ChangeListener { _, _, newVal ->
                                     if (newVal) {
                                         sort()
                                     }
                                 })
        restoreExpansion()
        isShowRoot = true
        root.isExpanded = true

        // Sorting
        if (preserveSortOrder) {
            // restore sorting
            sortOrder.clear()
            sortMode = rememberedSortMode
            for (i in rememberedSortOrder.indices) {
                rememberedSortOrder.get(i).sortType = rememberedSortTypes.get(i)
            }
            sortOrder.addAll(rememberedSortOrder)
        } else {
            // standard sorting
            sortOrder.clear()
            sortMode = TreeSortMode.ALL_DESCENDANTS
            when (displayMode) {
                DisplayMode.Plotting -> {
                    diedPermBornObjectsPlottingColumn.sortType = TreeTableColumn.SortType.DESCENDING
                    sortOrder.add(diedPermBornObjectsPlottingColumn)
                }
                DisplayMode.Metric -> {
                    afterObjectsCountColumn.sortType = TreeTableColumn.SortType.DESCENDING
                    sortOrder.add(afterObjectsCountColumn)
                }
            }
        }
    }

    fun sortByBytes() {
        val sortColum = if (sortOrder.isEmpty()) null else sortOrder[0]
        if (sortColum == null || sortColum !== diedPermBornBytesPlottingColumn || sortColum.sortType != TreeTableColumn.SortType.DESCENDING) {
            // sort by retained size
            sortOrder.clear()
            sortOrder.add(diedPermBornBytesPlottingColumn)
            sortOrder[0].sortType = TreeTableColumn.SortType.DESCENDING
            sortOrder[0].isSortable = true
            sort()
        }
    }
}
