package org.kirisame.mc.api.plugin;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a dependency on another plugin.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface Dependency {
    /** The name of the required plugin (matches {@link PluginDescriptor#name()}). */
    String plugin();

    /**
     * If true (default), the framework will refuse to load this plugin when the dependency is missing.
     * If false, the dependency is optional and the plugin can function without it.
     */
    boolean required() default true;
}
