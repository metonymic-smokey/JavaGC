
package at.jku.anttracks.classification.annotations;

import java.lang.annotation.*;

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface ClassifierProperty {
    /*
     * The level on which this property is shown in classifier overviews (e.g., on the UI). Level 0 is never shown in the overview, level
     * 10+ is always shown.
     */
    int overviewLevel();
}
