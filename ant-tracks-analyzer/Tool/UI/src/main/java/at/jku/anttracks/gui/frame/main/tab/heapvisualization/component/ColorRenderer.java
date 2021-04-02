
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.component;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class ColorRenderer extends JLabel implements TableCellRenderer {
    private static final long serialVersionUID = 1L;
    Border unselectedBorder;
    Border selectedBorder;

    public ColorRenderer() {
        setOpaque(true);
    }

    public Component getTableCellRendererComponent(JTable table, Object color, boolean isSelected, boolean hasFocus, int row, int column) {
        Color newColor = (Color) color;
        if (newColor != null) {
            setBackground(newColor);
            if (isSelected) {
                if (selectedBorder == null) {
                    selectedBorder = BorderFactory.createMatteBorder(2, 5, 2, 5, table.getSelectionBackground());
                }
                setBorder(selectedBorder);
            } else {
                if (unselectedBorder == null) {
                    unselectedBorder = BorderFactory.createMatteBorder(2, 5, 2, 5, table.getBackground());
                }
                setBorder(unselectedBorder);
            }

        }

        return this;
    }
}
