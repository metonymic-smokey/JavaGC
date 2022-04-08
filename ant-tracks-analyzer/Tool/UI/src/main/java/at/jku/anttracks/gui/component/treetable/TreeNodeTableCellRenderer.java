
package at.jku.anttracks.gui.component.treetable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.function.Function;

public class TreeNodeTableCellRenderer<T> extends TreeNodeCellPanel<T> implements TableCellRenderer {
    private static final long serialVersionUID = -4419575450835376020L;

    public TreeNodeTableCellRenderer(Function<TreeNode<T>, String> getTreeNodeText) {
        super(getTreeNodeText);
        setName("Table.cellRenderer");
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        @SuppressWarnings("unchecked")
        TreeNode<T> node = (TreeNode<T>) value;

        updateValue(node);

        return this;
    }
}
