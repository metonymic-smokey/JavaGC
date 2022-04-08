package at.jku.anttracks.classification;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class ClassifierProperty<T> {
    protected final Classifier<?> classifier;
    protected final Field field;
    protected final String internalName;
    protected final String presentationName;

    protected Supplier<T> propertyGetter = null;
    protected Consumer<T> propertySetter = null;

    protected boolean fullyInitialized = true;

    protected Logger LOGGER;

    @SuppressWarnings("unchecked")
    public ClassifierProperty(Classifier<?> classifier, Field field) {
        LOGGER = Logger.getLogger(getClass().getSimpleName());

        this.classifier = classifier;
        this.field = field;
        internalName = field.getName();
        presentationName = toPresentationName(field.getName());

        final Method getter;
        try {
            getter = classifier.getClass().getMethod("get" + Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1));
            if (getter == null) {
                LOGGER.warning("Field " + internalName + " of classifier " + classifier.getName() + " does not provide a suitable public getter method!");
            } else if (!field.getType().isAssignableFrom(getter.getReturnType())) {
                LOGGER.warning("Getter of field " + internalName + " in classifier " + classifier.getName() + " returns wrong type! Expected: " + field.getType()
                                                                                                                                                       .toString() + "." +
                                       " Actual: "
                                       + getter
                        .getReturnType() + ".");
            } else {
                propertyGetter = () -> {
                    try {
                        return (T) getter.invoke(classifier, new Object[0]);
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                        LOGGER.warning("Executing getter of field " + internalName + " in classifier " + classifier.getName() + " threw an exception:\n" + e);
                    }
                    return null;
                };
            }
        } catch (NoSuchMethodException | SecurityException e1) {
            LOGGER.warning("Field " + internalName + " of classifier " + classifier.getName() + " does not provide a suitable public getter method!");
        } finally {
            if (propertyGetter == null) {
                propertyGetter = () -> null;
                fullyInitialized = false;
            }
        }

        final Method setter;
        try {
            setter = classifier.getClass().getMethod("set" + Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1), field.getType());
            if (setter == null) {
                LOGGER.warning("Field " + internalName + " of classifier " + classifier.getName() + " does not provide a suitable public setter method!");
                return;
            } else {
                propertySetter = value -> {
                    try {
                        setter.invoke(classifier, value);
                    } catch (IllegalArgumentException | IllegalAccessException | SecurityException | InvocationTargetException e) {
                        LOGGER.warning("Executing setter of field " + internalName + " in classifier " + classifier.getName() + " threw an exception:\n" + e);
                    }
                };
            }
        } catch (NoSuchMethodException | SecurityException e1) {
            LOGGER.warning("Field " + internalName + " of classifier " + classifier.getName() + " does not provide a suitable public setter method!");
        } finally {
            if (propertySetter == null) {
                propertySetter = value -> {};
                fullyInitialized = false;
            }
        }
    }

    private String toPresentationName(String name) {
        // ToUpper first character
        name = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
        for (int i = 1; i < name.length(); i++) {
            // Encounter uppercase letter which is not preceded by a
            // whitespace -> Insert whitespace
            if (Character.isUpperCase(name.charAt(i)) && !Character.isWhitespace(name.charAt(i - 1))) {
                name = name.substring(0, i) + " " + name.substring(i);
            }
        }
        return name;
    }

    public Classifier<?> getClassifier() {
        return classifier;
    }

    public Field getField() {
        return field;
    }

    public String getPresentationName() {
        return presentationName;
    }

    public T get() {
        return propertyGetter.get();
    }

    public void set(T data) {
        propertySetter.accept(data);
    }
}
