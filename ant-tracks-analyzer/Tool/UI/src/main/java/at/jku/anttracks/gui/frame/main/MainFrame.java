
package at.jku.anttracks.gui.frame.main;

import at.jku.anttracks.gui.component.actiontab.tab.ActionTab;
import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab;
import at.jku.anttracks.gui.frame.main.component.maintabbedpane.MainTabbedPane;
import at.jku.anttracks.gui.frame.main.component.menubar.AntTracksMenuBar;
import at.jku.anttracks.gui.frame.main.tab.application.ApplicationTab;
import at.jku.anttracks.gui.model.AppInfo;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import kotlin.Unit;

import java.util.logging.Logger;

public class MainFrame {

    @FXML
    private Node root;
    @FXML
    private AntTracksMenuBar menuBar;

    public MainTabbedPane getMainTabbedPane() {
        return mainTabbedPane;
    }

    @FXML
    private MainTabbedPane mainTabbedPane;
    @FXML
    private VBox statusPane;

    private final static Logger LOGGER = Logger.getLogger(MainFrame.class.getSimpleName());

    public void init() {
        menuBar.init();
        mainTabbedPane.init();
        mainTabbedPane.getSelectedTabListeners().add(() -> {
            menuBar.setAppsEnabled(mainTabbedPane.getCloseableTabCount() > 0);
            return Unit.INSTANCE;
        });

        Label noOperationsLabel = new Label("No operations running");
        statusPane.getChildren().add(noOperationsLabel);
        statusPane.getChildren().addListener((ListChangeListener<Node>) c -> {
            Platform.runLater(() -> {
                if (statusPane.getChildren().isEmpty()) {
                    statusPane.getChildren().add(noOperationsLabel);
                } else {
                    if (statusPane.getChildren().size() > 1) {
                        statusPane.getChildren().removeIf(node -> node.equals(noOperationsLabel));
                    }
                }
            });
        });
    }

    public void plotAllApps() {
        LOGGER.fine("Plot all apps");

        mainTabbedPane.getTopLevelTabs().stream().filter(tab -> tab instanceof ApplicationTab).map(tab -> (ApplicationTab) tab).forEach(applicationTab -> applicationTab.plot());
    }

    public void addTab(ActionTab tab) {
        mainTabbedPane.getTopLevelTabs().add(tab);
    }

    public void addTab(ActionTab parent, ActionTab tab) {
        parent.getChildTabs().add(tab);
    }

    public void addAndSelectTab(ActionTab tab) {
        addTab(tab);
        selectTab(tab);
    }

    public void addAndSelectTab(ActionTab parent, ActionTab tab) {
        addTab(parent, tab);
        selectTab(tab);
    }

    public ObservableList<ActionTab> getTopLevelTabs() {
        return mainTabbedPane.getTopLevelTabs();
    }

    public void selectTab(ActionTab tab) {
        mainTabbedPane.select(tab);
    }

    public void removeTab(ActionTab tab) {
        mainTabbedPane.remove(tab);
    }

    public ActionTab getCurrentTab() {
        return mainTabbedPane.getActiveTab();
    }

    public AppInfo getAppInfo() {
        if (mainTabbedPane.getActiveTab() instanceof ApplicationBaseTab) {
            return ((ApplicationBaseTab) mainTabbedPane.getActiveTab()).getAppInfo();
        }
        return null;
    }

    public Pane getStatusPane() {
        return statusPane;
    }

    public Node getRoot() {
        return root;
    }

    public AntTracksMenuBar getMenuBar() {
        return menuBar;
    }

}
