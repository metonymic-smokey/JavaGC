
package at.jku.anttracks.gui.classification.filter.dialog.create

import at.jku.anttracks.classification.FilterFactory
import at.jku.anttracks.classification.FilterFactoryList
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.symbols.Symbols
import javafx.scene.control.Dialog
import java.util.function.Supplier

class CreateFilterDialog : Dialog<FilterFactory>() {

    private lateinit var dialogPane: CreateFilterDialogPane

    fun init(symbolsSupplier: Supplier<Symbols>,
             fastHeapSupplier: Supplier<IndexBasedHeap>,
             availableFilter: FilterFactoryList) {
        CreateFilterDialogPane().also {
            it.init(symbolsSupplier, fastHeapSupplier, availableFilter)
            setDialogPane(it)
            dialogPane = it
        }
        setResultConverter {
            if (it == dialogPane.createButton) {
                dialogPane.newFilter
            } else {
                // Cancel has been pressed
                null
            }
        }
        isResizable = true
    }
}
