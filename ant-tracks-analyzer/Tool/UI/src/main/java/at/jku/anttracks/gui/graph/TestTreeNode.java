package at.jku.anttracks.gui.graph;

import java.util.ArrayList;
import java.util.List;

public class TestTreeNode<T> {
    private T data;
    private List<TestTreeNode<T>> children;

    public TestTreeNode(T data) {
        this.data = data;
        children = new ArrayList<TestTreeNode<T>>();
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public List<TestTreeNode<T>> getChildren() {
        return children;
    }

    public void setChildren(List<TestTreeNode<T>> children) {
        this.children = children;
    }

    public void addChild(TestTreeNode<T> child) {
        children.add(child);
    }

    @Override
    public String toString() {
        return data.toString();
    }
}
