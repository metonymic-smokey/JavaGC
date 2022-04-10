
package at.jku.anttracks.classification.annotations;

import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;

import java.lang.annotation.*;

/**
 * This annotation is used to annotate {@link HeapObjectFilter} subclasses.
 *
 * @author Markus Weninger
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.TYPE_USE})
public @interface F {
    /**
     * Returns the filter's name.
     *
     * @return The filter's name
     */
    String name();

    /**
     * Returns the filter's description.
     *
     * @return The filter's description
     */
    String desc();

    /**
     * Returns the filter's source collection.
     *
     * @return The filter's source collection
     */
    ClassifierSourceCollection collection();
}
