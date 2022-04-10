
package at.jku.anttracks.gui.model.tablecellrenderer;

import at.jku.anttracks.gui.component.treetable.TreeTable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public abstract class BarCellRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = -6648271521788186225L;
    private static final Color[] percentageColors = {new Color(92, 192, 56),
                                                     new Color(222, 129, 144),
                                                     new Color(255, 204, 0)};

    int colorId = 0;
    String textToShow = "";
    double fillRatio = 0.0;

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (value == null) {
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }

        colorId = ((TreeTable<?>) table).getValueAt(row, column).node.getSubTreeLevel() % percentageColors.length;
        fillRatio = defineFillRatio(value);
        textToShow = defineTextToShow(value);
        // setHorizontalAlignment(SwingConstants.RIGHT);
        // setText(String.valueOf(percentage));
        return this;
    }

    abstract String defineTextToShow(Object value);

    abstract double defineFillRatio(Object value);

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(percentageColors[colorId]);

        // Bar
        double barWidth = fillRatio * getWidth();
        g2d.fillRect(0, 0, (int) barWidth, getHeight());

        // Empty space
        g2d.setColor(getBackground());
        g2d.fillRect((int) barWidth + 1, 0, (int) (getWidth() - barWidth), getHeight());

        // Right-aligned text
        g2d.setColor(Color.BLACK);
        float textX = getWidth() - g2d.getFontMetrics().stringWidth(textToShow) - 2.0f;
        // http://stackoverflow.com/questions/27706197/how-can-i-center-graphics-drawstring-in-java
        float textY = ((getHeight() - g2d.getFontMetrics().getHeight()) / 2) + g2d.getFontMetrics().getAscent();
        g2d.drawString(textToShow, textX, textY);
    }
}
