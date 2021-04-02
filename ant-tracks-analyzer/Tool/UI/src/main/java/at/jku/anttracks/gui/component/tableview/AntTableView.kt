package at.jku.anttracks.gui.component.tableview

import at.jku.anttracks.gui.component.tableview.cell.*
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.model.PlotablePermBornDiedTempData
import com.sun.javafx.scene.control.skin.TableViewSkin
import com.sun.xml.internal.ws.spi.db.BindingContextFactory.LOGGER
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.scene.control.TableCell
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.TreeTableColumn
import javafx.util.Callback
import java.lang.reflect.Method
import java.util.*

open class AntTableView<T> : TableView<T>() {
    init {
        // this.selectionModel.selectionMode = SelectionMode.MULTIPLE

        // Default cell factories and default sorting
        val initializationListener = object : ListChangeListener<T> {
            override fun onChanged(c: ListChangeListener.Change<out T>?) {
                if (c != null) {
                    while (c.next()) {
                        if (c.wasAdded()) {
                            setDefaultCellFactories(columns)
                            setDefaultSorting(columns)
                            items.removeListener(this)
                            return
                        }
                    }
                }
            }
        }
        items.addListener(initializationListener)

        // Listener to react to new items to fit column width to content width
        // TODO: Fix fit to content width
        /*
        items.addListener(ListChangeListener<T> {
            if (it != null && columns != null && columns.size > 0 && skin != null) {
                while (it.next()) {
                    if (it.wasAdded() || it.wasRemoved()) {
                        Platform.runLater {
                            for (column in columns) {
                                try {
                                    columnToFitMethod?.invoke(skin, column, -1)
                                } catch (e: IllegalAccessException) {
                                    e.printStackTrace()
                                } catch (e: InvocationTargetException) {
                                    e.printStackTrace()
                                } catch (e: NullPointerException) {
                                    // TODO This should not happen -.-
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }
            }
        })
        */
    }

    /**
     * Sets AntTracks's default cell visualizations (e.g., bars for percentages)
     *
     * @param columns the table's columns
     */
    protected fun setDefaultCellFactories(cols: List<TableColumn<T, *>>) {
        for (column in cols) {
            // Check if cell factory is still default type and has not been changed by dev
            if (column.cellFactory.javaClass.name.startsWith("javafx.scene.control.TableColumn$")) {
                // Exclude grouping columns
                if (column.getCellData(0) != null) {
                    val defaultFactory: Callback<TableColumn<T, out Any>, TableCell<T, out Any>>? = when (column.getCellData(0).javaClass.simpleName) {
                        "PlotablePermBornDiedTempData", "OldPlotableDiffingData" -> Callback { PlotablePermBornDiedTempDataTableCell<T>() }
                        "ValueWithReference" -> Callback { ValueWithReferenceTableCell<T>() }
                        "SampledValueWithReference" -> Callback { SampledValueWithReferenceTableCell(true) }
                        "ApproximateDouble" -> Callback { ApproximateDoubleTableCell<T>() }
                        "Percentage" -> Callback { PercentageTableCell<T>() }
                        // TODO: Every number gets formatted as memory cell. This is a strange default behavior.
                        //"Double", "Integer", "Long", "Number" -> {
                        //    Callback { MemoryTableCell<T>() }
                        //}
                        "Color" -> Callback { ColorTableCell<T>() }
                        else -> null
                    }
                    if (defaultFactory != null) {
                        column.cellFactory = defaultFactory
                    }

                } else {
                    LOGGER.warning("Could not determine column type for column " + column.text)
                }
            }

            setDefaultCellFactories(column.columns)
        }
    }

    protected fun setDefaultSorting(cols: ObservableList<TableColumn<T, *>>) {
        for (column in cols) {
            // Check if comperator is still default type and has not been changed by dev
            if (column.comparator.javaClass.name.startsWith("javafx.scene.control.TableColumnBase")) {
                // Exclude grouping columns
                if (column.getCellData(0) != null) {
                    when (column.getCellData(0).javaClass.simpleName) {
                        "PlotablePermBornDiedTempData", "OldPlotableDiffingData" -> (column as TreeTableColumn<T, PlotablePermBornDiedTempData>)
                                .setComparator(Comparator.comparingDouble { value ->
                                    if (value.plotStyle === PlotablePermBornDiedTempData.PlotStyle.PermDiedBorn)
                                        value.after
                                    else
                                        value.temp
                                })
                    }
                }
            }

            setDefaultSorting(column.columns)
        }
    }

    companion object {
        private val columnToFitMethod: Method?
            get() = TableViewSkin::class.java.getDeclaredMethod("resizeColumnToFitContent", TableColumn::class.java, Int::class.javaPrimitiveType)
                    .apply { isAccessible = true }
    }
}