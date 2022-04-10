package at.jku.anttracks.gui.component.general;

import at.jku.anttracks.gui.utils.FXMLUtil;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;

public class HorizontalScrollPane extends ScrollPane {

    private boolean scrollBarIncluded = true;

    public HorizontalScrollPane() {
        FXMLUtil.load(this, HorizontalScrollPane.class);

        /*
        this.widthProperty().addListener((observable, oldValue, newValue) -> {
            Region content = (Region) this.getContent();
            if (newValue.doubleValue() < content.getWidth()) {
                if (!scrollBarIncluded) {
                    scrollBarIncluded = true;
                    content.setPrefHeight(content.getHeight() + 16);
                }
            } else {
                if (scrollBarIncluded) {
                    scrollBarIncluded = false;
                    content.setPrefHeight(content.getHeight() - 16);
                }
            }
        });
        */
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new FixedScrollPaneSkin(this);
    }
}
