package at.jku.anttracks.gui.graph;

import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class Graph<T> {
    private final Node<T> rootNode;

    private final Function<T, String> textFunction;
    private final Function<T, Color> colorFunction;
    private final Function<T, String> tooltipFunction;
    private final Function<T, List<T>> childFunction;
    private final Function<T, String> cssFunction;
    private final Function<T, String> additionalTextFunction;
    private final Function<T, javafx.scene.Node> iconFunction;

    public Graph(T root, Function<T, List<T>> childFunctions,
                 Function<T, String> textFunction,
                 Function<T, Color> colorFunction,
                 Function<T, String> tooltipFunction,
                 Function<T, javafx.scene.Node> iconFunction,
                 Function<T, String> cssFunction,
                 Function<T, String> additionalTextFunction) {
        rootNode = new Node<T>(this, root);
        this.childFunction = childFunctions;
        this.textFunction = textFunction;
        this.colorFunction = colorFunction;
        this.tooltipFunction = tooltipFunction;
        this.cssFunction = cssFunction;
        this.additionalTextFunction = additionalTextFunction;
        this.iconFunction = iconFunction;

    }

    public Node<T> getNode() {
        return rootNode;
    }

    public List<T> getChildren(Node<T> node) {
        if (childFunction == null) { return new ArrayList<T>(); }
        return childFunction.apply(node.getData());
    }

    public String getText(Node<T> node) {
        if (textFunction == null) { return ""; }
        return textFunction.apply(node.getData());
    }

    public Color getColor(Node<T> node) {
        if (colorFunction == null) { return Color.TRANSPARENT; }
        return colorFunction.apply(node.getData());
    }

    public Tooltip getTooltip(Node<T> node) {
        if (tooltipFunction == null) { return new Tooltip(""); }
        return new Tooltip(tooltipFunction.apply(node.getData()));
    }

    public javafx.scene.Node getGraphic(Node<T> node) {
        if (iconFunction == null) { return null; }
        return iconFunction.apply(node.getData());
    }

    public String getAdditionalText(Node<T> node) {
        if (additionalTextFunction == null) { return ""; }
        return additionalTextFunction.apply(node.getData());
    }

    public String getCSS(Node<T> node) {
        if (cssFunction == null) { return ""; }
        return cssFunction.apply(node.getData());
    }
}
