
package at.jku.anttracks.gui.component.treetable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class TreeNodeTableCellEditor<T> extends AbstractCellEditor implements TableCellEditor {

    private final TableCellRenderer renderer;
    private final ListSelectionModel selection;

    public TreeNodeTableCellEditor(TableCellRenderer renderer, ListSelectionModel selection) {
        this.renderer = renderer;
        this.selection = selection;
    }

    @SuppressWarnings("unchecked")
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int r, int c) {
        SwingUtilities.invokeLater(() -> flipExpanded((TreeNode<T>) value, r));
        return renderer.getTableCellRendererComponent(table, value, isSelected, false, r, c);
    }

    private void flipExpanded(TreeNode<T> node, int row) {
        fireEditingCanceled();
        selection.clearSelection();
        node.setExpanded(!node.isExpanded());
        selection.addSelectionInterval(row, row);
    }

    @Override
    public Object getCellEditorValue() {
        return null;
    }

}
