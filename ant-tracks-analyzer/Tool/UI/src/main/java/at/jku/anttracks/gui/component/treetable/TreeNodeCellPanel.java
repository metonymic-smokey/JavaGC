package at.jku.anttracks.gui.component.treetable;

import at.jku.anttracks.gui.component.general.CompoundIcon;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static at.jku.anttracks.gui.utils.Consts.NODE_WITHOUT_ICON_ICON;

public class TreeNodeCellPanel<T> extends JPanel {
    private static final long serialVersionUID = 1072116199449437054L;

    private final JLabel expandCollapseIcon = new JLabel();
    private final JLabel label = new JLabel();

    private final NodeIcon collapsed;
    private final NodeIcon expanded;
    private final Function<TreeNode<T>, String> getTreeNodeText;

    public TreeNodeCellPanel(Function<TreeNode<T>, String> getTreeNodeText) {
        setLayout(new BorderLayout());

        collapsed = new NodeIcon('+');
        expanded = new NodeIcon('-');
        this.getTreeNodeText = getTreeNodeText;

        add(expandCollapseIcon, BorderLayout.WEST);
        add(label, BorderLayout.CENTER);
    }

    void updateValue(TreeNode<T> node) {
        int compensateIcon = 0;

        expandCollapseIcon.setIcon(null);
        label.setIcon(null);
        label.setText("");

        if (node == null) {
            label.setText("no data ...");
        } else {
            if (node.isRoot()) {
                label.setText(node.getTitle());
            } else {
                if (node.getChildCount() > 0) {
                    if (node.isExpanded()) {
                        expandCollapseIcon.setIcon(expanded);
                    } else {
                        expandCollapseIcon.setIcon(collapsed);
                    }
                } else {
                    expandCollapseIcon.setIcon(null);
                    compensateIcon = NodeIcon.SIZE;
                }
                label.setText(getTreeNodeText.apply(node));
                List<Icon> icons = new ArrayList<>();
                icons.add(node.getIcon() != null ? node.getIcon() : NODE_WITHOUT_ICON_ICON);
                if (node.getSubTreeLevel() > 0) {
                    // Concat icons if in subtree
                    TreeNode<T> current = node;
                    boolean notFirstInSubtree = false;
                    while (current.getParent() != null) {
                        if (current.getParent().getSubTreeLevel() != current.getSubTreeLevel()) {
                            // Concat
                            if (notFirstInSubtree) {
                                icons.add(0, current.getIcon() != null ? current.getIcon() : NODE_WITHOUT_ICON_ICON);
                            }
                            notFirstInSubtree = false;
                        } else {
                            notFirstInSubtree = true;
                        }
                        current = current.getParent();
                    }
                }

                label.setIcon(new CompoundIcon(icons.toArray(new Icon[icons.size()])));
            }
        }

        expandCollapseIcon.setBorder(BorderFactory.createEmptyBorder(5, node != null ? (node.getHierarchySperationWidth() + compensateIcon) : 0, 5, 10));
    }
}
