
package at.jku.anttracks.gui.component.treetable;

import javax.swing.*;
import java.awt.*;

public class NodeIcon implements Icon {

    static final int SIZE = 9;

    private final char type;

    public NodeIcon(char type) {
        this.type = type;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        g.setColor(Color.WHITE);
        g.fillRect(x, y, SIZE - 1, SIZE - 1);

        g.setColor(Color.DARK_GRAY);
        g.drawRect(x, y, SIZE - 1, SIZE - 1);

        g.drawLine(x + 2, y + SIZE / 2, x + SIZE - 3, y + SIZE / 2);
        if (type == '+') {
            g.drawLine(x + SIZE / 2, y + 2, x + SIZE / 2, y + SIZE - 3);
        }
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }
}
