package org.kirisame.mc.core.plugin;

import org.kirisame.mc.api.event.EventBus;
import org.kirisame.mc.api.exception.DependencyException;
import org.kirisame.mc.api.exception.PluginLoadException;
import org.kirisame.mc.api.plugin.KirisamePlugin;
import org.kirisame.mc.api.plugin.PluginContext;
import org.kirisame.mc.api.plugin.PluginDescriptor;
import org.kirisame.mc.api.plugin.PluginState;
import org.kirisame.mc.api.service.ServiceRegistry;
import org.kirisame.mc.api.transform.ClassTransform;
import org.kirisame.mc.agent.AgentBridge;
import org.tinylog.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Manages plugin discovery, dependency resolution, loading, and lifecycle.
 */
public class PluginManager {

    private final Map<String, PluginInfo> plugins = new LinkedHashMap<>();
    private final DependencyResolver resolver = new DependencyResolver();
    private List<PluginInfo> loadOrder = Collections.emptyList();
    private final EventBus eventBus;
    private final ServiceRegistry serviceRegistry;
    private final Path pluginsDir;

    public PluginManager(Path pluginsDir, EventBus eventBus, ServiceRegistry serviceRegistry) {
        this.pluginsDir = pluginsDir;
        this.eventBus = eventBus;
        this.serviceRegistry = serviceRegistry;
    }

    // --- Discovery ---

    /**
     * Scans the plugins directory for .jar files and reads their PluginDescriptor.
     */
    public void discoverPlugins() {
        if (!Files.isDirectory(pluginsDir)) {
            Logger.warn("Plugins directory does not exist: {}", pluginsDir);
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path jar : stream) {
                try {
                    PluginInfo info = readPluginInfo(jar);
                    plugins.put(info.getName(), info);
                    Logger.info("Discovered plugin: {} v{}", info.getName(), info.getDescriptor().version());
                } catch (Exception e) {
                    Logger.error(e, "Failed to read plugin descriptor from: {}", jar.getFileName());
                }
            }
        } catch (IOException e) {
            Logger.error(e, "Failed to scan plugins directory: {}", pluginsDir);
        }

        Logger.info("Discovered {} plugin(s)", plugins.size());
    }

    private PluginInfo readPluginInfo(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Find the plugin descriptor from the main class
            // Strategy: scan all entries for a class with @PluginDescriptor
            String mainClassName = null;
            PluginDescriptor descriptor = null;
            String transformClassName = null;

            // First pass: find the main class with @PluginDescriptor
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class") && !entry.getName().contains("$")) {
                    String className = entry.getName()
                            .replace("/", ".")
                            .replace(".class", "");
                    try {
                        URLClassLoader tempLoader = new URLClassLoader(
                                new URL[]{jarPath.toUri().toURL()}, getClass().getClassLoader());
                        Class<?> clazz = Class.forName(className, false, tempLoader);
                        PluginDescriptor desc = clazz.getAnnotation(PluginDescriptor.class);
                        if (desc != null) {
                            mainClassName = className;
                            descriptor = desc;
                            // Check for transform class field
                            try {
                                var field = clazz.getDeclaredField("TRANSFORM_CLASS");
                                field.setAccessible(true);
                                transformClassName = (String) field.get(null);
                            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                                // No transform class declared
                            }
                            tempLoader.close();
                            break;
                        }
                        tempLoader.close();
                    } catch (ClassNotFoundException ignored) {
                    } catch (NoClassDefFoundError ignored) {
                    }
                }
            }

            if (mainClassName == null || descriptor == null) {
                throw new PluginLoadException("No class with @PluginDescriptor found in " + jarPath.getFileName());
            }

            return new PluginInfo(jarPath, descriptor, mainClassName, transformClassName);
        }
    }

    // --- Resolution ---

    /**
     * Validates dependencies and determines load order.
     */
    public void resolveDependencies() {
        loadOrder = resolver.resolve(new ArrayList<>(plugins.values()));
    }

    // --- Loading ---

    /**
     * Creates classloaders and instantiates plugins in dependency order.
     *
     * @param parent the parent classloader (typically the framework classloader)
     */
    public void loadPlugins(ClassLoader parent) {
        for (PluginInfo info : loadOrder) {
            try {
                loadSinglePlugin(info, parent);
            } catch (Exception e) {
                Logger.error(e, "Failed to load plugin: {}", info.getName());
                info.setState(PluginState.FAILED);
            }
        }
    }

    private void loadSinglePlugin(PluginInfo info, ClassLoader parent) throws Exception {
        // 1. Collect dependency classloaders
        Map<String, PluginClassLoader> depLoaders = new LinkedHashMap<>();
        for (String depName : info.getResolvedDeps()) {
            PluginInfo depInfo = plugins.get(depName);
            if (depInfo != null && depInfo.getClassLoader() != null) {
                depLoaders.put(depName, depInfo.getClassLoader());
            }
        }

        // 2. Create plugin classloader
        URL jarUrl = info.getJarPath().toUri().toURL();
        PluginClassLoader classLoader = new PluginClassLoader(
                info.getName(), new URL[]{jarUrl}, parent, depLoaders);
        info.setClassLoader(classLoader);

        // 3. Instantiate main class
        Class<?> mainClass = Class.forName(info.getMainClassName(), true, classLoader);
        KirisamePlugin instance = (KirisamePlugin) mainClass.getDeclaredConstructor().newInstance();

        // 4. Inject context
        PluginContext context = new PluginContextImpl(info, eventBus, serviceRegistry, this);
        Method setContext = KirisamePlugin.class.getDeclaredMethod("_setContext", PluginContext.class);
        setContext.setAccessible(true);
        setContext.invoke(instance, context);

        info.setInstance(instance);

        // 5. Load transform class if declared
        if (info.getTransformClassName() != null && !info.getTransformClassName().isEmpty()) {
            try {
                Class<?> transformClass = Class.forName(info.getTransformClassName(), true, classLoader);
                ClassTransform transform = (ClassTransform) transformClass.getDeclaredConstructor().newInstance();
                info.setTransformInstance(transform);
                Logger.debug("Loaded transform class for plugin: {}", info.getName());
            } catch (Exception e) {
                Logger.warn(e, "Failed to load transform class for plugin: {}", info.getName());
            }
        }

        info.setState(PluginState.LOADED);
        Logger.info("Loaded plugin: {} v{}", info.getName(), info.getDescriptor().version());
    }

    // --- Enable ---

    /**
     * Calls onLoad() on all plugins in dependency order.
     */
    public void enablePlugins() {
        // Apply transforms first
        applyTransforms();

        for (PluginInfo info : loadOrder) {
            if (info.getState() != PluginState.LOADED) continue;
            try {
                info.getInstance()._onLoad();
                info.setState(PluginState.ENABLED);
                Logger.info("Enabled plugin: {}", info.getName());
            } catch (Exception e) {
                Logger.error(e, "Failed to enable plugin: {}", info.getName());
                info.setState(PluginState.FAILED);
            }
        }
    }

    private void applyTransforms() {
        if (!AgentBridge.isAvailable()) {
            Logger.warn("Agent bridge not available, skipping plugin transforms");
            return;
        }

        var builder = AgentBridge.getAgentBuilder();
        var inst = AgentBridge.getInstrumentation();

        for (PluginInfo info : loadOrder) {
            ClassTransform transform = info.getTransformInstance();
            if (transform != null) {
                try {
                    builder = transform.apply(builder);
                    Logger.debug("Applied transform from plugin: {}", info.getName());
                } catch (Exception e) {
                    Logger.error(e, "Failed to apply transform from plugin: {}", info.getName());
                }
            }
        }

        builder.installOn(inst);
        Logger.info("All plugin transforms applied");
    }

    // --- Disable ---

    /**
     * Calls onUnload() on all plugins in reverse dependency order, then closes classloaders.
     */
    public void disablePlugins() {
        List<PluginInfo> reversed = new ArrayList<>(loadOrder);
        Collections.reverse(reversed);

        for (PluginInfo info : reversed) {
            if (info.getState() != PluginState.ENABLED) continue;
            try {
                info.getInstance()._onUnload();
                info.setState(PluginState.DISABLED);
                Logger.info("Disabled plugin: {}", info.getName());
            } catch (Exception e) {
                Logger.error(e, "Error disabling plugin: {}", info.getName());
            }
        }

        // Close classloaders in reverse order
        for (PluginInfo info : reversed) {
            if (info.getClassLoader() != null) {
                try {
                    info.getClassLoader().close();
                } catch (IOException e) {
                    Logger.warn("Error closing classloader for plugin {}: {}", info.getName(), e.getMessage());
                }
            }
        }
    }

    // --- Dependency access ---

    /**
     * Gets a loaded plugin by name.
     */
    public KirisamePlugin getPluginByName(String name) {
        PluginInfo info = plugins.get(name);
        return (info != null && info.getInstance() != null) ? info.getInstance() : null;
    }

    /**
     * Gets a loaded plugin by class.
     */
    @SuppressWarnings("unchecked")
    public <T extends KirisamePlugin> T getPlugin(Class<T> pluginClass) {
        for (PluginInfo info : plugins.values()) {
            if (info.getInstance() != null && pluginClass.isInstance(info.getInstance())) {
                return (T) info.getInstance();
            }
        }
        return null;
    }

    /**
     * Returns all plugin names.
     */
    public Set<String> getPluginNames() {
        return Collections.unmodifiableSet(plugins.keySet());
    }

    public Map<String, PluginInfo> getPlugins() {
        return Collections.unmodifiableMap(plugins);
    }
}
