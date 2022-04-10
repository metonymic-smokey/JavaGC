package at.jku.anttracks.util

import at.jku.anttracks.parser.TraceSlaveParser
import java.util.*
import kotlin.math.max
import kotlin.streams.asSequence

fun Sequence<Int>?.toBitSet(): BitSet {
    return this?.asIterable().toBitSet()
}

fun Iterable<Int>?.toBitSet(): BitSet {
    this ?: return BitSet()
    val result = BitSet(max(this.max() ?: 0, 0))
    this.asSequence().filter { it > TraceSlaveParser.NULL_PTR }.forEach { result.set(it) }
    return result
}

fun IntArray?.toBitSet(): BitSet {
    this ?: return BitSet()
    val result = BitSet(max(this.max() ?: 0, 0))
    this.asSequence().filter { it > TraceSlaveParser.NULL_PTR }.forEach { result.set(it) }
    return result
}

fun BitSet.asSequence(): Sequence<Int> {
    return this.stream().asSequence()
}

fun BitSet.andNew(other: BitSet): BitSet {
    val new = this.clone() as BitSet
    new.and(other)
    return new
}

fun BitSet.toIntArray(): IntArray {
    return this.asSequence().toSet().toIntArray()
}
