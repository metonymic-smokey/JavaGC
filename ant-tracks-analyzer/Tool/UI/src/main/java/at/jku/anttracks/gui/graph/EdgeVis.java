package at.jku.anttracks.gui.graph;

import at.jku.anttracks.gui.utils.FXMLUtil;
import javafx.scene.shape.Line;

public class EdgeVis<T> extends Line {
    private NodeVis<T> nodeVisFrom;
    private NodeVis<T> nodeVisTo;

    public EdgeVis() {
        FXMLUtil.load(this, EdgeVis.class);
    }

    public void init(NodeVis<T> nodeVisFrom, NodeVis<T> nodeVisTo) {
        this.nodeVisFrom = nodeVisFrom;
        this.nodeVisTo = nodeVisTo;
    }

    public void redraw() {
        setStartX(nodeVisFrom.getLayoutX() + (nodeVisFrom.getWidth() / 2));
        setStartY(nodeVisFrom.getLayoutY() + nodeVisFrom.getHeight());
        setEndX(nodeVisTo.getLayoutX() + (nodeVisTo.getWidth() / 2));
        setEndY(nodeVisTo.getLayoutY());
        setVisible(nodeVisTo.isVisible());
    }

}
