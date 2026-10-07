package dev.openallay.value;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** An explicit immutable value owner, never an arbitrary reflective bean. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ValueType {
    Class<? extends ValueSchema.Provider> value();
}
