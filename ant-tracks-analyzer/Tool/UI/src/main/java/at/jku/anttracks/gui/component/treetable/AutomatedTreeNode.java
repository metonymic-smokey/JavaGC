
package at.jku.anttracks.gui.component.treetable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AutomatedTreeNode<CONTAINER, DATA> extends TreeNode<DATA> {

    private boolean childrenSet = false;
    private boolean dataSet = false;
    private boolean subTreeLevelSet = false;
    boolean iconSet = false;

    private final CONTAINER container;

    private final Function<CONTAINER, String> titleFunction;
    private final Function<CONTAINER, DATA> dataFunction;
    private final Function<CONTAINER, Collection<? extends CONTAINER>> childFunction;
    private final Function<CONTAINER, Integer> subTreeLevelFunction;
    private final Function<CONTAINER, Icon> iconFunction;

    public AutomatedTreeNode(CONTAINER container,
                             Function<CONTAINER, String> titleFunction,
                             Function<CONTAINER, DATA> dataFunction,
                             Function<CONTAINER, Collection<? extends CONTAINER>> childFunction,
                             Function<CONTAINER, Integer> subTreeLevelFunction,
                             Function<CONTAINER, Icon> iconFunction) {
        this(container, titleFunction, dataFunction, childFunction, subTreeLevelFunction, iconFunction, null);
    }

    public AutomatedTreeNode(CONTAINER container,
                             Function<CONTAINER, String> titleFunction,
                             Function<CONTAINER, DATA> dataFunction,
                             Function<CONTAINER, Collection<? extends CONTAINER>> childFunction,
                             Function<CONTAINER, Integer> subTreeLevelFunction,
                             Function<CONTAINER, Icon> iconFunction,
                             AutomatedTreeNode<CONTAINER, DATA> parent) {
        super(null);
        this.container = container;
        this.titleFunction = titleFunction;
        this.dataFunction = dataFunction;
        this.childFunction = childFunction;
        this.subTreeLevelFunction = subTreeLevelFunction;
        this.iconFunction = iconFunction;
        this.parent = parent;
        hierarchyLevel = this.parent == null ? 0 : this.parent.getHierarchyLevel() + 1;
    }

    public CONTAINER getContainer() {
        return container;
    }

    @Override
    public String getTitle() {
        if (title != null) {
            return title;
        }
        title = titleFunction.apply(container);
        return title;
    }

    @Override
    public DATA getData() {
        if (dataSet) {
            return data;
        }

        if (dataFunction == null) {
            return null;
        }
        data = dataFunction.apply(container);
        dataSet = true;
        return data;
    }

    @Override
    public List<TreeNode<DATA>> getChildren() {
        if (childrenSet) {
            return children;
        }

        if (childFunction == null) {
            return new ArrayList<>();
        }
        Collection<? extends CONTAINER> calcChildren = childFunction.apply(container);
        if (calcChildren == null) {
            return new ArrayList<>();
        }
        children = calcChildren.stream()
                               .map(childContainer -> new AutomatedTreeNode<>(childContainer,
                                                                              titleFunction,
                                                                              dataFunction,
                                                                              childFunction,
                                                                              subTreeLevelFunction,
                                                                              iconFunction,
                                                                              this))
                               .collect(Collectors.toList());
        childrenSet = true;
        return children;
    }

    @Override
    public int getSubTreeLevel() {
        if (subTreeLevelSet) {
            return subTreeLevel;
        } else {
            if (subTreeLevelFunction == null) {
                return -1;
            }
            subTreeLevel = subTreeLevelFunction.apply(container);
            subTreeLevelSet = true;
            return subTreeLevel;
        }
    }

    @Override
    public Icon getIcon() {
        if (iconSet) {
            return icon;
        } else {
            if (iconFunction == null) {
                return null;
            }
            icon = iconFunction.apply(container);
            iconSet = true;
            return icon;
        }
    }

    @Override
    public int getChildCount() {
        return getChildren().size();
    }

    @Override
    public TreeNode<DATA> getChildAt(int index) {
        if (index >= getChildren().size()) {
            return null;
        }
        return getChildren().get(index);
    }

    @Override
    public void addChild(TreeNode<DATA> node) {
        throw new UnsupportedOperationException("Data is calculated based on underlying data structure. Don't change data in this UI object");
    }

    @Override
    public TreeNode<DATA> addChild(String title, DATA data, int hierarchyLevel) {
        throw new UnsupportedOperationException("Data is calculated based on underlying data structure. Don't change data in this UI object");
    }

    @Override
    public void removeAllChildren() {
        throw new UnsupportedOperationException("Data is calculated based on underlying data structure. Don't change data in this UI object");
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getData() == null) ? 0 : getData().hashCode());
        result = prime * result + getHierarchyLevel();
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
        if (getData() == null) {
            if (other.getData() != null) {
                return false;
            }
        } else if (!getData().equals(other.getData())) {
            return false;
        }
        return getHierarchyLevel() == other.getHierarchyLevel();
    }
}
