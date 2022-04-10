
package at.jku.anttracks.classification.annotations;

import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;

import java.lang.annotation.*;

/**
 * This annotation is used to annotate {@link at.jku.anttracks.classification.Classifier} subclasses.
 *
 * @author Markus Weninger
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.TYPE_USE})
public @interface C {
    /**
     * Returns the classification's name. Examples: "Allocated Type", "Features", ...
     *
     * @return The classification's name
     */
    String name();

    /**
     * Returns the classification's description.
     *
     * @return The classification's description
     */
    String desc();

    /**
     * Returns the classification's type.
     *
     * @return The classification's type
     */
    ClassifierType type();

    /**
     * Returns the classification's source collection.
     *
     * @return The classification's source collection
     */
    ClassifierSourceCollection collection();

    /**
     * Returns the classification's example
     *
     * @return An example classification key for this classifier, e.g. "java.lang.String" for the type classifier
     */
    String example();
}
