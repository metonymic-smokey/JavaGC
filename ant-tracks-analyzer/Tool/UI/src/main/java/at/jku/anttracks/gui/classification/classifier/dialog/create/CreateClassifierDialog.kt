
package at.jku.anttracks.gui.classification.classifier.dialog.create

import at.jku.anttracks.classification.ClassifierFactory
import at.jku.anttracks.classification.ClassifierFactoryList
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.symbols.Symbols
import javafx.scene.control.Dialog
import java.util.function.Supplier

class CreateClassifierDialog : Dialog<ClassifierFactory>() {

    private lateinit var dialogPane: CreateClassifierDialogPane

    fun init(symbolsSupplier: Supplier<Symbols>,
             fastHeapSupplier: Supplier<IndexBasedHeap>,
             availableClassifier: ClassifierFactoryList) {

        CreateClassifierDialogPane()
                .also { it.init(symbolsSupplier, fastHeapSupplier, availableClassifier) }
                .also { setDialogPane(it) }
                .also { dialogPane = it }

        isResizable = true
        // Result converter returns null if "cancel" has been pressed
        setResultConverter { if (it == dialogPane.createButton) dialogPane.newClassifier else null }
    }
}
