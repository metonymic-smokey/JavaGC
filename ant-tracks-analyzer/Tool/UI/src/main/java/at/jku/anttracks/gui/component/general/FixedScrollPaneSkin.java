package at.jku.anttracks.gui.component.general;

import com.sun.javafx.scene.control.skin.ScrollPaneSkin;
import javafx.scene.control.ScrollPane;

public class FixedScrollPaneSkin extends ScrollPaneSkin {

    public FixedScrollPaneSkin(ScrollPane scroll) {
        super(scroll);
        scroll.hbarPolicyProperty().addListener((obs, oldV, newV) -> {
            // rude .. but visibility is updated in layout anyway
            FixedScrollPaneSkin.this.hsb.setVisible(false);
            FixedScrollPaneSkin.this.hsb.setManaged(false);
        });
    }

    @Override
    protected double computePrefHeight(double x, double topInset, double rightInset, double bottomInset, double leftInset) {
        double computed = super.computePrefHeight(x, topInset, rightInset, bottomInset, leftInset);
        //        System.out.println(this.getSkinnable().getViewportBounds().getWidth() + " VS " + ((Region) this.getSkinnable().getContent()).getWidth());
        if (getSkinnable().getHbarPolicy() == ScrollPane.ScrollBarPolicy.AS_NEEDED && hsb.isVisible()) {
            // this is fine when horizontal bar is shown/hidden due to resizing
            // not quite okay while toggling the policy
            // the actual visibilty is updated in layoutChildren?
            computed += hsb.prefHeight(-1);
        }
        return computed;
    }

}
