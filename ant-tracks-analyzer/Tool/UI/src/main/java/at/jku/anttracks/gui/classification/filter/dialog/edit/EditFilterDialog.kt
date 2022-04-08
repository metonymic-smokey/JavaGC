
package at.jku.anttracks.gui.classification.filter.dialog.edit

import at.jku.anttracks.classification.FilterFactory
import at.jku.anttracks.classification.FilterFactoryList
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.symbols.Symbols
import javafx.scene.control.Dialog
import java.util.function.Supplier

class EditFilterDialog : Dialog<FilterFactory>() {

    private lateinit var dialogPane: EditFilterDialogPane

    fun init(symbolsSupplier: Supplier<Symbols>,
             fastHeapSupplier: Supplier<IndexBasedHeap>,
             fac: FilterFactory,
             availableFilters: FilterFactoryList) {
        EditFilterDialogPane().also {
            it.init(fac, availableFilters, fastHeapSupplier, symbolsSupplier)
            setDialogPane(it)
            dialogPane = it
        }
        setResultConverter { result ->
            when {
                result == dialogPane.saveButton -> dialogPane.filter
                result == dialogPane.deleteButton -> fac
                result.buttonData.isCancelButton -> null
                else -> {
                    assert(false) { result.toString() + " button not yet supported" }
                    fac
                }
            }
        }
    }
}
