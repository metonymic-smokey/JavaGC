
package at.jku.anttracks.gui.component.treetable;

/**
 * Objects of this class represent the value for a TreeNode at a given column
 *
 * @param <T> The TreeNode's type this value belongs to
 * @author Markus Weninger
 */
public class TreeNodeValue<T> {

    public final TreeNode<T> node;
    public final Object value;
    @SuppressWarnings("unused")
    private final int column;

    public TreeNodeValue(TreeNode<T> node, Object value, int column) {
        this.node = node;
        this.value = value;
        this.column = column;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other != null && getClass().equals(other.getClass())) {
            @SuppressWarnings("unchecked")
            TreeNodeValue<T> that = (TreeNodeValue<T>) other;
            return node == that.node && value.equals(that.value);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
