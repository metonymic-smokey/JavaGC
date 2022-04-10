
package at.jku.anttracks.gui.classification.classifier

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.ClassifierException
import at.jku.anttracks.classification.annotations.C
import at.jku.anttracks.classification.annotations.ClassifierProperty
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection
import at.jku.anttracks.classification.enumerations.ClassifierType
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.symbols.Symbols
import at.jku.anttracks.util.ImagePack
import java.util.*
import java.util.function.Supplier

@C(name = ContainingDataStructuresClassifier.NAME,
   desc = "This classifier is used to classify an object into the set of data structures is is contained in",
   example = "Stored in java.util.HashMap allocated at X.foo()",
   type = ClassifierType.ONE,
   collection = ClassifierSourceCollection.FASTHEAP)
class ContainingDataStructuresClassifier : Classifier<String>() {
    @ClassifierProperty(overviewLevel = 10)
    var includeIndirectDataStructures = true

    @ClassifierProperty(overviewLevel = 10)
    var showOnlyTopLevelDataStructures = true

    @ClassifierProperty(overviewLevel = 10)
    var considerIndirectlyContainedObjects = true

    public override fun loadIcons(): Array<ImagePack>? {
        return arrayOf()//                ImageUtil.getResourceImagePack("Data structures", "ds.png")
    }

    @Throws(ClassifierException::class)
    public override fun classify(): String {
        if(type().simpleName.equals("Date")) {
            println("Debug me")
        }
        var dataStructures = fastHeap()!!.getDataStructures(index(), includeIndirectDataStructures, showOnlyTopLevelDataStructures)
        if (dataStructures == null) {
            dataStructures = HashSet()
        }

        if (dataStructures.isEmpty() && considerIndirectlyContainedObjects) {
            // recursively follow the from pointers of the currently classified object
            // if an encountered object is part of one or multiple data structures, remember them but dont follow the pointers of that object

            val closedSet = BitSet()
            val toProcess = BitSet()
            closedSet.set(index())
            fastHeap().getFromPointers(index()).forEach {
                closedSet.set(it)
                toProcess.set(it)
            }

            while (!toProcess.isEmpty) {
                var idx = toProcess.nextSetBit(0)
                while (idx != -1) {
                    toProcess.clear(idx)

                    val ds = fastHeap().getDataStructures(idx, includeIndirectDataStructures, showOnlyTopLevelDataStructures)

                    if (ds != null) {
                        ds.forEach { dataStructures.add(it) }
                    } else {
                        fastHeap().getFromPointers(idx)
                                .filter {
                                    it != IndexBasedHeap.NULL_INDEX && !closedSet.get(it)
                                }
                                .forEach {
                                    toProcess.set(it)
                                    closedSet.set(it)
                                }
                    }

                    idx = toProcess.nextSetBit(idx + 1)
                }
            }
        }
        dataStructures = dataStructures.filter { !showOnlyTopLevelDataStructures || it.isTopLevelDataStructure }.toHashSet()
        if (dataStructures.isEmpty()) {
            return NO_DS_RESULT
        } else {
            if (dataStructures.size > 1) {
                return MULTIPLE_DS_RESULT
            } else {
                val containingDS = dataStructures.iterator().next()
                return fastHeap().getType(containingDS.headIdx).getExternalName(true, false) +
                        " (allocated at " +
                        fastHeap().getAllocationSite(containingDS.headIdx).callSites[0].shortest +
                        ")"
            }
        }
    }

    override fun setup(symbolsSupplier: Supplier<Symbols>?, fastHeapSupplier: Supplier<IndexBasedHeap>?) {
        super.setup(symbolsSupplier, fastHeapSupplier)
    }

    companion object {
        const val NAME = "Contained in data structure"

        val NO_DS_RESULT = "Not contained in a data structure"
        val MULTIPLE_DS_RESULT = "Contained in multiple data structures"
    }
}
