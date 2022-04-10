
package at.jku.anttracks.gui.classification.component.arrowpane;

import at.jku.anttracks.gui.utils.Consts;
import at.jku.anttracks.gui.utils.ImageUtil;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class ArrowPane extends HBox {

    public ArrowPane() {
        setAlignment(Pos.CENTER);
        getChildren().add(new Label("", ImageUtil.getIconNode(Consts.ARROW_IMAGE)));
    }
}
