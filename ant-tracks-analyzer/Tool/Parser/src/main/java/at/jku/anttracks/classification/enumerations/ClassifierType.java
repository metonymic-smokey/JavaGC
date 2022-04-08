
package at.jku.anttracks.classification.enumerations;

import at.jku.anttracks.classification.annotations.C;

/**
 * This enum is used by the {@link C} annotation to define its behaviour.
 *
 * @author Markus Weninger
 */
public enum ClassifierType {

    /**
     * A classifier of this type identifies a heap object with a single identifier. Example: A heap object can only be allocation by a
     * single subsystem -> VM, Interpreter, C1 or C2.
     */
    ONE("One"),
    /**
     * A classifier of this type may identify a heap object by multiple identifies. Example: A heap object may be assigned to multiple
     * features (feature 1 & feature 4). The identifier of such a classifier is assumed to be an array.
     */
    MANY("Many"),
    /**
     * A classifier of this type identifies a heap object not by a single identifier, but by a sequence of multiple identifiers. Example: A
     * heap object may be identified by its stack trace (A calls B, B calls C -> Identifier = [A, B, C]). The identifier of such a
     * classifier is assumed to be an array.
     */
    HIERARCHY("Hierarchy"),
    /**
     * A classifier of this type may identify a heap by multiple sequences of identifiers. Example: A heap object may be identified by the
     * path to all his roots (Root1 -> A, Root2 -> B -> A, Root3 -> C -> B -> A, thus the identifier of A would be:
     * [ [Root1]; [B, Root2]; [C, B, Root3] ]). The identifier of such a classifier is assumed to be an array of arrays.
     */
    MANY_HIERARCHY("Many hierarchy");

    private String text;

    ClassifierType(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
