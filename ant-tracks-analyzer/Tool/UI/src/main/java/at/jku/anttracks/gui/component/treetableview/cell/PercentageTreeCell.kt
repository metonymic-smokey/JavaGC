package at.jku.anttracks.gui.component.treetableview.cell

import at.jku.anttracks.gui.model.Percentage
import java.text.DecimalFormat

class PercentageTreeCell<T> : BarTreeCell<T, Percentage>() {
    override fun defineTextToShow(item: Percentage): String {
        return format.format(item.value)
    }

    override fun defineFillRatio(item: Percentage): Double {
        return item.value / 100.0
    }

    companion object {
        private val format = DecimalFormat("###,##0.0")
    }
}
