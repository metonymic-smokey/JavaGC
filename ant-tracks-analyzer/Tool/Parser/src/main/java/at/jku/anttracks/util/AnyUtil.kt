package at.jku.anttracks.util

import org.openjdk.jol.info.GraphLayout

val <T> T.safe
    get() = this
val Any.deepCount: Long
    get() = GraphLayout.parseInstance(this).totalCount()
val Any.deepSize: Long
    get() = GraphLayout.parseInstance(this).totalSize()