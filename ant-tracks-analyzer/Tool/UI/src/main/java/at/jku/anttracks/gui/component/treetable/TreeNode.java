
package at.jku.anttracks.gui.component.treetable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TreeNode<T> implements Comparable<TreeNode<T>> {
    public interface TreeNodeVisitor<Z> {
        void visit(TreeNode<Z> node);
    }

    private static final int HIERARCHY_SEPARATION_SIZE = 15;

    private final List<TreeNodeListener<T>> listeners = new CopyOnWriteArrayList<>();
    private boolean expanded;

    /**
     * Title is used when either (1) no data is set or (2) the node is root
     */
    protected String title;
    protected TreeNode<T> parent;
    protected Icon icon;
    protected T data;
    protected int hierarchyLevel;
    protected int subTreeLevel;
    protected List<TreeNode<T>> children = new ArrayList<>();

    public TreeNode(String title) {
        this(title, null, null);
    }

    private TreeNode(String title, T data, TreeNode<T> parent) {
        this(title, data, parent, 0);
    }

    private TreeNode(String title, T data, TreeNode<T> parent, int subTreeLevel) {
        this.title = title;
        this.data = data;
        this.parent = parent;
        this.subTreeLevel = subTreeLevel;
    }

    public boolean isRoot() {
        return parent == null;
    }

    public String getTitle() {
        return title;
    }

    public T getData() {
        return data;
    }

    public int getLevel() {
        int level = 0;
        TreeNode<T> ancestor = parent;
        while (ancestor != null) {
            level++;
            ancestor = ancestor.parent;
        }
        return level;
    }

    public TreeNode<T> getParent() {
        return parent;
    }

    public List<TreeNode<T>> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public int getChildCount() {
        return children.size();
    }

    public TreeNode<T> getChildAt(int index) {
        if (index >= children.size()) {
            return null;
        }
        return children.get(index);
    }

    public void addChild(TreeNode<T> node) {
        if (node.getParent() != this) {
            throw new IllegalArgumentException();
        }
        this.children.add(node);
    }

    public TreeNode<T> addChild(String title, T data, int hierarchyLevel) {
        TreeNode<T> node = this.addChild(title, data);
        node.hierarchyLevel = hierarchyLevel;
        return node;
    }

    public void removeAllChildren() {
        children.clear();
    }

    public void setExpanded(boolean expanded) {
        boolean fire = this.expanded != expanded;
        this.expanded = expanded;
        if (fire) {
            if (expanded) {
                listeners.forEach(l -> l.expanded(this));
            } else {
                listeners.forEach(l -> l.collapsed(this));
            }
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    public String getHierarchy() {
        StringBuilder hierarchy = new StringBuilder();
        for (int i = 0; i < hierarchyLevel; i++) {
            hierarchy.append("  ");
        }
        return hierarchy.toString();
    }

    public int getHierarchySperationWidth() {
        return hierarchyLevel * HIERARCHY_SEPARATION_SIZE;
    }

    public void addListener(TreeNodeListener<T> listener) {
        if (listener != null) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
    }

    public void removeListener(TreeNodeListener<T> listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    @Override
    public int compareTo(TreeNode<T> that) {
        if (this == that) {
            return 0;
        } else if (this.getParent() != that.getParent()) {
            throw new IllegalArgumentException();
        } else {
            return this.getChildren().indexOf(that) - that.getChildren().indexOf(this);
        }
    }

    @Override
    public String toString() {
        return getTitle();
    }

    private TreeNode<T> addChild(String title, T data) {
        TreeNode<T> match = null;
        int i = 0;
        while (i < this.children.size() && match == null) {
            TreeNode<T> child = this.children.get(i);
            if (child.getData() == data) {
                match = child;
            }
            i++;
        }

        if (match == null) {
            match = new TreeNode<>(title, data, this);
            this.children.add(match);
        }

        return match;
    }

    public void visit(TreeNodeVisitor<T> visitor) {
        visitor.visit(this);
        children.forEach(x -> x.visit(visitor));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((data == null) ? 0 : data.hashCode());
        result = prime * result + hierarchyLevel;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        TreeNode<?> other = (TreeNode<?>) obj;
        if (data == null) {
            if (other.data != null) {
                return false;
            }
        } else if (!data.equals(other.data)) {
            return false;
        }
        return hierarchyLevel == other.hierarchyLevel;
    }

    public int getHierarchyLevel() {
        return hierarchyLevel;
    }

    public int getSubTreeLevel() {
        return subTreeLevel;
    }

    public Icon getIcon() {
        return icon;
    }

    public void setIcon(Icon icon) {
        this.icon = icon;
    }
}
