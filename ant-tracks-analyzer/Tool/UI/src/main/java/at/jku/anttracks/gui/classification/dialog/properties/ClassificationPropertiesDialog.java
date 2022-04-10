
package at.jku.anttracks.gui.classification.dialog.properties;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierProperty;
import at.jku.anttracks.gui.model.IAvailableClassifierInfo;
import at.jku.anttracks.gui.utils.NodeUtil;
import at.jku.anttracks.gui.utils.NodeUtil.AccessibleNode;
import at.jku.anttracks.gui.utils.WindowUtil;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ClassificationPropertiesDialog extends Dialog<Boolean> {

    private class ClassifierPropertyInfos<T> extends ClassifierProperty<T> {
        private Supplier<T> uiGetter;
        private Consumer<T> uiSetter;
        private boolean fullyInitialized = true;

        @SuppressWarnings("unchecked")
        private ClassifierPropertyInfos(ClassifierProperty<?> property, Supplier<?> uiGetter, Consumer<?> uiSetter) {
            this(((ClassifierProperty<T>) property).getClassifier(), property.getField(), (Supplier<T>) uiGetter, (Consumer<T>) uiSetter);
        }

        private ClassifierPropertyInfos(Classifier<?> classifier, Field field, Supplier<T> uiGetter, Consumer<T> uiSetter) {
            super(classifier, field);
            this.uiGetter = uiGetter;
            if (uiGetter == null) {
                fullyInitialized = false;
            }
            this.uiSetter = uiSetter;
            if (uiSetter == null) {
                fullyInitialized = false;
            }

            classifierToUi();
        }

        /**
         * Copies the data that is currently visible on the UI into the underlying data
         *
         * @return True if the underlying data changed (i.e., if something was changed on the UI), otherwise false
         */
        private boolean uiToClassifier() {
            if (fullyInitialized) {
                T propertyValue = propertyGetter.get();
                T uiValue = uiGetter.get();
                if ((propertyValue != null && uiValue != null && !propertyValue.equals(uiValue)) ||
                        (propertyValue == null && uiValue != null) ||
                        (propertyValue != null && uiValue == null)) {
                    propertySetter.accept(uiValue);
                    return true;
                }
            }
            return false;
        }

        private void classifierToUi() {
            if (fullyInitialized) {
                uiSetter.accept(propertyGetter.get());
            }
        }
    }

    private final List<ClassifierPropertyInfos<?>> properties = new ArrayList<>();

    public ClassificationPropertiesDialog(Classifier<?> classifier, IAvailableClassifierInfo availableClassifierInfo) {
        setTitle("Configure properties of classifier \"" + classifier.getName() + "\"");
        ClassificationPropertiesDialogPane dialogPane = new ClassificationPropertiesDialogPane();
        dialogPane.init();
        setDialogPane(dialogPane);


        /*
         * Return false if any other button than "apply" has been pressed.
         */
        setResultConverter(result -> {
            if (result == ButtonType.APPLY) {
                // Copy the current data from the UI into the underlying data.
                for (ClassifierPropertyInfos<?> property : properties.stream().filter(property -> property.fullyInitialized).collect(Collectors.toList())) {
                    property.uiToClassifier();
                }
                return true;
            } else {
                return false;
            }
        });

        if (classifier.configurableProperties().length == 0) {
            Label headerLabel = new Label("No configurable properties in classifier" + classifier.getName());
            dialogPane.getMainPane().getChildren().add(headerLabel);
        } else {
            for (int i = 0; i < classifier.configurableProperties().length; i++) {
                ClassifierProperty<?> property = classifier.configurableProperties()[i];
                ClassifierPropertyInfos<?> propertyInfos;

                AccessibleNode<?> node = NodeUtil.datatypeToNode(property.getField().getType(),
                                                                 this::checkAcceptButtonEnabling,
                                                                 availableClassifierInfo);
                propertyInfos = new ClassifierPropertyInfos<>(property, node.getter, node.setter);

                if (!propertyInfos.fullyInitialized) {
                    continue;
                }
                properties.add(propertyInfos);

                Pane fieldPane;
                if (node.node instanceof CheckBox) {
                    fieldPane = new HBox();
                    Label textLabel = new Label(propertyInfos.getPresentationName());
                    textLabel.setStyle("-fx-font-weight: bold");
                    fieldPane.getChildren().add(node.node);
                    fieldPane.getChildren().add(textLabel);
                } else {
                    fieldPane = new VBox();
                    ((VBox) fieldPane).setSpacing(5);
                    Label textLabel = new Label(propertyInfos.getPresentationName() + ":");
                    textLabel.setStyle("-fx-font-weight: bold");
                    fieldPane.getChildren().add(textLabel);
                    fieldPane.getChildren().add(node.node);
                }
                if (i > 0) {
                    Separator separator = new Separator();
                    separator.setPadding(new Insets(10.0, 0.0, 10.0, 0.0));
                    fieldPane.getChildren().add(separator);
                }
                dialogPane.getMainPane().getChildren().add(fieldPane);
            }
        }

        // position dialog to (always) appear on same screen as main window (and make it fit there)
        /*
        Bounds creatorScreenCoords = creator.localToScreen(creator.getBoundsInLocal());
        ObservableList<Screen> screenList = Screen.getScreensForRectangle(creatorScreenCoords.getMinX(), creatorScreenCoords.getMinY(), 1, 1);
        if (screenList.size() >= 1) {
            Rectangle2D currentScreen = screenList.get(0).getBounds();
            dialogPane.setMaxWidth(currentScreen.getWidth() * 0.80);
            dialogPane.setMaxHeight(currentScreen.getHeight() * 0.80);
        }
        */
    }

    private void checkAcceptButtonEnabling() {
        boolean everythingSet = properties.stream()
                                          .filter(property -> property.fullyInitialized)
                                          .allMatch(property -> property.uiGetter != null && property.uiGetter.get() != null);
        getApplyButton().setDisable(!everythingSet);
    }

    private Button getApplyButton() {
        Node node = getDialogPane().lookupButton(ButtonType.APPLY);
        if (node instanceof Button) {
            return (Button) node;
        } else {
            throw new IllegalStateException("Apply Button not found - Dialog may not be initialized correctly");
        }
    }

    public static boolean showDialogForClassifier(Classifier<?> classifier, IAvailableClassifierInfo availableClassifierInfo) {
        if (classifier == null) {
            return false;
        }
        if (classifier.configurableProperties().length == 0) {
            return false;
        }

        ClassificationPropertiesDialog dialog = new ClassificationPropertiesDialog(classifier, availableClassifierInfo);
        WindowUtil.INSTANCE.centerInMainFrame(dialog);
        Optional<Boolean> dialogResult = dialog.showAndWait();

        return dialogResult.isPresent() && dialog.getResult();
    }
}
