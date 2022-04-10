
package at.jku.anttracks.gui.classification.component.configurationpane

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.ClassifierFactory
import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.FilterFactory
import at.jku.anttracks.gui.classification.classifier.component.selectionpane.ObjectClassifierSelectionPane
import at.jku.anttracks.gui.classification.component.selectionpane.ClassificationSelectionPane.ClassificationSelectionListener
import at.jku.anttracks.gui.classification.filter.OnlyDataStructureHeadsFilter
import at.jku.anttracks.gui.classification.filter.component.selectionpane.ObjectFilterSelectionPane
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.model.Description
import at.jku.anttracks.gui.model.IAvailableClassifierInfo
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.gui.utils.ImageUtil
import at.jku.anttracks.util.safe
import javafx.beans.DefaultProperty
import javafx.collections.ObservableList
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.layout.BorderPane
import javafx.scene.layout.VBox
import org.controlsfx.control.PopOver
import org.controlsfx.control.ToggleSwitch

@DefaultProperty(value = "extension")
abstract class ConfigurationPane : BorderPane() {
    enum class Mode {
        CONFIGURE,
        ANALYSIS,
        SWITCHABLE
    }

    @FXML
    lateinit var filterSelectionPane: ObjectFilterSelectionPane
    @FXML
    lateinit var classifierSelectionPane: ObjectClassifierSelectionPane
    @FXML
    lateinit var configureButton: Button
    @FXML
    private lateinit var extension: VBox
    @FXML
    lateinit var dataStructureSwitch: ToggleSwitch

    open val componentDescriptions: List<Triple<Node, Description, PopOver.ArrowLocation>> by lazy {
        listOf(Triple(filterSelectionPane.selectedPane,
                      Description("Currently selected filters")
                              .linebreak()
                              .appendDefault("Right-click them for settings"),
                      PopOver.ArrowLocation.RIGHT_TOP),
               Triple(classifierSelectionPane.selectedPane,
                      Description("Currently selected classifiers")
                              .linebreak()
                              .appendDefault("Right-click them for settings"),
                      PopOver.ArrowLocation.TOP_LEFT),
               Triple(dataStructureSwitch,
                      Description("Switch between data structure and object view"),
                      PopOver.ArrowLocation.BOTTOM_RIGHT))
    }

    lateinit var appInfo: AppInfo
        private set
    lateinit var availableClassifierInfo: IAvailableClassifierInfo
        private set
    lateinit var mode: Mode
        private set
    var isConfigureMode: Boolean = false
        protected set

    init {
        FXMLUtil.load(this, ConfigurationPane::class.java)
    }

    fun init(appInfo: AppInfo, availableClassifierInfo: IAvailableClassifierInfo, mode: Mode = Mode.SWITCHABLE, prohibitDuplicates: Boolean = false) {
        this.appInfo = appInfo
        this.mode = mode
        this.availableClassifierInfo = availableClassifierInfo

        val configureIcon = ImageUtil.getIconNode(Consts.SETTINGS_IMAGE, 24, 24)
        configureButton.graphic = configureIcon

        // vbox.setStyle("-fx-padding: 0px;");
        filterSelectionPane.init(availableClassifierInfo, 0, true)
        filterSelectionPane.addListener(createFilterListener())
        classifierSelectionPane.init(availableClassifierInfo, 0, prohibitDuplicates)
        classifierSelectionPane.addListener(createClassifierListener())

        initMode()

        // define data structure switch behaviour
        val onlyTopLevelDataStructureHeads = availableClassifierInfo.availableFilter.get(OnlyDataStructureHeadsFilter.NAME)
        dataStructureSwitch.selectedProperty().addListener { obs, wasSelected, isSelected ->
            if (!wasSelected && isSelected!!) {
                filterSelectionPane.addSelectedButNotShown(onlyTopLevelDataStructureHeads)
            } else if (wasSelected!! && !isSelected) {
                filterSelectionPane.removeSelectedButNotShown(onlyTopLevelDataStructureHeads)
            }
        }
    }

    // This method is needed to support custom components in JavaFX
    fun getExtension(): ObservableList<Node> {
        return extension.children
    }

    private fun initMode() {
        when (mode) {
            Mode.CONFIGURE -> {
                switchToConfigureMode()
                configureButton.isVisible = false
                configureButton.isManaged = false
            }
            Mode.ANALYSIS -> {
                switchToAnalysisMode()
                configureButton.isVisible = false
                configureButton.isManaged = false
            }
            Mode.SWITCHABLE -> {
                switchToConfigureMode()
                configureButton.isVisible = true
                configureButton.isManaged = true
            }
        }.safe
    }

    open fun switchMode() {
        if (mode == Mode.SWITCHABLE) {
            if (isConfigureMode) {
                switchToAnalysisMode()
            } else {
                switchToConfigureMode()
            }
        }
    }

    fun switchToConfigureMode() {
        if (mode == Mode.SWITCHABLE || mode == Mode.CONFIGURE) {
            filterSelectionPane.switchToConfigurationPerspective()
            classifierSelectionPane.switchToConfigurationPerspective()
            configureButton.isVisible = false
            configureButton.isManaged = false
            isConfigureMode = true
        }
    }

    fun switchToAnalysisMode() {
        if (mode == Mode.SWITCHABLE || mode == Mode.ANALYSIS) {
            filterSelectionPane.switchToAnalysisPerspective()
            classifierSelectionPane.switchToAnalysisPerspective()
            configureButton.isVisible = mode == Mode.SWITCHABLE
            configureButton.isManaged = mode == Mode.SWITCHABLE
            isConfigureMode = false
        }
    }

    protected abstract fun createClassifierListener(): ClassificationSelectionListener<Classifier<*>, ClassifierFactory>

    protected abstract fun createFilterListener(): ClassificationSelectionListener<Filter, FilterFactory>
}
