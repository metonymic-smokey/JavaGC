
package at.jku.anttracks.gui.chart.base

import at.jku.anttracks.gui.model.ChartSelection
import java.util.*

class SelectedItemsList {
    private val items: MutableList<ChartSelection> = ArrayList()

    fun clear() {
        this.items.clear()
    }

    fun containsItemInAnySeries(x: Int): Boolean {
        return this.items.stream().anyMatch { i -> i.item == x }
    }

    fun containsItemInAnySeries(sel: ChartSelection): Boolean {
        return containsItemInAnySeries(sel.item)
    }

    fun containsItem(series: Int, x: Int): Boolean {
        return this.items.stream().anyMatch { i -> i.item == x && i.series == series }
    }

    fun containsItem(sel: ChartSelection): Boolean {
        return containsItem(sel.item, sel.series)
    }

    fun add(item: ChartSelection) {
        this.items.add(item)
    }

    fun get(): List<ChartSelection> {
        Collections.sort(items, Comparator.comparingDouble { cs -> cs.x })
        return this.items
    }

    operator fun get(index: Int): ChartSelection {
        Collections.sort(items, Comparator.comparingDouble { cs -> cs.x })
        return this.items[index]
    }

    fun remove(item: Int) {
        this.items.removeAll { selection -> selection.item == item }
    }

    fun remove(sel: ChartSelection) {
        this.items.removeAll { selection -> selection.item == sel.item }
    }

    fun size(): Int {
        return items.size
    }

    fun sort() {
        items.sort()
    }
}
