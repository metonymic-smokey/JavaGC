
package at.jku.anttracks.gui.utils;

import at.jku.anttracks.gui.model.IAvailableClassifierInfo;

import javax.swing.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class JComponentUtil {
    public static class AccessibleComponent<T> {
        public final JComponent component;
        public final Consumer<T> setter;
        public final Supplier<T> getter;

        public AccessibleComponent(JComponent component, Consumer<T> setter, Supplier<T> getter) {
            this.component = component;
            this.setter = setter;
            this.getter = getter;
        }
    }

    @SuppressWarnings({"unused"})
    public static <T, V> AccessibleComponent<V> datatypeToComponent(Class<T> clazz, Runnable onListener, IAvailableClassifierInfo availableClassifierInfo) {
        JComponent component;
        Consumer<V> setter;
        Supplier<V> getter;

        switch (clazz.getName()) {
            /*
             * case "at.jku.anttracks.classification.HeapObjectClassifierChain": ObjectClassifierSelectionPanel
             * classifierSelectionPanel = new ObjectClassifierSelectionPanel( availableClassifierInfo);
             * classifierSelectionPanel.addListener(new ClassifierSelectionListener() {
             *
             * @Override public void classifierSelected(ObjectClassifierSelectionPanel sender, HeapObjectClassifier<?> classifier) { if
             * (onListener != null) { onListener.run(); } }
             *
             * @Override public void classifierDeselected(ObjectClassifierSelectionPanel sender, HeapObjectClassifier<?> classifier) { if
             * (onListener != null) { onListener.run(); } } }); classifierSelectionPanel.expand(); component = classifierSelectionPanel;
             * getter = () -> (V) classifierSelectionPanel.getSelectedClassifiers(); setter = value ->
             * classifierSelectionPanel.reset((HeapObjectClassifierChain) value); break;
             */
            default:
                return datatypeToComponent(clazz, onListener);
        }

        // return new AccessibleComponent<>(component, setter, getter);
    }

    @SuppressWarnings("unchecked")
    public static <T, V> AccessibleComponent<V> datatypeToComponent(Class<T> clazz, Runnable onListener) {
        JComponent component;
        Consumer<V> setter;
        Supplier<V> getter;

        switch (clazz.getName()) {
            case "byte":
            case "java.lang.Byte":
            case "short":
            case "java.lang.Short":
            case "int":
            case "java.lang.Integer":
            case "long":
            case "java.lang.Long":
            case "float":
            case "java.lang.Float":
            case "double":
            case "java.lang.Double":
            case "char":
            case "java.lang.Character":
            case "java.lang.String":
                JFormattedTextField textField = new JFormattedTextField();
                textField.addActionListener(e -> {
                    if (onListener != null) {
                        onListener.run();
                    }
                });
                component = textField;

                getter = () -> (V) textField.getValue();
                setter = value -> textField.setValue(value);

                break;
            case "boolean":
            case "java.lang.Boolean":
                JCheckBox checkBox = new JCheckBox();
                checkBox.addActionListener(e -> {
                    if (onListener != null) {
                        onListener.run();
                    }
                });
                component = checkBox;
                getter = () -> (V) (Boolean) checkBox.isSelected();
                setter = value -> checkBox.setSelected((Boolean) value);
                break;
            default:
                if (clazz.isEnum()) {
                    JComboBox<T> enumBox = new JComboBox<>();
                    enumBox.addActionListener(e -> {
                        if (onListener != null) {
                            onListener.run();
                        }
                    });
                    for (T constant : clazz.getEnumConstants()) {
                        enumBox.addItem(constant);
                    }
                    component = enumBox;
                    getter = () -> (V) enumBox.getSelectedItem();
                    setter = value -> enumBox.setSelectedItem(value);
                    break;
                } else {
                    component = new JLabel("<html>Currently AntTracks does not support to set properties of type <tt>" + clazz.getName() + "</tt></html>");
                    getter = () -> null;
                    setter = value -> {};
                }
                break;
        }

        return new AccessibleComponent<>(component, setter, getter);
    }
}
