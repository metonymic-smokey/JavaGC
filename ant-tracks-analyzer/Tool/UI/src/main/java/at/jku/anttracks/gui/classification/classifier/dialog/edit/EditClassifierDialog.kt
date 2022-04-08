
package at.jku.anttracks.gui.classification.classifier.dialog.edit

import at.jku.anttracks.classification.ClassifierFactory
import at.jku.anttracks.classification.ClassifierFactoryList
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.symbols.Symbols
import javafx.scene.control.Dialog
import java.io.IOException
import java.util.function.Supplier

class EditClassifierDialog @Throws(IOException::class)
constructor(symbolsSupplier: Supplier<Symbols>,
            fastHeapSupplier: Supplier<IndexBasedHeap>,
            fac: ClassifierFactory,
            availableClassifiers: ClassifierFactoryList) : Dialog<ClassifierFactory>() {

    val dialogPane = EditClassifierDialogPane()
            .also { it.init(fac, availableClassifiers, fastHeapSupplier, symbolsSupplier) }
            .also { setDialogPane(it) }

    init {
        setResultConverter { result ->
            when {
                result == dialogPane.saveButton ->
                    // Return new one
                    dialogPane.classifier
                result == dialogPane.deleteButton -> fac
                result.buttonData.isCancelButton -> null
                else -> {
                    assert(false) { result.toString() + " button is not yet supported!" }
                    fac
                }
            }
        }
    }

}
