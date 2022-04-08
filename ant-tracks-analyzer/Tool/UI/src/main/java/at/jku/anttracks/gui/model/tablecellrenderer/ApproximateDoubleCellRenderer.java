
package at.jku.anttracks.gui.model.tablecellrenderer;

import at.jku.anttracks.gui.model.ApproximateDouble;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class ApproximateDoubleCellRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = -7629559964336560836L;
    private final boolean toKb;

    public ApproximateDoubleCellRenderer(boolean toKb) {
        this.toKb = toKb;
    }

    public ApproximateDoubleCellRenderer() {
        this(false);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (value != null) {
            ApproximateDouble num = (ApproximateDouble) value;
            if (toKb) {
                num = new ApproximateDouble(num.value / 1024, num.isExact);
            }

            setHorizontalAlignment(SwingConstants.RIGHT);
            setText(num.toString());
        }
        return this;
    }
}
