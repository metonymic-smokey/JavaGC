package at.jku.anttracks.gui.graph;

import javafx.event.Event;

public class ClickListener<T> {
    private GraphVis<T> graphVis;

    public ClickListener(GraphVis<T> graphVis) {
        this.graphVis = graphVis;
    }

    public void clicked(Event event) {
        Object object = event.getSource();
        if (object instanceof NodeVis<?>) {
            @SuppressWarnings("unchecked")
            NodeVis<T> nodeVis = (NodeVis<T>) object;
            graphVis.nodeClicked(nodeVis);

        }
    }

}
