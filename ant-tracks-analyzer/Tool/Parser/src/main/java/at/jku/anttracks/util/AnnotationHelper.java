package at.jku.anttracks.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;

public class AnnotationHelper {
    public static <T extends Annotation> T getAnnotation(Object object, Class<T> annotationClass) {
        T annotation = object.getClass().getAnnotation(annotationClass);
        if (annotation != null) {
            return annotation;
        }
        for (AnnotatedType t : object.getClass().getAnnotatedInterfaces()) {
            annotation = t.getAnnotation(annotationClass);
            if (annotation != null) {
                return annotation;
            }
        }
        AnnotatedType superclass = object.getClass().getAnnotatedSuperclass();
        assert superclass != null : "Every class must have a superclass";
        if (superclass != null) {
            annotation = superclass.getAnnotation(annotationClass);
        }
        if (annotation != null) {
            return annotation;
        }
        return null;
    }
}
