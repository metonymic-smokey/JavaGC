
package at.jku.anttracks.gui.component.general;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;

public class DropdownPanel<T> extends JPanel {

    private static final long serialVersionUID = -5988937161286854337L;

    private final static int labelWidth = 140;
    private final static int textWidth = 400;

    private final JLabel label;
    private JComboBox<String> dropdown = new JComboBox<>();
    private final List<T> data;

    public DropdownPanel(String labelText, List<T> data, ActionListener action) {
        super(new FlowLayout(FlowLayout.LEFT));
        this.data = data;
        label = new JLabel(labelText);
        label.setPreferredSize(new Dimension(labelWidth, 25));
        add(label);
        dropdown = new JComboBox<>();
        dropdown.setPreferredSize(new Dimension(textWidth, 25));
        dropdown.addActionListener(action);
        this.data.forEach(d -> {
            dropdown.addItem(d.toString());
        });
        add(dropdown);
    }

    public T getSelection() {
        return dropdown.getSelectedIndex() < 0 ? null : data.get(dropdown.getSelectedIndex());
    }
}
