


package com.android.apksig.internal.util;

import java.lang.annotation.Annotation;
import java.util.Objects;

public class ClassCompat {
    public static <A extends Annotation> A getDeclaredAnnotation(Class<?> containerClass,
                                                                 Class<A> annotationClass) {
        Objects.requireNonNull(annotationClass);
        Objects.requireNonNull(containerClass);
        // Loop over all directly-present annotations looking for a matching one
        for (Annotation annotation : containerClass.getDeclaredAnnotations()) {
            if (annotationClass.equals(annotation.annotationType())) {
                // More robust to do a dynamic cast at runtime instead
                // of compile-time only.
                return annotationClass.cast(annotation);
            }
        }
        return null;
    }
}
