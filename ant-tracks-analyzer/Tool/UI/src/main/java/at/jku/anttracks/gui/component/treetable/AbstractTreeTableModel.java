
package at.jku.anttracks.gui.component.treetable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public abstract class AbstractTreeTableModel<T> implements TreeTableModelInterface<T> {

    private final Logger logger = Logger.getLogger("at.jku.anttracks.gui.detail.statistics.treetable.AbstractTreeTableModel");

    protected final TreeNode<T> root;
    private final List<TreeTableModelListener<T>> listeners = new CopyOnWriteArrayList<>();
    private final TreeNodeListener<T> nodeListener = new TreeNodeListener<T>() {
        @Override
        public void expanded(TreeNode<T> node) {
            for (int child = 0; child < node.getChildCount(); child++) {
                try {
                    node.children.get(child).addListener(nodeListener);
                } catch (NullPointerException npe) {
                    logger.warning("Listener could not be installed into child node #" + child + " of " + node + " [ChildrenCount: " + node.getChildCount() + "]\n" +
                                           npe);
                }
            }
            listeners.forEach(l -> l.expanded(node));
        }

        @Override
        public void collapsed(TreeNode<T> node) {
            listeners.forEach(l -> l.collapsed(node));
        }
    };

    public AbstractTreeTableModel(TreeNode<T> root) {
        this.root = root;

        // Forward information from each node listener to the
        // TreeTableModelListener
        if (this.root != null) {
            this.root.addListener(nodeListener);
        }

        this.root.setExpanded(false);
        this.root.setExpanded(true);
    }

    @Override
    public TreeNode<T> getRoot() {
        return root;
    }

    @Override
    public void addListener(TreeTableModelListener<T> l) {
        if (l != null) {
            listeners.add(l);
        }
    }

    @Override
    public void removeListener(TreeTableModelListener<T> l) {
        if (l != null) {
            listeners.remove(l);
        }
    }

    protected void fireStructureChange() {
        listeners.forEach(l -> l.structureChanged());
    }
}
