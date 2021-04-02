
package at.jku.anttracks.gui.classification.classifier

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.annotations.C
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection
import at.jku.anttracks.classification.enumerations.ClassifierType
import at.jku.anttracks.gui.classification.classifier.ThreadClassifier.Companion.DESC
import at.jku.anttracks.gui.classification.classifier.ThreadClassifier.Companion.EX
import at.jku.anttracks.gui.classification.classifier.ThreadClassifier.Companion.NAME

@C(name = NAME, desc = DESC, example = EX, type = ClassifierType.ONE, collection = ClassifierSourceCollection.ALL)
class ThreadClassifier : Classifier<String>() {
    // TODO: Icon

    public override fun classify(): String {
        return externalThreadName()
    }

    companion object {
        const val NAME = "Thread"
        const val DESC = "This classifier distinguishes objects based on the thread that allocated them"
        const val EX = "MainThread"
    }
}