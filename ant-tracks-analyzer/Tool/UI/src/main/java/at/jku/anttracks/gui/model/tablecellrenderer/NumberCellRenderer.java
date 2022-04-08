
package at.jku.anttracks.gui.model.tablecellrenderer;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.DecimalFormat;

public class NumberCellRenderer extends DefaultTableCellRenderer {
    private static final long serialVersionUID = 1L;
    private final boolean toKb;

    public NumberCellRenderer(boolean toKb) {
        this.toKb = toKb;
    }

    public NumberCellRenderer() {
        this(false);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (value != null) {
            double num = (double) value;

            if (toKb) {
                num /= 1024;
            }
            DecimalFormat numberFormat = new DecimalFormat("###,###.#");

            String text = numberFormat.format(num);
            setHorizontalAlignment(SwingConstants.RIGHT);
            setText(text);
        }
        return this;
    }
}