
package at.jku.anttracks.gui.frame.main.tab.fallback

import at.jku.anttracks.gui.component.actiontab.ActionTabAction
import at.jku.anttracks.gui.frame.main.component.applicationbase.IdeasEnabledTab
import at.jku.anttracks.gui.model.*
import at.jku.anttracks.gui.utils.AppLoader
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.fxml.FXML
import javafx.scene.control.Button
import org.controlsfx.control.PopOver

class FallbackTab : IdeasEnabledTab() {
    @FXML
    private lateinit var loadAntTracksTraceButton: Button
    @FXML
    private lateinit var loadHPROFButton: Button

    override val componentDescriptions by lazy {
        listOf(Triple(loadAntTracksTraceButton,
                      Description("Load an AntTracks trace file to analyze it."),
                      PopOver.ArrowLocation.RIGHT_CENTER),
               Triple(loadHPROFButton,
                      Description("Load an HPROF (heap dump) file to analyze it."),
                      PopOver.ArrowLocation.RIGHT_CENTER),
               Triple(ClientInfo.mainFrame.mainTabbedPane.tabPanels,
                      Description("This is the tab panel.")
                              .linebreak()
                              .appendDefault("Use it to navigate around AntTracks and to keep track of your opened views."),
                      PopOver.ArrowLocation.LEFT_TOP),
               Triple(ClientInfo.mainFrame.mainTabbedPane.actionPanels,
                      Description("This is the action panel.")
                              .linebreak()
                              .appendDefault("In here appear the main actions you can perform on the current tab."),
                      PopOver.ArrowLocation.BOTTOM_CENTER),
               Triple(ClientInfo.mainFrame.statusPane, Description("This is the operations pane.")
                       .linebreak()
                       .appendDefault("All currently running tasks will indicate their progress here."),
                      PopOver.ArrowLocation.BOTTOM_CENTER),
               Triple(ClientInfo.mainFrame.mainTabbedPane.ideasIcon.parent,
                      Description("We know that AntTracks can be quite confusing to use sometimes :(")
                              .linebreak()
                              .appendDefault("To make it easier for you we put this lightbulb here - when it turns on, AntTracks has some ideas for the current analysis that might help you.")
                              .linebreak()
                              .appendDefault("Simply hover the lightbulb to check them out!"),
                      PopOver.ArrowLocation.TOP_RIGHT))
    }

    override val initialTabIdeas by lazy {
        listOf(Idea("Welcome to AntTracks!",
                    Description("In here, AntTracks will display ideas to help you during the analysis process.")
                            .linebreak()
                            .appendDefault("Good luck!"),
                    listOf("Help, I'm lost!" does { showComponentDescriptions() },
                           "Who are you?" does { ClientInfo.mainFrame.menuBar.showAbout() }),
                    null,
                    this),
               Idea("Load a trace file!",
                    Description("Get started by loading a ")
                            e "trace file "
                            a "!",
                    listOf("Load a trace file" does { loadAntTracksTrace() }),
                    listOf(loadAntTracksTraceButton at Idea.BulbPosition.TOP_RIGHT),
                    this))
    }

    init {
        FXMLUtil.load(this, FallbackTab::class.java)
    }

    fun init() {
        super.init(
                SimpleStringProperty("Welcome!"),
                SimpleStringProperty("Please load a trace file."),
                SimpleStringProperty("Please load a trace file. The trace file has to be located next to the symbols file and may be accompanied by a class definitions file."),
                Consts.ROCKET_ICON,
                listOf(
                        ActionTabAction("Load AntTracks trace file",
                                        "Load an AntTracks trace file for analysis",
                                        "Import",
                                        SimpleBooleanProperty(true),
                                        null,
                                        ::loadAntTracksTrace),
                        ActionTabAction("Load HPROF file (experimental)",
                                        "This feature is currently under construction. Load an HPROF file for analysis",
                                        "Import",
                                        SimpleBooleanProperty(true),
                                        null,
                                        ::loadHprofFile)),
                false)
    }

    fun loadAntTracksTrace() {
        AppLoader.loadAntTracksTrace()
    }

    fun loadHprofFile() {
        AppLoader.loadHprof();
    }
}