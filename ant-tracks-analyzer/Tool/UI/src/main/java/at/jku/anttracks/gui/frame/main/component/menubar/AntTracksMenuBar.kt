
package at.jku.anttracks.gui.frame.main.component.menubar

import at.jku.anttracks.gui.dialog.about.AboutDialog
import at.jku.anttracks.gui.dialog.preference.PreferencesDialog
import at.jku.anttracks.gui.model.ClientInfo
import at.jku.anttracks.gui.model.IAppInfo
import at.jku.anttracks.gui.utils.AppLoader
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.gui.utils.OsScript
import at.jku.anttracks.gui.utils.WindowUtil
import at.jku.anttracks.util.safe
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.stage.FileChooser

class AntTracksMenuBar : MenuBar() {

    @FXML
    private lateinit var loadAntTracksTraceMenuItem: MenuItem
    @FXML
    private lateinit var loadHprofMenuItem: MenuItem
    //@FXML
    //private MenuItem preprocessMenuItem;
    @FXML
    private lateinit var exitMenuItem: MenuItem

    @FXML
    private lateinit var windowMenu: Menu
    @FXML
    private lateinit var extendedChartsMenuItem: CheckMenuItem
    @FXML
    private lateinit var preferencesMenuItem: MenuItem

    @FXML
    private lateinit var applicationMenu: Menu
    @FXML
    private lateinit var redefineMenuItem: MenuItem
    @FXML
    private lateinit var aliveDeadRelativeMenuItem: RadioMenuItem
    @FXML
    private lateinit var aliveDeadNObjectsMenuItem: RadioMenuItem
    @FXML
    private lateinit var aliveDeadBytesMenuItem: RadioMenuItem
    @FXML
    private lateinit var featureChartMenuItem: CheckMenuItem
    @FXML
    private lateinit var addDataStructureDefinitionFileMenuItem: MenuItem

    @FXML
    private lateinit var aboutMenuItem: MenuItem

    init {
        FXMLUtil.load(this, AntTracksMenuBar::class.java)
    }

    fun init() {
        initializeMenus()
    }

    fun setAppsEnabled(enabled: Boolean) {
        applicationMenu.isDisable = !enabled
    }

    private fun initializeMenus() {
        initializeFileMenu()
        initializeWindowMenu()
        initializeApplicationMenu()
        initializeHelpMenu()
    }

    private fun initializeFileMenu() {
        loadAntTracksTraceMenuItem.setOnAction { ae -> loadAntTracksTrace() }
        loadHprofMenuItem.setOnAction { ae -> loadHprof() }
        //preprocessMenuItem.setDisable(!OsScript.isSupported());
        //preprocessMenuItem.setOnAction(ae -> preprocessDirectory());
        exitMenuItem.setOnAction { ae -> System.exit(0) }
    }

    private fun initializeWindowMenu() {
        windowMenu.setOnShowing { ae -> extendedChartsMenuItem.isSelected = ClientInfo.isExtendedChartVisibility }
        extendedChartsMenuItem.setOnAction { ae -> switchExtendedChartVisibility() }
        preferencesMenuItem.setOnAction { ae -> showPreferences() }
    }

    private fun initializeApplicationMenu() {
        applicationMenu.setOnShowing { ae ->
            val appInfo = ClientInfo.getCurrentAppInfo()
            if (appInfo != null) {
                redefineMenuItem.isDisable = !appInfo.isParsingCompleted
                featureChartMenuItem.isDisable = appInfo.selectedFeaturesFile == null
                featureChartMenuItem.isSelected = appInfo.isShowFeatures
                when (appInfo.aliveDeadPanelType) {
                    IAppInfo.AliveDeadPanelType.RELATIVE -> aliveDeadRelativeMenuItem.isSelected = true
                    IAppInfo.AliveDeadPanelType.N_OBJECTS -> aliveDeadNObjectsMenuItem.isSelected = true
                    IAppInfo.AliveDeadPanelType.BYTE -> aliveDeadBytesMenuItem.isSelected = true
                }.safe
                addDataStructureDefinitionFileMenuItem.isDisable = !appInfo.isParsingCompleted
            }
        }
        redefineMenuItem.isDisable = !OsScript.isSupported()
        redefineMenuItem.setOnAction { ae -> reloadApp() }
        aliveDeadRelativeMenuItem.setOnAction { ae -> ClientInfo.getCurrentAppInfo()?.aliveDeadPanelType = IAppInfo.AliveDeadPanelType.RELATIVE }
        aliveDeadNObjectsMenuItem.setOnAction { ae -> ClientInfo.getCurrentAppInfo()?.aliveDeadPanelType = IAppInfo.AliveDeadPanelType.N_OBJECTS }
        aliveDeadBytesMenuItem.setOnAction { ae -> ClientInfo.getCurrentAppInfo()?.aliveDeadPanelType = IAppInfo.AliveDeadPanelType.BYTE }
        featureChartMenuItem.isDisable = !OsScript.isSupported()
        featureChartMenuItem.setOnAction { ae -> switchFeatureChartVisibility() }
        addDataStructureDefinitionFileMenuItem.setOnAction { ae ->
            val chooser = FileChooser()
            chooser.title = "Select data structure definition files..."
            chooser.extensionFilters.add(FileChooser.ExtensionFilter("Data structure definitions (*.ds)", "*.ds"))
            val dsFiles = chooser.showOpenMultipleDialog(ClientInfo.stage)
            if (dsFiles != null) {
                ClientInfo.getCurrentAppInfo()?.selectDataStructureDefinitionFiles(dsFiles.map { it.toURI() })
            }
        }
    }

    private fun initializeHelpMenu() {
        aboutMenuItem.setOnAction { ae -> showAbout() }
    }

    private fun loadAntTracksTrace() {
        AppLoader.loadAntTracksTrace()
    }

    private fun loadHprof() {
        AppLoader.loadHprof()
    }

    // private void preprocessDirectory() {
    //    AppLoader.preprocessDirectory();
    //}

    private fun switchExtendedChartVisibility() {
        ClientInfo.isExtendedChartVisibility = !ClientInfo.isExtendedChartVisibility
        ClientInfo.mainFrame.plotAllApps()
    }

    private fun showPreferences() {
        val preferencesDialog = PreferencesDialog()
        WindowUtil.centerInMainFrame(preferencesDialog)
        preferencesDialog.showAndWait()
    }

    private fun reloadApp() {
        AppLoader.reload()
    }

    private fun switchFeatureChartVisibility() {
        val appInfo = ClientInfo.getCurrentAppInfo()
        appInfo?.isShowFeatures = !(appInfo?.isShowFeatures ?: true)
    }

    fun showAbout() {
        val aboutDialog = AboutDialog()
        aboutDialog.init()
        WindowUtil.centerInMainFrame(aboutDialog)
        aboutDialog.showAndWait()
    }
}
