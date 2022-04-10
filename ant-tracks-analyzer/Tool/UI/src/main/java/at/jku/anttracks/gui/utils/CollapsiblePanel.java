
package at.jku.anttracks.gui.utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.CopyOnWriteArrayList;

public class CollapsiblePanel<T extends Component> extends JPanel {

    private static final long serialVersionUID = 1L;

    public static final int NORTH = 0, WEST = 1, SOUTH = 2, EAST = 3;
    private static final int BUTTON_SIZE = 5;

    private final JScrollPane contentScrollPane;
    private final JPanel contentPanel;
    private T content;

    private boolean expanded = true;
    private JPanel collapseButtonPanel;
    private CollapsibleButton collapseButton;

    private final CopyOnWriteArrayList<ExpansionListener> listener;

    public CollapsiblePanel(int orientation, String title) {
        super(new BorderLayout());

        assert orientation >= NORTH && orientation <= EAST : "Invalid orientation";
        super.setBorder(BorderFactory.createTitledBorder(title));

        this.contentPanel = new JPanel(new BorderLayout());
        this.contentScrollPane = new JScrollPane(this.contentPanel);
        this.contentScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        this.contentScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        add(this.contentScrollPane, BorderLayout.CENTER);

        createCollapsePanel(orientation);
        this.collapseButton.removeMouseListener(this.collapseButton.getMouseListeners()[0]);
        this.collapseButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                switchExpansion();
            }
        });

        this.listener = new CopyOnWriteArrayList<>();
    }

    public CollapsiblePanel(int orientation, String title, T content) {
        this(orientation, title);
        setContent(content);
    }

    public void addListener(ExpansionListener l) {
        this.listener.add(l);
    }

    private void createCollapsePanel(int orientation) {

        String layout;

        if (orientation == NORTH) {
            layout = BorderLayout.PAGE_START;
        } else if (orientation == EAST) {
            layout = BorderLayout.LINE_END;
        } else if (orientation == SOUTH) {
            layout = BorderLayout.PAGE_END;
        } else {
            layout = BorderLayout.LINE_START;
        }

        this.collapseButton = new CollapsibleButton(orientation, BUTTON_SIZE - 1);
        this.collapseButtonPanel = new JPanel(new BorderLayout());
        this.collapseButtonPanel.add(this.collapseButton, BorderLayout.CENTER);
        this.collapseButtonPanel.setBackground(Color.RED);
        add(this.collapseButtonPanel, layout);
    }

    public void collapse() {
        if (!this.expanded) {
            return;
        }

        switchExpansion();
    }

    public void expand() {
        if (this.expanded) {
            return;
        }

        switchExpansion();
    }

    public T getContent() {
        return this.content;
    }

    public void setContent(T content) {
        if (this.content != null) {
            this.contentPanel.remove(content);
            content = null;
        }
        this.content = content;
        this.contentPanel.add(this.content, BorderLayout.CENTER);
    }

    private void switchExpansion() {
        this.expanded = !this.expanded;

        if (this.expanded) {
            this.contentScrollPane.setVisible(true);
            this.listener.forEach(x -> x.expanded());

        } else {
            this.contentScrollPane.setVisible(false);
            this.listener.forEach(x -> x.collapsed());
        }

        this.collapseButton.updateExpanded(this.expanded);

        revalidate();
        repaint();
    }

    public interface ExpansionListener {
        void expanded();

        void collapsed();
    }
}
