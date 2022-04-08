package at.jku.anttracks.gui.utils;

import com.sun.javafx.scene.control.behavior.TabPaneBehavior;
import com.sun.javafx.scene.control.skin.TabPaneSkin;
import javafx.scene.control.Tab;

public class TabUtil {

    /**
     * Close a tab without requiring access to the containing tab pane (workaround, see https://bugs.openjdk.java.net/browse/JDK-8091261)
     *
     * @param tab the tab to be closed
     */
    public static void closeTab(Tab tab) {
        TabPaneBehavior behavior = ((TabPaneSkin) tab.getTabPane().getSkin()).getBehavior();
        if (behavior.canCloseTab(tab)) {
            behavior.closeTab(tab);
        }
    }
}
