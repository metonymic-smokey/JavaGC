
package at.jku.anttracks.classification;

import java.util.Arrays;

/**
 * This class represents a classifier that is only applied on a given set of objects, identified by
 * {@link ConditionalClassifier#getClassifier()}
 *
 * @author Markus Weninger
 */
public abstract class ConditionalClassifier {

    /**
     * The classifiers that should be applied at the tree node defined by {@link ConditionalClassifier#getConditions()}
     *
     * @return The classifier
     */
    public abstract ClassifierChain getClassifiers();

    /**
     * Defines the node on which the classifier should be applied as an {@link Object[]}. {@code getConditions()[0]} represents the root
     * object, {@code getConditions()[1]} the key on level 1, and so on.
     *
     * @return The object array defining the node on which this classifier should be applied
     */
    public abstract Object[] getConditions();

    /**
     * Gets the classifier's name
     *
     * @return The classifier's name
     */
    public String getName() {
        return getClassifiers() + " applied @ " + Arrays.toString(getConditions());
    }

    @Override
    public String toString() {
        return getName();
    }
}
