package at.jku.anttracks.gui.frame.main.tab.application.tab.web

import at.jku.anttracks.gui.component.antwebview.AntWebView
import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.application.model.ApplicationTabModel
import at.jku.anttracks.gui.model.IAppInfo
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.beans.property.SimpleStringProperty
import javafx.fxml.FXML

class ApplicationWebTab : ApplicationBaseTab() {
    @FXML
    lateinit var webView: AntWebView<ApplicationWebBridge>

    init {
        FXMLUtil.load(this, ApplicationWebTab::class.java)
    }

    fun init(model: ApplicationTabModel) {
        webView.init(javaClass.getResource("ApplicationWebTab.html"), ApplicationWebBridge())

        super.init(model.appInfo,
                   SimpleStringProperty("Experimental web-based analysis view"),
                   SimpleStringProperty("This view is currently not for actual use and is under heavy development"),
                   SimpleStringProperty("This view uses a WebView as main control. " +
                                                "It is used to evaluate the compatibility of JavaFX with web-based visualizations such as d3.js, dagre-js or plotly.js plots."),
                   null,
                   listOf(),
                   false
        )
    }

    override fun cleanupOnClose() {
        // Nothing to clean up
    }

    override fun appInfoChangeAction(type: IAppInfo.ChangeType) {
        // TODO currently ignored
    }
}