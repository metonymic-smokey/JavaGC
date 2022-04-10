
package at.jku.anttracks.gui.component.treetable;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TreeContextMenuItem extends JMenuItem {

    private static final long serialVersionUID = -4187164408336680439L;

    private final Supplier<String> textSupplier;
    private final Consumer<MouseEvent> onClick;

    public TreeContextMenuItem(Supplier<String> textSupplier, Consumer<MouseEvent> onClick) {
        super(textSupplier.get());
        this.textSupplier = textSupplier;
        this.onClick = onClick;

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                TreeContextMenuItem.this.onClick.accept(e);
            }
        });
    }

    public void updateText() {
        setText(textSupplier.get());
    }
}
