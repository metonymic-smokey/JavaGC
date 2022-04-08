
package at.jku.anttracks.gui.classification.classifier

import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.ClassifierException
import at.jku.anttracks.classification.Transformer
import at.jku.anttracks.classification.annotations.C
import at.jku.anttracks.classification.annotations.ClassifierProperty
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection.FASTHEAP
import at.jku.anttracks.classification.enumerations.ClassifierType.ONE
import at.jku.anttracks.classification.nodes.FastHeapGroupingNode
import at.jku.anttracks.classification.nodes.GroupingNode
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.symbols.Symbols
import java.util.function.Supplier

@C(name = DataStructureLeavesTransformer.NAME,
   desc = "This transformer is used to classify the leaf objects of the data structure headed by this object",
   example = "",
   type = ONE,
   collection = FASTHEAP)
class DataStructureLeavesTransformer : Transformer() {

    @ClassifierProperty(overviewLevel = 10)
    var showOnlyDeepLeaves = true

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
                ?.getLeafObjects(fastHeap(), showOnlyDeepLeaves)
                ?.stream()
                ?.forEach { objIndex -> (transformerRoot as FastHeapGroupingNode).classify(fastHeap(), objIndex, classifiers) }

        return transformerRoot
    }

    override fun title(): String {
        return "Contain the following leaf objects:"
    }

    override fun setup(symbolsSupplier: Supplier<Symbols>?, fastHeapSupplier: Supplier<IndexBasedHeap>?) {
        super.setup(symbolsSupplier, fastHeapSupplier)
        classifiers?.list?.forEach { classifier -> classifier.setup(symbolsSupplier, fastHeapSupplier) }
    }

    companion object {
        const val NAME = "Data structure leaves"
    }
}
