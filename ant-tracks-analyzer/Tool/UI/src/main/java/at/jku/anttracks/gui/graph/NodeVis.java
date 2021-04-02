package at.jku.anttracks.gui.graph;

import at.jku.anttracks.gui.utils.FXMLUtil;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

public class NodeVis<T> extends VBox {

    @FXML
    Label textLabel;
    @FXML
    Label additionalTextLabel;

    @FXML
    TitledPane additionalTextPane;

    private static final int DEPTH_DISTANCE = 50;

    private boolean expanded;

    private double modifier;
    private double change;
    private double shift;
    private NodeVis<T> ancestor = this;
    private NodeVis<T> thread = null;

    private Node<T> node;
    private NodeVis<T> parent;
    private List<ClickListener<T>> clickListeners = new ArrayList<>();
    private List<NodeVis<T>> kids;

    private Runnable doRedraw;

    public NodeVis() {
        FXMLUtil.load(this, NodeVis.class);
    }

    public void init(Node<T> node, NodeVis<T> parent, Runnable doRedraw) {
        this.doRedraw = doRedraw;
        this.node = node;
        this.parent = parent;
        kids = new ArrayList<>();
        Graph<T> graph = node.getGraph();
        // set Attributes
        textLabel.setGraphic(graph.getGraphic(node));
        String css = graph.getCSS(node);
        if (css != "") {
            this.setStyle(css);
        }
        textLabel.setText(graph.getText(node));
        textLabel.setTooltip(graph.getTooltip(node));
        additionalTextLabel.setText(graph.getAdditionalText(node));
        additionalTextPane.setAnimated(false);

        this.setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(1))));

        this.heightProperty().addListener((obs) -> {
            this.doRedraw.run();
        });

        if (additionalTextLabel.getText() == null || additionalTextLabel.getText().isEmpty()) {
            additionalTextPane.setVisible(false);
            additionalTextPane.setManaged(false);
        }
        setBackground(new Background(new BackgroundFill(graph.getColor(node), null, null)));

        setOnMouseClicked(new EventHandler<Event>() {

            @Override
            public void handle(Event event) {
                clickListeners.forEach(x -> x.clicked(event));
            }
        });
    }

    public List<NodeVis<T>> createChildren() {
        List<Node<T>> children = node.createChildren();
        for (Node<T> child : children) {
            NodeVis<T> nodeVis = addNodeVis(child);
            this.kids.add(nodeVis);
        }
        return this.kids;
    }

    private NodeVis<T> addNodeVis(Node<T> child) {
        NodeVis<T> nodeVis = new NodeVis<>();
        nodeVis.init(child, this, doRedraw);
        return nodeVis;
    }

    public void addListener(ClickListener<T> clickListener) {
        clickListeners.add(clickListener);
    }

    /**
     * @return the node
     */
    public Node<T> getNode() {
        return node;
    }

    public boolean isChildrenSet() {
        return node.isChildrenSet();
    }

    public List<NodeVis<T>> getKids() {
        return kids;
    }

    public void reset() {
        shift = 0;
        change = 0;
        modifier = 0;
        setX(0);
        setAncestor(this);
        setThread(null);
    }

    public List<NodeVis<T>> getVisibleChildren() {
        List<NodeVis<T>> children = new ArrayList<>();
        for (NodeVis<T> child : kids) {
            if (child.isVisible()) {
                children.add(child);
            }
        }
        return children;
    }

    public NodeVis<T> getChild(boolean left) {
        List<NodeVis<T>> children = getVisibleChildren();
        if (children.size() > 0) {
            if (left) {
                return children.get(0);
            } else {
                return children.get(children.size() - 1);
            }
        } else {
            return getThread();
        }
    }

    public NodeVis<T> getLeftMostSibling() {
        if (parent != null) {
            NodeVis<T> temp = parent.getVisibleChildren().get(0);
            if (!temp.equals(this)) {
                return temp;
            }
        }
        return null;
    }

    public NodeVis<T> getLeftSibling() {
        if (parent == null) {
            return null;
        }
        List<NodeVis<T>> children = parent.getVisibleChildren();
        int index = children.indexOf(this) - 1;
        if (index < 0 || index >= children.size()) {
            return null;
        }
        return children.get(index);
    }

    public boolean isSibling(NodeVis<T> sibling) {
        if (parent == null || sibling == null) {
            return false;
        }
        for (NodeVis<T> n : parent.getVisibleChildren()) {
            if (n.equals(sibling)) {
                return true;
            }
        }
        return false;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExtended(boolean extended) {
        this.expanded = extended;
    }

    public double getModifier() {
        return modifier;
    }

    public void setModifier(double mod) {
        modifier = mod;
    }

    public void addModifier(double shift) {
        modifier += shift;
    }

    public void setX(double x) {
        setLayoutX(x);
    }

    public void addX(double x) {
        setX(getX() + x);
    }

    public double getX() {
        return getLayoutX();
    }

    public void addChange(double d) {
        change += d;
    }

    public void addShift(double shift) {
        this.shift += shift;
    }

    public double getChange() {
        return change;
    }

    public double getShift() {
        return shift;
    }

    public NodeVis<T> getAncestor() {
        return ancestor;
    }

    public void setAncestor(NodeVis<T> a) {
        ancestor = a;
    }

    public NodeVis<T> getThread() {
        return thread;
    }

    public void setThread(NodeVis<T> sibling) {
        thread = sibling;
    }

    public void setY() {
        setLayoutY(parent == null ? 0 : parent.getLayoutY() + parent.getHeight() + DEPTH_DISTANCE);
    }
}


