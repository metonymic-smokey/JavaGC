
package at.jku.anttracks.gui.classification.classifier

import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.ClassifierException
import at.jku.anttracks.classification.annotations.C
import at.jku.anttracks.classification.annotations.ClassifierProperty
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection
import at.jku.anttracks.classification.enumerations.ClassifierType
import at.jku.anttracks.classification.nodes.FastHeapGroupingNode
import at.jku.anttracks.classification.nodes.GroupingNode
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.symbols.Symbols
import java.util.function.Supplier

@C(name = ContainedDataStructuresTransformer.NAME,
   desc = "This transformer is used to classify the data structure heads contained in this data structure",
   example = "",
   type = ClassifierType.ONE,
   collection = ClassifierSourceCollection.FASTHEAP)
class ContainedDataStructuresTransformer : at.jku.anttracks.classification.Transformer() {
    /*
    @ClassifierProperty(overviewLevel = 10)
    private boolean showOnlyDeepLeaves = true;

    public boolean getShowOnlyDeepLeaves() {
        return showOnlyDeepLeaves;
    }

    public void setShowOnlyDeepLeaves(boolean showOnlyDeepLeaves) {
        this.showOnlyDeepLeaves = showOnlyDeepLeaves;
    }
    */

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
        val dataStructure = fastHeap()!!.getHeadedDataStructure(index())

        dataStructure?.pointedDataStructures?.map { ds -> ds.headIdx }?.forEach { objIndex -> (transformerRoot as FastHeapGroupingNode).classify(fastHeap(), objIndex, classifiers) }

        return transformerRoot
    }

    override fun title(): String {
        return "Contain the following data structures:"
    }

    override fun setup(symbolsSupplier: Supplier<Symbols>?, fastHeapSupplier: Supplier<IndexBasedHeap>?) {
        super.setup(symbolsSupplier, fastHeapSupplier)
        classifiers?.list?.forEach { classifier -> classifier.setup(symbolsSupplier, fastHeapSupplier) }
    }

    companion object {
        const val NAME = "Contained data structures"
    }
}
