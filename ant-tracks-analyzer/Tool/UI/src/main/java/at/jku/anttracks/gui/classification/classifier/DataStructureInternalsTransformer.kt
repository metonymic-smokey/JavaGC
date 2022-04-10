
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
import java.util.function.Supplier

@C(name = DataStructureInternalsTransformer.NAME,
   desc = "This transformer is used to classify the internal (non-leaf) objects of the data structure headed by this object",
   example = "",
   type = ClassifierType.ONE,
   collection = ClassifierSourceCollection.FASTHEAP)
class DataStructureInternalsTransformer : Transformer() {
    @ClassifierProperty(overviewLevel = 10)
    private var classifiers: ClassifierChain? = ClassifierChain(TypeClassifier())

    fun getClassifiers(): ClassifierChain? {
        return classifiers
    }

    fun setClassifiers(classifiers: ClassifierChain?) {
        this.classifiers = classifiers
        if (classifiers != null && classifiers.length() > 0) {
            classifiers.list.forEach { classifier -> classifier.setup({ symbols() }) { fastHeap() } }
        }
    }

    @Throws(ClassifierException::class)
    public override fun classify(transformerRoot: GroupingNode): GroupingNode {
        fastHeap()!!
                .getHeadedDataStructure(index())
                ?.getInternalObjects(fastHeap())
                ?.stream()
                ?.forEach { objIndex -> (transformerRoot as FastHeapGroupingNode).classify(fastHeap(), objIndex, classifiers) }

        return transformerRoot
    }

    override fun title(): String {
        return "Contain the following internal (non-leaf) objects:"
    }

    override fun setup(symbolsSupplier: Supplier<Symbols>?, fastHeapSupplier: Supplier<IndexBasedHeap>?) {
        super.setup(symbolsSupplier, fastHeapSupplier)
        classifiers?.list?.forEach { classifier -> classifier.setup(symbolsSupplier, fastHeapSupplier) }
    }

    companion object {
        const val NAME = "Data structure internals"
    }
}
