
package at.jku.anttracks.gui.model.tablecellrenderer;

import at.jku.anttracks.gui.model.ApproximateLong;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.DecimalFormat;

public class ApproximateLongCellRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;
    private final boolean toKb;

    public ApproximateLongCellRenderer(boolean toKb) {
        this.toKb = toKb;
    }

    public ApproximateLongCellRenderer() {
        this(false);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (value != null) {
            final ApproximateLong num = (ApproximateLong) value;
            double result = num.value;
            if (toKb) {
                result /= 1024;
            }
            DecimalFormat numberFormat = new DecimalFormat("###,###.#");

            final String text = numberFormat.format(result);
            setHorizontalAlignment(SwingConstants.RIGHT);
            setText(num.isExact ? text : ("~ " + text));
        }
        return this;
    }
}
