
package at.jku.anttracks.classification;

import at.jku.anttracks.classification.annotations.F;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.util.AnnotationHelper;

import java.util.logging.Logger;

public abstract class Filter extends Classifier<Boolean> {

    protected Logger LOGGER = Logger.getLogger(this.getClass().getSimpleName());

    @Override
    public String toString() {
        String s = getName();
        return s != null ? s : super.toString();
    }

    /**
     * Gets the filters's name
     *
     * @return The classifier's name
     */
    @Override
    public String getName() {
        if (name == null) {
            F annotation = AnnotationHelper.getAnnotation(this,
                                                          F.class);
            name = annotation != null ? annotation.name() : "No name";
        }
        return name;
    }

    /**
     * Gets the classifier's name
     *
     * @return The classifier's name
     */
    @Override
    public String getDesc() {
        if (desc == null) {
            F annotation = AnnotationHelper.getAnnotation(this,
                                                          F.class);
            desc = annotation != null ? annotation.desc() : "No description";
        }
        return desc;
    }

    /**
     * Gets the classifier's name
     *
     * @return The classifier's name
     */
    public ClassifierSourceCollection getSourceCollection() {
        if (sourceCollection == null) {
            F annotation = AnnotationHelper.getAnnotation(this,
                                                          F.class);
            sourceCollection = annotation != null ? annotation.collection() : ClassifierSourceCollection.ALL;
        }
        return sourceCollection;
    }
}