
package at.jku.anttracks.gui.classification.classifier

import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.ClassifierException
import at.jku.anttracks.classification.Transformer
import at.jku.anttracks.classification.annotations.C
import at.jku.anttracks.classification.annotations.ClassifierProperty
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection
import at.jku.anttracks.classification.enumerations.ClassifierType
import at.jku.anttracks.classification.nodes.FastHeapGroupingNode
import at.jku.anttracks.classification.nodes.GroupingNode
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.symbols.Symbols
import at.jku.anttracks.util.ImagePack
import java.util.*
import java.util.function.Supplier

@C(name = ContainingDataStructuresTransformer.NAME,
   desc = "This transformer is used to classify the data structures that contain this object",
   example = "(using \"Data structure and Type\") Data structures -> java.util.HashMap & java.util.ArrayList",
   type = ClassifierType.ONE,
   collection = ClassifierSourceCollection.FASTHEAP)
class ContainingDataStructuresTransformer : Transformer() {

    @ClassifierProperty(overviewLevel = 10)
    var classifiers = ClassifierChain(TypeClassifier())

    @ClassifierProperty(overviewLevel = 10)
    var includeIndirectDataStructures = true

    @ClassifierProperty(overviewLevel = 10)
    var showOnlyTopLevelDataStructures = true

    @ClassifierProperty(overviewLevel = 10)
    var considerIndirectlyContainedObjects = false

    public override fun loadIcons(): Array<ImagePack>? {
        return arrayOf()//                ImageUtil.getResourceImagePack("Data structures", "ds.png")
    }

    @Throws(ClassifierException::class)
    public override fun classify(transformerRoot: GroupingNode): GroupingNode {
        var dataStructures = fastHeap()!!.getDataStructures(index(), includeIndirectDataStructures, false)

        if (dataStructures == null && considerIndirectlyContainedObjects) {
            // recursively follow the from pointers of the currently classified object
            // if an encountered object is part of one or multiple data structures, remember them but dont follow the pointers of that object
            dataStructures = HashSet()
            val closedSet: BitSet = BitSet()
            val toProcess: BitSet = BitSet()
            closedSet.set(index())
            fastHeap().getFromPointers(index()).forEach {
                closedSet.set(it)
                toProcess.set(it)
            }

            while (!toProcess.isEmpty) {
                var idx = toProcess.nextSetBit(0)
                while (idx != -1) {
                    toProcess.clear(idx)

                    val ds = fastHeap().getDataStructures(idx, includeIndirectDataStructures, false)

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

        // classify gathered data structure heads
        dataStructures?.forEach {
            if (it.headIdx != index() && (!showOnlyTopLevelDataStructures || it.isTopLevelDataStructure)) {
                (transformerRoot as FastHeapGroupingNode).classify(fastHeap(), it.headIdx, classifiers)
            }
        }

        return transformerRoot
    }

    override fun title(): String {
        return "Contained in the following data structures:"
    }

    override fun setup(symbolsSupplier: Supplier<Symbols>?, fastHeapSupplier: Supplier<IndexBasedHeap>?) {
        super.setup(symbolsSupplier, fastHeapSupplier)
        classifiers.list?.forEach { classifier -> classifier.setup(symbolsSupplier, fastHeapSupplier) }
    }

    companion object {
        const val NAME = "Containing data structures"
    }
}
