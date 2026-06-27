package org.kirisame.mc.core.plugin;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;

/**
 * A child-first classloader with dependency-scoped visibility.
 * A plugin can only see its own classes, the framework API (via parent),
 * and the classes of its declared dependencies.
 */
public class PluginClassLoader extends URLClassLoader {

    private final String pluginName;
    private final Map<String, PluginClassLoader> dependencyLoaders;

    public PluginClassLoader(String pluginName, URL[] urls, ClassLoader parent,
                             Map<String, PluginClassLoader> dependencyLoaders) {
        super(urls, parent);
        this.pluginName = pluginName;
        this.dependencyLoaders = dependencyLoaders;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // 1. Try to find in own JAR
        try {
            return super.findClass(name);
        } catch (ClassNotFoundException ignored) {
        }

        // 2. Try dependency classloaders (in dependency declaration order)
        for (Map.Entry<String, PluginClassLoader> entry : dependencyLoaders.entrySet()) {
            try {
                return entry.getValue().loadClass(name);
            } catch (ClassNotFoundException ignored) {
            }
        }

        throw new ClassNotFoundException(
                name + " (not in plugin '" + pluginName + "' or its dependencies: "
                        + dependencyLoaders.keySet() + ")");
    }

    /**
     * Returns the plugin name this classloader belongs to.
     */
    public String getPluginName() {
        return pluginName;
    }
}
