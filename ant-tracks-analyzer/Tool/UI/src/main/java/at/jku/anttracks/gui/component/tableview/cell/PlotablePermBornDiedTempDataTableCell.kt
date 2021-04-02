package at.jku.anttracks.gui.component.tableview.cell

import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.diffing.PermBornDiedTempStackedAreaJFreeChartPane
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.model.PlotablePermBornDiedTempData
import com.sun.javafx.scene.control.skin.Utils
import javafx.beans.binding.ObjectBinding
import javafx.beans.value.ChangeListener
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.OverrunStyle
import javafx.scene.control.Tooltip
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.Pane
import javafx.scene.layout.RowConstraints
import javafx.scene.paint.Color
import javafx.scene.text.Font

import java.lang.reflect.Method
import java.text.DecimalFormat

class PlotablePermBornDiedTempDataTableCell<T> : AntTableCell<T, PlotablePermBornDiedTempData>() {

    private var tempColumnConstraint: ColumnConstraints = ColumnConstraints().apply { percentWidth = 20.0 }
    private var leftEmptyColumnConstraint: ColumnConstraints = ColumnConstraints().apply { percentWidth = 20.0 }
    private var diedColumnConstraint: ColumnConstraints = ColumnConstraints().apply { percentWidth = 20.0 }
    private var permColumnConstraint: ColumnConstraints = ColumnConstraints().apply { percentWidth = 20.0 }
    private var bornColumnConstraint: ColumnConstraints = ColumnConstraints().apply { percentWidth = 20.0 }
    private var rightEmptyColumnConstraint: ColumnConstraints = ColumnConstraints().apply { percentWidth = 20.0 }

    private val diedLabel: Label = Label()
    private val permLabel: Label = Label()
    private val bornLabel: Label = Label()
    private val tempLabel: Label = Label()

    private val tempPane: Pane = Pane().apply {
        GridPane.setColumnIndex(this, 0)
        GridPane.setRowIndex(this, 0)
        GridPane.setColumnSpan(this, 1)
        children.add(tempLabel)
    }
    private val diedPane: Pane = Pane().apply {
        GridPane.setColumnIndex(this, 2)
        GridPane.setRowIndex(this, 0)
        GridPane.setColumnSpan(this, 1)
        children.add(diedLabel)
    }
    private val permPane: Pane = Pane().apply {
        GridPane.setColumnIndex(this, 3)
        GridPane.setRowIndex(this, 0)
        GridPane.setColumnSpan(this, 1)
        children.add(permLabel)
    }
    private val bornPane: Pane = Pane().apply {
        GridPane.setColumnIndex(this, 4)
        GridPane.setRowIndex(this, 0)
        GridPane.setColumnSpan(this, 1)
        children.add(bornLabel)
    }

    /*
    <GridPane fx:id="mainPane">
            <Pane fx:id="tempPane" GridPane.columnIndex="0" GridPane.rowIndex="0" GridPane.columnSpan="1">
                <Label fx:id="tempLabel"></Label>
            </Pane>
            <Pane fx:id="diedPane" GridPane.columnIndex="2" GridPane.rowIndex="0" GridPane.columnSpan="1">
                <Label fx:id="diedLabel"></Label>
            </Pane>
            <Pane fx:id="permPane" GridPane.columnIndex="3" GridPane.rowIndex="0" GridPane.columnSpan="1">
                <Label fx:id="permLabel"></Label>
            </Pane>
            <Pane fx:id="bornPane" GridPane.columnIndex="4" GridPane.rowIndex="0" GridPane.columnSpan="1">
                <Label fx:id="bornLabel"></Label>
            </Pane>
        </GridPane>
     */
    override val node: Node = GridPane().apply {
        columnConstraints.addAll(tempColumnConstraint, leftEmptyColumnConstraint, diedColumnConstraint, permColumnConstraint, bornColumnConstraint, rightEmptyColumnConstraint)
        rowConstraints.add(RowConstraints().apply { percentHeight = 100.0 })
        children.addAll(
                tempPane,
                diedPane,
                permPane,
                bornPane
        )
    }

    private val diedWidthListener: ChangeListener<in Number> = ChangeListener { observableValue, oldV, newV ->
        if (newV.toDouble() > 0 && item != null) {
            setClippedText(diedLabel, item!!.died)
        }
    }

    private val permWidthListener: ChangeListener<in Number> = ChangeListener { observableValue, oldV, newV ->
        if (newV.toDouble() > 0 && item != null) {
            setClippedText(permLabel, item!!.perm)
        }
    }

    private val bornWidthListener: ChangeListener<in Number> = ChangeListener { observableValue, oldV, newV ->
        if (newV.toDouble() > 0 && item != null) {
            setClippedText(bornLabel, item!!.born)
        }
    }

    private val tempWidthListener: ChangeListener<in Number> = ChangeListener { observableValue, oldV, newV ->
        if (newV.toDouble() > 0 && item != null) {
            setClippedText(tempLabel, item!!.temp)
        }
    }

    private var item: PlotablePermBornDiedTempData? = null

    init {
        diedLabel.maxWidthProperty().bind(diedPane.widthProperty())
        permLabel.maxWidthProperty().bind(permPane.widthProperty())
        bornLabel.maxWidthProperty().bind(bornPane.widthProperty())
        tempLabel.maxWidthProperty().bind(tempPane.widthProperty())

        diedLabel.textOverrunProperty().set(OverrunStyle.CLIP)
        permLabel.textOverrunProperty().set(OverrunStyle.CLIP)
        bornLabel.textOverrunProperty().set(OverrunStyle.CLIP)
        tempLabel.textOverrunProperty().set(OverrunStyle.CLIP)

        diedPane.style = "-fx-background-color: " + hexColor(DIED_COLOR)
        permPane.style = "-fx-background-color: " + hexColor(PERM_COLOR)
        bornPane.style = "-fx-background-color: " + hexColor(BORN_COLOR)
        tempPane.style = "-fx-background-color: " + hexColor(TEMP_COLOR)

        diedPane.widthProperty().addListener(diedWidthListener)
        permPane.widthProperty().addListener(permWidthListener)
        bornPane.widthProperty().addListener(bornWidthListener)
        tempPane.widthProperty().addListener(tempWidthListener)
    }

    override fun updateNode(item: PlotablePermBornDiedTempData) {
        this.item = item

        when (item.plotStyle) {
            PlotablePermBornDiedTempData.PlotStyle.PermDiedBorn -> {
                val full = item.maxDied + item.maxAfter
                val leftEmpty = (item.maxDied - item.died) / full
                val died = item.died / full
                val perm = item.perm / full
                val born = item.born / full
                val rightEmpty = (item.maxAfter - item.after) / full

                // Reset
                tempColumnConstraint.percentWidth = 100.0
                leftEmptyColumnConstraint.percentWidth = 100.0
                diedColumnConstraint.percentWidth = 100.0
                permColumnConstraint.percentWidth = 100.0
                bornColumnConstraint.percentWidth = 100.0
                rightEmptyColumnConstraint.percentWidth = 100.0

                // Set
                tempColumnConstraint.percentWidth = 0.0
                leftEmptyColumnConstraint.percentWidth = leftEmpty * 100
                diedColumnConstraint.percentWidth = died * 100
                permColumnConstraint.percentWidth = perm * 100
                bornColumnConstraint.percentWidth = born * 100
                rightEmptyColumnConstraint.percentWidth = rightEmpty * 100
            }
            PlotablePermBornDiedTempData.PlotStyle.Temp -> {
                val temp = item.temp / item.maxTemp

                // Reset
                tempColumnConstraint.percentWidth = 100.0
                leftEmptyColumnConstraint.percentWidth = 100.0
                diedColumnConstraint.percentWidth = 100.0
                permColumnConstraint.percentWidth = 100.0
                bornColumnConstraint.percentWidth = 100.0
                rightEmptyColumnConstraint.percentWidth = 100.0

                // Set
                tempColumnConstraint.percentWidth = temp * 100
                leftEmptyColumnConstraint.percentWidth = 0.0
                diedColumnConstraint.percentWidth = 0.0
                permColumnConstraint.percentWidth = 0.0
                bornColumnConstraint.percentWidth = 0.0
                rightEmptyColumnConstraint.percentWidth = 0.0
            }
        }
    }

    override fun calculateTooltip(item: PlotablePermBornDiedTempData): Tooltip? {
        val gridPane = GridPane()
        gridPane.columnConstraints.add(ColumnConstraints(100.0))
        gridPane.columnConstraints.add(ColumnConstraints(100.0))
        gridPane.rowConstraints.add(RowConstraints())
        gridPane.rowConstraints.add(RowConstraints())
        gridPane.rowConstraints.add(RowConstraints())
        gridPane.rowConstraints.add(RowConstraints())
        gridPane.rowConstraints.stream().forEach { constraint -> constraint.percentHeight = 25.0 }

        val tooltip = Tooltip()
        tooltip.graphic = gridPane

        when (item.plotStyle) {
            PlotablePermBornDiedTempData.PlotStyle.PermDiedBorn -> {
                // Check if mouse hovers painted area
                // TODO: Make mouse position check
                /*
                if (x >= startXBorn * cellWidth && x <= endXBorn * cellWidth) {
                    return String.format("Born: %12s", format.format(data.born));
                }
                if (x >= startXPerm * cellWidth && x <= endXPerm * cellWidth) {
                    return String.format("Perm: %12s", format.format(data.perm));
                }
                if (x >= startXDied * cellWidth && x <= endXDied * cellWidth) {
                    return String.format("Died: %12s", format.format(data.died));
                }
                */

                val diedTextLabel = Label("Died:")
                diedTextLabel.textFill = Color.WHITE
                GridPane.setConstraints(diedTextLabel, 0, 0)
                gridPane.children.add(diedTextLabel)
                val diedNumberLabel = Label(format.format(item.died))
                diedNumberLabel.textFill = Color.WHITE
                GridPane.setConstraints(diedNumberLabel, 1, 0)
                gridPane.children.add(diedNumberLabel)

                val permTextLabel = Label("Perm:")
                permTextLabel.textFill = Color.WHITE
                GridPane.setConstraints(permTextLabel, 0, 1)
                gridPane.children.add(permTextLabel)
                val permNumberLabel = Label(format.format(item.perm))
                permNumberLabel.textFill = Color.WHITE
                GridPane.setConstraints(permNumberLabel, 1, 1)
                gridPane.children.add(permNumberLabel)

                val bornTextLabel = Label("Born:")
                bornTextLabel.textFill = Color.WHITE
                GridPane.setConstraints(bornTextLabel, 0, 2)
                gridPane.children.add(bornTextLabel)
                val bornNumberLabel = Label(format.format(item.born))
                bornNumberLabel.textFill = Color.WHITE
                GridPane.setConstraints(bornNumberLabel, 1, 2)
                gridPane.children.add(bornNumberLabel)

                if (item.before > 0) {
                    val ratioTextLabel = Label("After-Before-Ratio:")
                    ratioTextLabel.textFill = Color.WHITE
                    GridPane.setConstraints(ratioTextLabel, 0, 3)
                    gridPane.children.add(ratioTextLabel)
                    val ratioNumberLabel = Label(String.format("%+,.3f%%", 100.0 * item.after / (1.0 * item.before) - 100.0))
                    ratioNumberLabel.textFill = Color.WHITE
                    GridPane.setConstraints(ratioNumberLabel, 1, 3)
                    gridPane.children.add(ratioNumberLabel)
                }
            }

            PlotablePermBornDiedTempData.PlotStyle.Temp -> {
                val tempTextLabel = Label("Temp:")
                tempTextLabel.textFill = Color.WHITE
                GridPane.setConstraints(tempTextLabel, 0, 0)
                gridPane.children.add(tempTextLabel)
                val tempNumberLabel = Label(String.format("%,3f", item.temp))
                tempNumberLabel.textFill = Color.WHITE
                GridPane.setConstraints(tempNumberLabel, 1, 0)
                gridPane.children.add(tempNumberLabel)
            }
        }
        return tooltip
    }

    private fun setClippedText(label: Label, value: Double) {
        try {
            if (computeClippedText == null) {
                computeClippedText = Utils::class.java.getDeclaredMethod("computeClippedText",
                                                                         *arrayOf(Font::class.java, String::class.java, java.lang.Double.TYPE, OverrunStyle::class.java, String::class.java))
                computeClippedText!!.isAccessible = true
            }
        } catch (t: Throwable) {
            // ignore silently
        }

        val text = if (value > 0) format.format(value) else ""
        label.textProperty().bind(object : ObjectBinding<String>() {

            init {
                label.maxWidthProperty().addListener { _0, _1, _2 -> invalidate() }
            }

            override fun computeValue(): String {
                try {
                    if (computeClippedText != null) {
                        val clipped = computeClippedText!!.invoke(null,
                                                                  label.font,
                                                                  text,
                                                                  label.maxWidth,
                                                                  label.textOverrun,
                                                                  label.ellipsisString) as String
                        if (clipped.length < text.length) {
                            return ""
                        }
                    }
                } catch (t: Throwable) {
                    //ignore silently
                }

                return text
            }

        })
    }

    private fun hexColor(color: java.awt.Color): String {
        return String.format("#%02x%02x%02x", color.red, color.green, color.blue)
    }

    companion object {
        val PERM_COLOR = PermBornDiedTempStackedAreaJFreeChartPane.PERM_BORN_DIED_TEMP_COLORS[0]
        val DIED_COLOR = PermBornDiedTempStackedAreaJFreeChartPane.PERM_BORN_DIED_TEMP_COLORS[1]
        val BORN_COLOR = PermBornDiedTempStackedAreaJFreeChartPane.PERM_BORN_DIED_TEMP_COLORS[2]
        val TEMP_COLOR = PermBornDiedTempStackedAreaJFreeChartPane.PERM_BORN_DIED_TEMP_COLORS[3]

        private val format = DecimalFormat("###,###.#")

        private var computeClippedText: Method? = null
    }
}
