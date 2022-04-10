
package at.jku.anttracks.gui.model

class SampledValueWithReference(value: Double, reference: Double, val isSampled: Boolean = false, val withoutSample: Double) : ValueWithReference(value, reference) {
    operator fun compareTo(that: SampledValueWithReference): Int {
        return Math.signum(value - that.value).toInt()
    }

    override fun toString(): String {
        return (if (isSampled) "~" else "") + value.toString();
    }
}
