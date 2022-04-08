
package at.jku.anttracks.gui.model

class Percentage(val value: Double) : Comparable<Percentage> {

    override fun compareTo(that: Percentage): Int {
        return Math.signum(this.value - that.value).toInt()
    }

    override fun toString(): String {
        return value.toString()
    }

}
