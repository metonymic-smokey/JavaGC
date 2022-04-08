
package at.jku.anttracks.gui.component.treetable;

import at.jku.anttracks.util.ArraysUtil;

import javax.swing.table.AbstractTableModel;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.logging.Logger;

// This class represents the middle-ware between UI and model
public final class TreeTableModelAdapter<T> extends AbstractTableModel {
    private final Logger LOGGER = Logger.getLogger("at.jku.anttracks.gui.detail.statistics.treetable.TreeTableModelAdapter");

    private static final long serialVersionUID = 1L;
    private TreeTableModelInterface<T> model;

    private TreeNode<T>[] index;

    TreeTableModelAdapter<T>.AdapterTreeTableListener listener = new AdapterTreeTableListener();

    public TreeTableModelAdapter(TreeTableModelInterface<T> model) {
        updateModel(model);
    }

    public void updateModel(TreeTableModelInterface<T> model) {
        if (this.model != null) {
            this.model.removeListener(listener);
        }
        TreeTableModelInterface<T> old = this.model;
        this.model = model;

        if (old != null) {
            TreeNode<T> oldCur = old.getRoot();
            TreeNode<T> newCur = this.model.getRoot();

            expandRecursive(oldCur, newCur);
        }

        this.model.addListener(listener);
        rebuildIndex();
    }

    private void expandRecursive(TreeNode<T> oldCur, TreeNode<T> newCur) {
        for (TreeNode<T> oldChild : oldCur.getChildren()) {
            for (TreeNode<T> newChild : newCur.getChildren()) {
                if (oldChild.isExpanded() && oldChild.toString().equals(newChild.toString())) {
                    newChild.setExpanded(true);
                    expandRecursive(oldChild, newChild);
                }
            }
        }
    }

    @Override
    public int getColumnCount() {
        return model.getColumnCount();
    }

    public TreeTableModelInterface<T> getModel() {
        return model;
    }

    @Override
    public String getColumnName(int column) {
        return model.getColumnName(column);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return model.getColumnClass(column);
    }

    @Override
    public int getRowCount() {
        return index.length;
    }

    public TreeNode<T> getNode(int row) {
        return index[row];
    }

    @Override
    public TreeNodeValue<T> getValueAt(int row, int column) {
        return new TreeNodeValue<>(index[row], model.getValueAt(index[row], column), column);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return model.isCellEditable(index[row], column);
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
        model.setValueAt(value, index[row], column);
    }

    private void rebuildIndex() {
        index = buildIndex(0, computeRowCount());
    }

    private TreeNode<T>[] buildIndex(int from, int to) {
        @SuppressWarnings("unchecked")
        TreeNode<T>[] index = (TreeNode<T>[]) Array.newInstance(TreeNode.class, to - from);
        for (int row = from; row < to; row++) {
            index[row - from] = computeNodeForRow(row);
        }
        return index;
    }

    private int computeRowCount() {
        return computeRowCount(model.getRoot());
    }

    private static <T> int computeRowCount(TreeNode<T> node) {
        return computeRowCount(node, false);
    }

    private static <T> int computeRowCount(TreeNode<T> node, boolean assumeExpanded) {
        if (node == null) {
            return 0;
        } else {
            int count = 1;
            if (node.isRoot() || node.isExpanded() || assumeExpanded) {
                List<TreeNode<T>> children = node.getChildren();
                for (int child = 0; child < children.size(); child++) {
                    count += computeRowCount(children.get(child));
                }
            }
            return count;
        }
    }

    private TreeNode<T> computeNodeForRow(int row) {
        return computePathForRow(row).pop();
    }

    private Stack<TreeNode<T>> computePathForRow(int row) {
        Stack<TreeNode<T>> path = new Stack<>();
        computePathForRow(model.getRoot(), row, path);
        return path;
    }

    private static <T> int computePathForRow(TreeNode<T> node, int row, Stack<TreeNode<T>> path) {
        if (row == 0) {
            path.push(node);
            return 0;
        } else if (node.isRoot() || node.isExpanded()) {
            path.push(node);
            int consumedRows = 1;
            List<TreeNode<T>> children = node.getChildren();
            for (int childIndex = 0; childIndex < children.size(); childIndex++) {
                TreeNode<T> child = children.get(childIndex);
                int consumedRowsByChild = computePathForRow(child, row - consumedRows, path);
                if (consumedRowsByChild == 0) {
                    return 0;
                }
                consumedRows += consumedRowsByChild;
            }
            path.pop();
            return consumedRows;
        } else {
            return 1;
        }
    }

    private int getRowForNode(TreeNode<T> node) {
        for (int row = 0; row < index.length; row++) {
            if (index[row] == node) {
                return row;
            }
        }
        return -1;
    }

    private final class AdapterTreeTableListener implements TreeTableModelListener<T> {

        @Override
        public void dataChanged() {
            rebuildIndex();
            fireTableDataChanged();
        }

        @Override
        public void cellUpdated(TreeNode<T> node, int column) {
            fireTableCellUpdated(getRowForNode(node), column);
        }

        @Override
        public void expanded(TreeNode<T> node) {
            expansionChanged(node);
        }

        @Override
        public void collapsed(TreeNode<T> node) {
            expansionChanged(node);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void expansionChanged(TreeNode<T> node) {
            int row = getRowForNode(node);
            int childCount = computeRowCount(node, true) - 1;
            if (childCount > 0) {
                if (node.isExpanded()) {
                    if (childCount != node.getChildCount()) {
                        LOGGER.warning("child count must match!");
                    }
                    TreeNode<T>[] childIndex = buildIndex(row + 1, row + 1 + childCount);
                    index = ArraysUtil.insert(TreeNode.class, index, childIndex, row + 1);
                    fireTableRowsInserted(row + 1, row + 1 + childCount - 1);
                } else {
                    index = ArraysUtil.remove(TreeNode.class, index, row + 1, childCount);
                    fireTableRowsDeleted(row + 1, row + 1 + childCount - 1);
                }
                assert Arrays.equals(index, buildIndex(0, computeRowCount()));
            }
        }

        @Override
        public void structureChanged() {
            fireTableStructureChanged();
        }
    }
}
