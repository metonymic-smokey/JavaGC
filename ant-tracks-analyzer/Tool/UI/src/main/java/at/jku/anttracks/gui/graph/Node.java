package at.jku.anttracks.gui.graph;

import java.util.ArrayList;
import java.util.List;

public class Node<T> {

    private final T data;
    private Graph<T> graph;
    private boolean childrenSet = false;
    private List<Node<T>> children;

    public Node(Graph<T> graph, T data) {
        this.data = data;
        this.graph = graph;
    }

    public List<Node<T>> createChildren() {
        children = new ArrayList<>();
        List<T> childrenList = graph.getChildren(this);
        for (T t : childrenList) {
            Node<T> node = new Node<T>(graph, t);
            children.add(node);
        }
        childrenSet = true;
        return children;
    }

    public boolean isChildrenSet() {
        return childrenSet;
    }

    public T getData() {
        return data;
    }

    public Graph<T> getGraph() {
        return graph;
    }

}
