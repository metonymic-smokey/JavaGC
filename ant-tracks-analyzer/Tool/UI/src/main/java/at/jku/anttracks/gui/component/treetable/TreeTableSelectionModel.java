
package at.jku.anttracks.gui.component.treetable;

import javax.swing.*;

public class TreeTableSelectionModel<T> extends DefaultListSelectionModel {
    private static final long serialVersionUID = 1L;

    private final TreeTable<T> table;

    public TreeTableSelectionModel(TreeTable<T> table) {
        this.table = table;
        setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    }

    @Override
    public void addSelectionInterval(int index0, int index1) {
        // Recursion anchor
        if (index0 < 0 || index1 < 0) {
            return;
        }

        setValueIsAdjusting(true);
        for (int base = Math.min(index0, index1); base <= Math.max(index0, index1); base++) {
            int curIndex = base;
            int parentIndex = findParentIndex(curIndex);
            // Recursive call for post-order DFS
            addSelectionInterval(parentIndex, parentIndex);
            super.addSelectionInterval(curIndex, curIndex);
        }
        setValueIsAdjusting(false);
    }

    @Override
    public void setSelectionInterval(int index0, int index1) {
        // Sanity check
        if (index0 < 0 || index1 < 0) {
            return;
        }

        // This check has the following reason:
        // If the user clicks on a row and keeps the mouse pressed (similar to drag-and-drop) multiple setSelectionInterval calls are
        // executed.
        // The first one with setSelectionInterval(selectedRow, selectedRow), the following with setSelectionInterval(minSelection,
        // selectedRow)
        // Because every selection automatically selects all root nodes (and therefore also always row 0), minSelection is always 0 during
        // such movements.
        // Therefore, we ignore DnD-moves
        if (index0 == 0 && index1 != 0) {
            return;
        }
        clearSelection();
        this.addSelectionInterval(index0, index1);
    }

    private int findParentIndex(int index) {
        int level = getLevelForRow(index);
        for (int parentIndex = index - 1; parentIndex >= 0; parentIndex--) {
            if (getLevelForRow(parentIndex) < level) {
                return parentIndex;
            }
        }
        return -1;
    }

    private int getLevelForRow(int row) {
        return table.getValueAt(row, 0).node.getLevel();
    }

}
