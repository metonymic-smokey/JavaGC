package at.jku.anttracks.gui.graph;

import at.jku.anttracks.gui.utils.FXMLUtil;
import javafx.scene.layout.AnchorPane;

import java.util.List;

public class GraphVis<T> extends AnchorPane {

    private static final double BASIC_DISTANCE = 50;
    final boolean left = true;
    final boolean right = false;

    ClickListener<T> clickListener = new ClickListener<T>(this);
    NodeVis<T> root;

    public GraphVis() {
        FXMLUtil.load(this, GraphVis.class);
    }

    public void init(Graph<T> graph) {
        Node<T> node = graph.getNode();
        root = new NodeVis<>();
        root.init(node, null, () -> redraw());
        root.addListener(clickListener);
        this.getChildren().add(root);
        redraw();
    }

    public void nodeClicked(NodeVis<T> node) {
        if (node.isExpanded()) {
            node.setExtended(false);
            setVisible(node, false);
        } else {
            node.setExtended(true);
            if (node.isChildrenSet()) {
                setVisible(node, true);
            } else {
                List<NodeVis<T>> children = node.createChildren();
                for (NodeVis<T> child : children) {
                    child.addListener(clickListener);
                    this.getChildren().add(child);
                    addEdgeVis(node, child);
                }
            }
        }
        redraw();
    }

    private void setVisible(NodeVis<T> node, boolean visible) {
        if (node == null || node.getKids() == null) {
            return;
        }
        for (NodeVis<T> n : node.getKids()) {
            if (n.isVisible() != visible) {
                n.setVisible(visible);
            }
            if (!visible || n.isExpanded()) {
                setVisible(n, visible);
            }
        }
    }

    private void addEdgeVis(NodeVis<T> parent, NodeVis<T> child) {
        EdgeVis<T> edgeVis = new EdgeVis<>();
        edgeVis.init(parent, child);
        this.getChildren().add(edgeVis);
    }

    @SuppressWarnings("unchecked")
    private void redraw() {
        //using this algorithm: http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.16.8757&rep=rep1&type=pdf
        reset(root);
        firstWalk(root);
        double min = secondWalk(root, 0, Double.POSITIVE_INFINITY);
        if (min < 0) {
            thirdWalk(root, -min);
        }

        for (javafx.scene.Node n : this.getChildren()) {
            if (n instanceof EdgeVis<?>) {
                ((EdgeVis<T>) n).redraw();
            }
        }
        System.out.println();
    }

    public void reset(NodeVis<T> node) {
        node.reset();
        if (node.getKids() == null) {
            return;
        }
        for (NodeVis<T> n : node.getKids()) {
            reset(n);
        }
    }

    private void firstWalk(NodeVis<T> node) {
        List<NodeVis<T>> children = node.getVisibleChildren();
        if (children.size() == 0) {
            if (node.getLeftSibling() != null) {
                node.setX(node.getLeftSibling().getX() + node.getLeftSibling().getWidth() + BASIC_DISTANCE);
            } else {
                node.setX(0);
            }
        } else {
            NodeVis<T> defaultAncestor = children.get(0);
            for (NodeVis<T> n : children) {
                firstWalk(n);
                defaultAncestor = apportion(n, defaultAncestor);
            }
            //        	executeShifts(node);
            double midpoint = (children.get(0).getX() +
                                       children.get(children.size() - 1).getX()) / 2;

            NodeVis<T> leftSibling = node.getLeftSibling();
            if (leftSibling != null) {
                double x = leftSibling.getX() + leftSibling.getWidth() + BASIC_DISTANCE;
                node.setX(x);
                node.setModifier(x - midpoint);
            } else {
                node.setX(midpoint);
            }

        }
    }

    private NodeVis<T> apportion(NodeVis<T> node, NodeVis<T> defaultAncestor) {
        NodeVis<T> leftSibling = node.getLeftSibling();
        if (leftSibling != null) {
            NodeVis<T> innerRight = node;
            NodeVis<T> outerRight = node;
            NodeVis<T> innerLeft = leftSibling;
            NodeVis<T> outerLeft = node.getLeftMostSibling();
            double shiftInnerRight = node.getModifier();
            double shiftOuterRight = node.getModifier();
            double shiftInnerLeft = innerLeft.getModifier();
            double shiftOuterLeft = outerLeft.getModifier();
            while (innerLeft.getChild(right) != null &&
                    innerRight.getChild(left) != null) {
                innerLeft = innerLeft.getChild(right);
                innerRight = innerRight.getChild(left);
                outerLeft = outerLeft.getChild(left);
                outerRight = outerRight.getChild(right);
                outerRight.setAncestor(node);
                double shift = (innerLeft.getX() + shiftInnerLeft) -
                        (innerRight.getX() + shiftInnerRight) + BASIC_DISTANCE
                        + innerRight.getWidth();
                if (shift > 0) {
                    NodeVis<T> ancestor = getAncestor(innerLeft, node, defaultAncestor);
                    moveSubtree(ancestor, node, shift);
                    shiftInnerRight += shift;
                    shiftOuterRight += shift;
                }
                shiftInnerLeft += innerLeft.getModifier();
                shiftOuterLeft += outerLeft.getModifier();
                shiftInnerRight += innerRight.getModifier();
                shiftOuterRight += outerRight.getModifier();
            }
            if (innerLeft.getChild(right) != null &&
                    outerRight.getChild(right) == null) {
                outerRight.setThread(innerLeft.getChild(right));
                outerRight.addModifier(shiftInnerLeft - shiftOuterRight);
            } else if (innerRight.getChild(left) != null &&
                    outerLeft.getChild(left) == null) {
                outerLeft.setThread(innerRight.getChild(left));
                outerLeft.addModifier(shiftInnerRight - shiftOuterLeft);
                defaultAncestor = node;
            }
        }
        return defaultAncestor;
    }

    private void moveSubtree(NodeVis<T> left, NodeVis<T> right, double shift) {
        int subtrees = right.getVisibleChildren().size() - left.getVisibleChildren().size();
        if (subtrees != 0) {
            right.addChange(-(shift / subtrees));
        }
        right.addShift(shift);
        if (subtrees != 0) {
            left.addChange(shift / subtrees);
        }
        right.addX(shift);
        right.addModifier(shift);
    }

    //Replaced with thirdWalk
    //	private void executeShifts(NodeVis<T> node) {
    //		double shift = 0;
    //		double change = 0;
    //		List<NodeVis<T>> children = node.getVisibleChildren();
    //		for (int i=children.size()-1; i>=0; i--) {
    //			NodeVis<T> n = children.get(i);
    //			n.addX(shift);
    //			n.addModifier(shift);
    //			change += n.getChange();
    //			shift += n.getShift() + change;
    //		}
    //	}

    private NodeVis<T> getAncestor(NodeVis<T> innerLeft, NodeVis<T> node, NodeVis<T> defaultAncestor) {
        if (node.isSibling(innerLeft.getAncestor())) {
            return innerLeft.getAncestor();
        }
        return defaultAncestor;
    }

    private double secondWalk(NodeVis<T> node, double m, double min) {
        node.addX(m);
        node.setY();
        min = Math.min(node.getX(), min);
        for (NodeVis<T> n : node.getVisibleChildren()) {
            min = secondWalk(n, m + node.getModifier(), min);
        }
        return min;
    }

    private void thirdWalk(NodeVis<T> node, double min) {
        node.addX(min);
        for (NodeVis<T> n : node.getVisibleChildren()) {
            thirdWalk(n, min);
        }
    }
}
