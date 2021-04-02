
package at.jku.anttracks.gui.component.treetable;

public interface TreeTableModelInterface<T> {

    TreeNode<T> getRoot();

    int getColumnCount();

    String getColumnName(int column);

    Class<?> getColumnClass(int column);

    Object getValueAt(TreeNode<T> node, int column);

    boolean isCellEditable(TreeNode<T> node, int column);

    void setValueAt(Object aValue, TreeNode<T> node, int column);

    void addListener(TreeTableModelListener<T> listener);

    void removeListener(TreeTableModelListener<T> listener);

    String getDefaultSorting();
}
