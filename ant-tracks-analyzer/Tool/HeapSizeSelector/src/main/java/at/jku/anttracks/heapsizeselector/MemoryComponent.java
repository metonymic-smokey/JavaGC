package at.jku.anttracks.heapsizeselector;

import javax.swing.*;
import java.awt.*;

public class MemoryComponent extends JComponent {

    private static final long serialVersionUID = 1L;

    private volatile MemoryInfo memory;
    private volatile long usage;

    public MemoryComponent(MemoryInfo memory) {
        this.memory = memory;
        this.usage = 0;
        setMinimumSize(new Dimension(0, 25));
        setPreferredSize(new Dimension(0, 50));
    }

    public MemoryInfo getMemory() {
        return memory;
    }

    public void setMemory(MemoryInfo memory) {
        this.memory = memory;
        SwingUtilities.invokeLater(this::repaint);
    }

    public void setUsage(long usage) {
        this.usage = usage;
        SwingUtilities.invokeLater(this::repaint);
    }

    @Override
    public void paint(Graphics g) {
        double usageRatio = 1.0 * usage / memory.total;

        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, getWidth() * 1, getHeight());

        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, (int) (getWidth() * (1 - memory.available)), getHeight());

        g.setColor(usageRatio > memory.available ? Color.RED : Color.GREEN);
        g.fillRect((int) (getWidth() * (1 - usageRatio)), 0, getWidth(), getHeight());

        g.setColor(Color.BLACK);
        for (int i = 0; i < 10; i++) {
            int x = (int) (getWidth() * 0.1 * i);
            g.drawLine(x, 0, x, getHeight());
        }
    }
}
