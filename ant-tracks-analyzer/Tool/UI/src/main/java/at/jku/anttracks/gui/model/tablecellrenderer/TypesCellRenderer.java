
package at.jku.anttracks.gui.model.tablecellrenderer;

import at.jku.anttracks.gui.model.Types;
import at.jku.anttracks.util.SignatureConverter;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class TypesCellRenderer extends DefaultTableCellRenderer {
    private static final long serialVersionUID = 1L;

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (value != null) {
            StringBuilder text = new StringBuilder();
            boolean first = true;
            for (String type : ((Types) value).types) {
                if (type != null) {
                    if (first) {
                        first = false;
                    } else {
                        text.append(", ");
                    }
                    text.append(SignatureConverter.convertToJavaType(type, true));
                }
            }
            setHorizontalAlignment(SwingConstants.RIGHT);
            setText(text.toString());
        }
        return this;
    }
}
