
package at.jku.anttracks.gui.frame.main.component.maintabbedpane

import at.jku.anttracks.gui.component.actiontab.pane.ActionTabbedPane
import at.jku.anttracks.gui.frame.main.tab.fallback.FallbackTab
import at.jku.anttracks.gui.utils.FXMLUtil

class MainTabbedPane : ActionTabbedPane() {
    private var fallbackTab = FallbackTab()

    init {
        FXMLUtil.load(this, MainTabbedPane::class.java)
    }

    override fun init() {
        fallbackTab.init()
        topLevelTabs.add(fallbackTab)
        super.init()
    }

    /*
    private void initAddTab() {
        addTab = new Tab();
        addTab.setText("+");
        addTab.setClosable(false);
        addTab.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (!oldValue && newValue) {
                AppLoader.load();
            }
            if (tabPane.getSelectionModel().getSelectedIndex() == tabPane.getTabs().size() - 1) {
                tabPane.getSelectionModel().selectPrevious();
            }
        });
    }
    */
}
