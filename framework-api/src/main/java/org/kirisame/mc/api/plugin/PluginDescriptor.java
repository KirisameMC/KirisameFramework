package org.kirisame.mc.api.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Plugin metadata annotation. Every plugin main class must be annotated with this.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PluginDescriptor {
    String name();
    String version();
    String author() default "";
    String description() default "";
    Dependency[] dependencies() default {};
}
