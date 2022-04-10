package at.jku.anttracks.gui.component.tableview.cell

import at.jku.anttracks.gui.model.ValueWithReference
import java.text.DecimalFormat

private val numberFormat = DecimalFormat("###,###.#")

class ValueWithReferenceTableCell<T>(val includePercentage: Boolean = false,
                                     val format: (ValueWithReference) -> String = { numberFormat.format(it.value) }) : BarTableCell<T, ValueWithReference>() {
    override fun defineTextToShow(item: ValueWithReference) = buildString {
        val value = format(item)
        append(value)
        if (includePercentage) {
            val spaces = " ".repeat(12 - value.length)
            append(spaces, "(", numberFormat.format(item.percentage.value), "%)")
        }
    }

    override fun defineFillRatio(item: ValueWithReference): Double {
        return item.percentage.value / 100.0
    }
}
