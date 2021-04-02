
package at.jku.anttracks.gui.model

import java.math.BigDecimal

open class ValueWithReference(val value: Double, val reference: Double) : Comparable<ValueWithReference> {
    var percentage: Percentage

    init {
        percentage = if (reference == 0.0) Percentage(0.0) else Percentage(BigDecimal(value / reference * 100).setScale(1, BigDecimal.ROUND_HALF_UP).toDouble())
    }

    override fun compareTo(that: ValueWithReference): Int {
        return Math.signum(value - that.value).toInt()
    }

    override fun toString(): String {
        return value.toString()
    }
}
