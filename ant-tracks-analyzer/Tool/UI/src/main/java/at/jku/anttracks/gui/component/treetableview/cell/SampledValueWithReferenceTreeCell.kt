package at.jku.anttracks.gui.component.treetableview.cell

import at.jku.anttracks.gui.model.SampledValueWithReference
import java.text.DecimalFormat

class SampledValueWithReferenceTreeCell<T>(val includeSampleBase: Boolean = false,
                                           val includePercentage: Boolean = false,
                                           val format: (Double) -> String = { numberFormat.format(it) }) : BarTreeCell<T, SampledValueWithReference>() {
    override fun defineTextToShow(item: SampledValueWithReference) = buildString {
        val value = format(item.value)
        val withoutSampleValue = format(item.withoutSample)
        if (item.isSampled) {
            append("~")
        }
        append(value)
        if (item.isSampled && includeSampleBase) {
            append(" (sampled on ")
            append(withoutSampleValue)
            append(")")
        }
        if (includePercentage) {
            val referenceLength = if (includeSampleBase) 30 else 15
            val totalLength = value.length + if (includeSampleBase) withoutSampleValue.length else 0
            val spaces = " ".repeat(referenceLength - value.length)
            append(spaces, "(", numberFormat.format(item.percentage.value), "%)")
        }
    }

    override fun defineFillRatio(item: SampledValueWithReference): Double {
        return item.percentage.value / 100.0
    }

    companion object {
        val numberFormat = DecimalFormat("###,###.#")
    }
}
