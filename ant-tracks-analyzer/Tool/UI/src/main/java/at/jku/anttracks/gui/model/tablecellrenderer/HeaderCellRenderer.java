
package at.jku.anttracks.gui.model.tablecellrenderer;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class HeaderCellRenderer implements TableCellRenderer {
    private DefaultTableCellRenderer renderer;
    private int horAlignment;

    public HeaderCellRenderer(JTable table, int horizontalAlignment) {
        horAlignment = horizontalAlignment;
        renderer = (DefaultTableCellRenderer) table.getTableHeader().getDefaultRenderer();
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
        JLabel label;
        try {
            label = (JLabel) renderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
        } catch (Throwable t) {
            label = new JLabel();
        }
        label.setHorizontalAlignment(horAlignment);
        return label;
    }
}
