
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.component;

import javax.swing.*;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ColorEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {
    private static final long serialVersionUID = 1L;
    private Color currentColor;
    private final JButton button;
    private final JColorChooser colorChooser;
    private final JDialog dialog;

    protected static final String EDIT = "edit";
    public static final Color[] BASE_COLOR_VALUES;

    static {
        BASE_COLOR_VALUES = new Color[]{new Color(222, 73, 222), new Color(9, 156, 31), new Color(73, 185, 222), new Color(237, 209, 47), new Color(227, 227, 225)};
    }

    public ColorEditor() {

        button = new JButton();
        button.setActionCommand(EDIT);
        button.addActionListener(this);
        button.setBorderPainted(false);

        colorChooser = new JColorChooser();
        try {
            final LookAndFeel tmp = UIManager.getLookAndFeel();
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
            SwingUtilities.updateComponentTreeUI(colorChooser);
            UIManager.setLookAndFeel(tmp);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }
        AbstractColorChooserPanel[] panels = colorChooser.getChooserPanels();
        colorChooser.removeChooserPanel(panels[1]);
        colorChooser.removeChooserPanel(panels[2]);
        colorChooser.removeChooserPanel(panels[3]);
        colorChooser.removeChooserPanel(panels[4]);

        colorChooser.setPreviewPanel(new JPanel());
        JPanel p = (JPanel) panels[0].getComponent(0);
        p.remove(2);
        p.remove(1);

        dialog = JColorChooser.createDialog(button, "Space color picker", true, colorChooser, this, null);
        JRootPane rootPane = (JRootPane) dialog.getComponent(0);
        JLayeredPane layeredPane = (JLayeredPane) rootPane.getComponent(1);
        p = (JPanel) layeredPane.getComponent(0);
        p = (JPanel) p.getComponent(1);

        JButton okayButton = (JButton) p.getComponent(0);
        JButton cancelButton = (JButton) p.getComponent(1);
        JButton resetButton = (JButton) p.getComponent(2);
        p.remove(resetButton);
        p.add(cancelButton);
        p.add(okayButton);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (EDIT.equals(e.getActionCommand())) {
            button.setBackground(currentColor);
            colorChooser.setColor(currentColor);
            dialog.setVisible(true);

            fireEditingStopped();
        } else {
            currentColor = colorChooser.getColor();
        }
    }

    @Override
    public Object getCellEditorValue() {
        return currentColor;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        currentColor = (Color) value;
        return button;
    }

}
