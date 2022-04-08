
package at.jku.anttracks.heap

import at.jku.anttracks.heap.labs.AddressHO
import at.jku.anttracks.heap.roots.RootPtr
import at.jku.anttracks.heap.space.SpaceInfo

interface ObjectVisitor {
    class Settings(val rootPointerInfoNeeded: Boolean) {
        companion object {
            var ALL_INFOS = Settings(true)
            var NO_INFOS = Settings(false)
        }
    }

    fun visit(address: Long, obj: AddressHO, space: SpaceInfo, rootPtrs: List<RootPtr>?)
}
