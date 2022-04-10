
package at.jku.anttracks.gui.component.treetable;

public interface TreeTableModelListener<T> {

    void dataChanged();

    void cellUpdated(TreeNode<T> node, int column);

    void expanded(TreeNode<T> node);

    void collapsed(TreeNode<T> node);

    void structureChanged();

}
