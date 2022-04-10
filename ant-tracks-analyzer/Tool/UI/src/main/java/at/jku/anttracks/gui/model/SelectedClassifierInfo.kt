package at.jku.anttracks.gui.model

import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.Filter

open class SelectedClassifierInfo(var selectedClassifiers: ClassifierChain = ClassifierChain(),
                                  var selectedFilters: List<Filter> = listOf())
