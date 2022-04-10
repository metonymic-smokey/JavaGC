
package at.jku.anttracks.gui.utils;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.ClassifierFactory;
import at.jku.anttracks.gui.classification.classifier.component.selectionpane.ObjectClassifierSelectionPane;
import at.jku.anttracks.gui.classification.component.selectionpane.ClassificationSelectionPane;
import at.jku.anttracks.gui.classification.component.selectionpane.ClassificationSelectionPane.ClassificationSelectionListener;
import at.jku.anttracks.gui.model.IAvailableClassifierInfo;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.util.StringConverter;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class NodeUtil {
    public static class AccessibleNode<T> {
        public final Node node;
        public final Consumer<T> setter;
        public final Supplier<T> getter;

        public AccessibleNode(Node node, Consumer<T> setter, Supplier<T> getter) {
            this.node = node;
            this.setter = setter;
            this.getter = getter;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T, V> AccessibleNode<V> datatypeToNode(Class<T> clazz, Runnable onListener, IAvailableClassifierInfo availableClassifierInfo) {
        class ForwardingClassificationSelectionListener implements ClassificationSelectionListener<Classifier<?>, ClassifierFactory> {
            @Override
            public void selected(ClassificationSelectionPane<Classifier<?>, ClassifierFactory> sender, Classifier<?> x) {
                if (onListener != null) {
                    onListener.run();
                }
            }

            @Override
            public void deselected(ClassificationSelectionPane<Classifier<?>, ClassifierFactory> sender, Classifier<?> x) {
                if (onListener != null) {
                    onListener.run();
                }
            }

            @Override
            public void propertiesChanged(ClassificationSelectionPane<Classifier<?>, ClassifierFactory> sender, Classifier<?> x) {
                // Nothing to do if property changed
            }
        }

        Node node;
        Consumer<V> setter;
        Supplier<V> getter;

        switch (clazz.getName()) {
            case "at.jku.anttracks.classification.Classifier": {
                ObjectClassifierSelectionPane classifierSelectionPane = new ObjectClassifierSelectionPane();
                classifierSelectionPane.init(availableClassifierInfo,
                                             1,
                                             false);
                classifierSelectionPane.addListener(new ForwardingClassificationSelectionListener());
                classifierSelectionPane.switchToConfigurationPerspective();
                node = classifierSelectionPane;
                getter = () -> (V) (classifierSelectionPane.getSelected().isEmpty() ? null : classifierSelectionPane.getSelected().get(0));
                setter = value -> classifierSelectionPane.resetSelected(new ClassifierChain((Classifier<?>) value).getList());
                break;
            }
            case "at.jku.anttracks.classification.ClassifierChain": {
                ObjectClassifierSelectionPane classifierSelectionPane = new ObjectClassifierSelectionPane();
                classifierSelectionPane.init(availableClassifierInfo,
                                             0,
                                             false);
                classifierSelectionPane.addListener(new ForwardingClassificationSelectionListener());
                classifierSelectionPane.switchToConfigurationPerspective();
                node = classifierSelectionPane;
                getter = () -> (V) new ClassifierChain(classifierSelectionPane.getSelected());
                setter = value -> classifierSelectionPane.resetSelected(((ClassifierChain) value).getList());
                break;
            }
            default:
                return datatypeToNode(clazz, onListener);
        }

        return new AccessibleNode<>(node, setter, getter);
    }

    @SuppressWarnings("unchecked")
    public static <T, V> AccessibleNode<V> datatypeToNode(Class<T> clazz, Runnable onListener) {
        Node node;
        Consumer<V> setter;
        Supplier<V> getter;

        TextField textField = new TextField();
        textField.setOnAction(ae -> {
            if (onListener != null) {
                onListener.run();
            }
        });

        switch (clazz.getName()) {
            case "byte":
            case "java.lang.Byte":
                textField.setTextFormatter(new TextFormatter<Byte>(new StringConverter<Byte>() {

                    @Override
                    public String toString(Byte object) {
                        if (object != null) {
                            return object.toString();
                        } else {
                            return "";
                        }
                    }

                    @Override
                    public Byte fromString(String string) {
                        return Byte.parseByte(string);
                    }

                }));

                node = textField;

                getter = () -> (V) textField.getTextFormatter().getValue();
                setter = value -> textField.setText(value.toString());

                break;
            case "short":
            case "java.lang.Short":
                textField.setTextFormatter(new TextFormatter<Short>(new StringConverter<Short>() {

                    @Override
                    public String toString(Short object) {
                        if (object != null) {
                            return object.toString();
                        } else {
                            return "";
                        }
                    }

                    @Override
                    public Short fromString(String string) {
                        return Short.parseShort(string);
                    }

                }));

                node = textField;

                getter = () -> (V) textField.getTextFormatter().getValue();
                setter = value -> textField.setText(value.toString());

                break;
            case "int":
            case "java.lang.Integer":
                textField.setTextFormatter(new TextFormatter<Integer>(new StringConverter<Integer>() {

                    @Override
                    public String toString(Integer object) {
                        if (object != null) {
                            return object.toString();
                        } else {
                            return "";
                        }
                    }

                    @Override
                    public Integer fromString(String string) {
                        return Integer.parseInt(string);
                    }

                }));

                node = textField;

                getter = () -> (V) textField.getTextFormatter().getValue();
                setter = value -> textField.setText(value.toString());

                break;
            case "long":
            case "java.lang.Long":
                textField.setTextFormatter(new TextFormatter<Long>(new StringConverter<Long>() {

                    @Override
                    public String toString(Long object) {
                        if (object != null) {
                            return object.toString();
                        } else {
                            return "";
                        }
                    }

                    @Override
                    public Long fromString(String string) {
                        return Long.parseLong(string);
                    }

                }));

                node = textField;

                getter = () -> (V) textField.getTextFormatter().getValue();
                setter = value -> textField.setText(value.toString());

                break;
            case "float":
            case "java.lang.Float":
                textField.setTextFormatter(new TextFormatter<Float>(new StringConverter<Float>() {

                    @Override
                    public String toString(Float object) {
                        if (object != null) {
                            return object.toString();
                        } else {
                            return "";
                        }
                    }

                    @Override
                    public Float fromString(String string) {
                        return Float.parseFloat(string);
                    }

                }));

                node = textField;

                getter = () -> (V) textField.getTextFormatter().getValue();
                setter = value -> textField.setText(value.toString());

                break;
            case "double":
            case "java.lang.Double":
                textField.setTextFormatter(new TextFormatter<Double>(new StringConverter<Double>() {

                    @Override
                    public String toString(Double object) {
                        if (object != null) {
                            return object.toString();
                        } else {
                            return "";
                        }
                    }

                    @Override
                    public Double fromString(String string) {
                        return Double.parseDouble(string);
                    }

                }));

                node = textField;

                getter = () -> (V) textField.getTextFormatter().getValue();
                setter = value -> textField.setText(value.toString());

                break;
            case "char":
            case "java.lang.Character":
                textField.setTextFormatter(new TextFormatter<Character>(new StringConverter<Character>() {

                    @Override
                    public String toString(Character object) {
                        if (object != null) {
                            return object.toString();
                        } else {
                            return "";
                        }
                    }

                    @Override
                    public Character fromString(String string) {
                        return string.toCharArray()[0];
                    }

                }));

                node = textField;

                getter = () -> (V) textField.getTextFormatter().getValue();
                setter = value -> textField.setText(value.toString());

                break;
            case "java.lang.String":
                node = textField;

                getter = () -> (V) textField.getText();
                setter = value -> textField.setText(value.toString());

                break;
            case "boolean":
            case "java.lang.Boolean":
                CheckBox checkBox = new CheckBox();
                checkBox.setOnAction(ae -> {
                    if (onListener != null) {
                        onListener.run();
                    }
                });
                node = checkBox;
                getter = () -> (V) (Boolean) checkBox.isSelected();
                setter = value -> checkBox.setSelected((Boolean) value);
                break;
            default:
                if (clazz.isEnum()) {
                    ComboBox<T> enumBox = new ComboBox<>();
                    enumBox.setOnAction(ae -> {
                        if (onListener != null) {
                            onListener.run();
                        }
                    });
                    for (T constant : clazz.getEnumConstants()) {
                        enumBox.getItems().add(constant);
                    }
                    node = enumBox;
                    getter = () -> (V) enumBox.getSelectionModel().getSelectedItem();
                    setter = value -> enumBox.getSelectionModel().select((T) value);
                    break;
                } else {
                    // TODO set clazzname bold
                    node = new Label("Currently AntTracks does not support to set properties of type " + clazz.getName());
                    getter = () -> null;
                    setter = value -> {};
                }
                break;
        }

        return new AccessibleNode<>(node, setter, getter);
    }

    /**
     * When scrollable nodes are placed in a ScrollPane, we often want to scroll the containing pane even when the cursor points the scrollable node (e.g. multiple
     * tables inside a
     * ScrollPane).
     * This method installs a scroll event filter on the scrollable node such that scrolling is only performed on this node when it is focused (clicked/selected).
     *
     * @param scrollableNode       the node that has some scroll behaviour that should be suppressed unless it is focused
     * @param containingScrollPane the scroll pane that should be scrolled instead when scroll events are filtered at the node
     */
    public static void ignoreScrollingUnlessFocused(Node scrollableNode, ScrollPane containingScrollPane) {
        Function<Node, EventHandler<ScrollEvent>> getScrollOnlyWhenFocusedEventFilter = node -> evt -> {
            if (!node.isFocused()) {
                evt.consume();
                node.getParent().fireEvent(evt.copyFor(containingScrollPane, node));
            }
        };
        scrollableNode.addEventFilter(ScrollEvent.SCROLL, getScrollOnlyWhenFocusedEventFilter.apply(scrollableNode));
    }

    public static void fireClickEvent(Node node) {
        Event.fireEvent(node, new MouseEvent(MouseEvent.MOUSE_CLICKED,
                                             0,
                                             0,
                                             0,
                                             0,
                                             MouseButton.PRIMARY,
                                             1,
                                             false,
                                             false,
                                             false,
                                             false,
                                             true,
                                             false,
                                             false,
                                             true,
                                             true,
                                             true,
                                             null));
    }

    public static void ignoreScrollingUnlessFocused(Node scrollableNode) {
        Node parent = scrollableNode.getParent();
        while (parent != null) {
            if (parent instanceof ScrollPane) {
                ignoreScrollingUnlessFocused(scrollableNode, (ScrollPane) parent);
                return;
            }
            parent = parent.getParent();
        }
    }
}
