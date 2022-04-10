
package at.jku.anttracks.gui.component.treetable;

public interface TreeNodeListener<T> {

    void expanded(TreeNode<T> node);

    void collapsed(TreeNode<T> node);

}
