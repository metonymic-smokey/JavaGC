
package at.jku.anttracks.gui.utils;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.Region;

public class Errors {

    public static void display(Throwable error) {
        Alert alert = new Alert(AlertType.ERROR, "An error occured: " + error);
        alert.setTitle("Error");
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        WindowUtil.INSTANCE.centerInMainFrame(alert);
        alert.showAndWait();
    }

    private Errors() {
        throw new Error("Do not instantiate!");
    }

}
