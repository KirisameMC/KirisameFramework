package org.kirisame.mc.core.plugin;

import net.bytebuddy.agent.builder.AgentBuilder;
import org.kirisame.mc.api.event.EventBus;
import org.kirisame.mc.api.exception.DependencyException;
import org.kirisame.mc.api.exception.PluginLoadException;
import org.kirisame.mc.api.plugin.Dependency;
import org.kirisame.mc.api.plugin.KirisamePlugin;
import org.kirisame.mc.api.plugin.PluginContext;
import org.kirisame.mc.api.plugin.PluginDescriptor;
import org.kirisame.mc.api.plugin.PluginState;
import org.kirisame.mc.api.service.ServiceRegistry;
import org.kirisame.mc.api.transform.ClassTransform;
import org.kirisame.mc.agent.AgentBridge;
import org.kirisame.mc.core.transform.TransformBridgeImpl;
import org.objectweb.asm.*;
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
 *
 * <p>Discovery and transforms are bytecode-level (no class loading, safe before server start).
 * Plugin instantiation happens after server start, so PluginClassLoader sees NMS classes.</p>
 */
public class PluginManager {

    private final Map<String, PluginInfo> plugins = new LinkedHashMap<>();
    private final DependencyResolver resolver = new DependencyResolver();
    private List<PluginInfo> loadOrder = Collections.emptyList();
    private final EventBus eventBus;
    private final ServiceRegistry serviceRegistry;
    private final Path pluginsDir;
    private final List<URLClassLoader> transformClassLoaders = new ArrayList<>();

    public PluginManager(Path pluginsDir, EventBus eventBus, ServiceRegistry serviceRegistry) {
        this.pluginsDir = pluginsDir;
        this.eventBus = eventBus;
        this.serviceRegistry = serviceRegistry;
    }

    // --- Discovery (ASM bytecode scan, no class loading) ---

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

    /**
     * Reads plugin metadata from JAR bytecode using ASM.
     * No classes are loaded — safe even if the plugin references NMS classes.
     */
    private PluginInfo readPluginInfo(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            String mainClassName = null;
            PluginDescriptorData descriptor = null;
            String transformClassName = null;

            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class") || entry.getName().contains("$")) {
                    continue;
                }

                String className = entry.getName()
                        .replace("/", ".")
                        .replace(".class", "");

                try (InputStream is = jar.getInputStream(entry)) {
                    ClassReader reader = new ClassReader(is);
                    PluginDescriptorScanner scanner = new PluginDescriptorScanner();
                    reader.accept(scanner, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);

                    if (scanner.descriptor != null) {
                        mainClassName = className;
                        descriptor = scanner.descriptor;
                        transformClassName = scanner.transformClassName;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }

            if (mainClassName == null || descriptor == null) {
                throw new PluginLoadException("No class with @PluginDescriptor found in " + jarPath.getFileName());
            }

            return new PluginInfo(jarPath, descriptor.toPluginDescriptor(), mainClassName, transformClassName);
        }
    }

    // --- ASM scanner for @PluginDescriptor + TRANSFORM_CLASS ---

    /**
     * Holds raw annotation values read from bytecode.
     */
    private static class PluginDescriptorData {
        String name = "";
        String version = "1.0";
        String author = "";
        String description = "";
        List<DependencyData> dependencies = new ArrayList<>();

        PluginDescriptor toPluginDescriptor() {
            final String n = name, v = version, a = author, d = description;
            final Dependency[] deps = dependencies.stream()
                    .map(DependencyData::toDependency).toArray(Dependency[]::new);
            return new PluginDescriptor() {
                @Override public String name() { return n; }
                @Override public String version() { return v; }
                @Override public String author() { return a; }
                @Override public String description() { return d; }
                @Override public Dependency[] dependencies() { return deps; }
                @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return PluginDescriptor.class; }
            };
        }
    }

    private static class DependencyData {
        String plugin = "";
        boolean required = true;

        Dependency toDependency() {
            final String p = plugin;
            final boolean r = required;
            return new Dependency() {
                @Override public String plugin() { return p; }
                @Override public boolean required() { return r; }
                @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return Dependency.class; }
            };
        }
    }

    /**
     * ASM ClassVisitor that scans for @PluginDescriptor and reads its values + TRANSFORM_CLASS field.
     */
    private static class PluginDescriptorScanner extends ClassVisitor {
        PluginDescriptorData descriptor;
        String transformClassName;

        PluginDescriptorScanner() {
            super(Opcodes.ASM9);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if ("Lorg/kirisame/mc/api/plugin/PluginDescriptor;".equals(descriptor)) {
                this.descriptor = new PluginDescriptorData();
                return new PluginDescriptorAnnotationVisitor(this.descriptor);
            }
            return null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if ("TRANSFORM_CLASS".equals(name) && value instanceof String) {
                transformClassName = (String) value;
            }
            return null;
        }
    }

    private static class PluginDescriptorAnnotationVisitor extends AnnotationVisitor {
        private final PluginDescriptorData data;

        PluginDescriptorAnnotationVisitor(PluginDescriptorData data) {
            super(Opcodes.ASM9);
            this.data = data;
        }

        @Override
        public void visit(String name, Object value) {
            switch (name) {
                case "name": data.name = (String) value; break;
                case "version": data.version = (String) value; break;
                case "author": data.author = (String) value; break;
                case "description": data.description = (String) value; break;
            }
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            if ("dependencies".equals(name)) {
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                        DependencyData dep = new DependencyData();
                        data.dependencies.add(dep);
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String name, Object value) {
                                if ("plugin".equals(name)) dep.plugin = (String) value;
                                else if ("required".equals(name)) dep.required = (Boolean) value;
                            }
                        };
                    }
                };
            }
            return null;
        }
    }

    // --- Resolution ---

    public void resolveDependencies() {
        loadOrder = resolver.resolve(new ArrayList<>(plugins.values()));
    }

    // --- Transform application (before server start) ---

    /**
     * Applies plugin transforms by loading transform classes from plugin JARs.
     * Classloaders are kept alive so the server can resolve Advice classes.
     */
    public void applyTransforms() {
        if (!AgentBridge.isAvailable()) {
            Logger.warn("Agent bridge not available, skipping plugin transforms");
            return;
        }

        AgentBuilder builder = AgentBridge.getAgentBuilder();
        var inst = AgentBridge.getInstrumentation();

        for (PluginInfo info : loadOrder) {
            String transformClassName = info.getTransformClassName();
            if (transformClassName == null || transformClassName.isEmpty()) continue;

            try {
                URLClassLoader transformLoader = new URLClassLoader(
                        new URL[]{info.getJarPath().toUri().toURL()},
                        getClass().getClassLoader());
                transformClassLoaders.add(transformLoader);

                Class<?> transformClass = Class.forName(transformClassName, true, transformLoader);
                ClassTransform transform = (ClassTransform) transformClass.getDeclaredConstructor().newInstance();

                info.setTransformInstance(transform);
                builder = transform.apply(builder);
                Logger.debug("Applied transform from plugin: {}", info.getName());
            } catch (Exception e) {
                Logger.error(e, "Failed to apply transform from plugin: {}", info.getName());
            }
        }

        builder.installOn(inst);
        Logger.info("All plugin transforms applied");
    }

    // --- Loading (AFTER server start — PluginClassLoader sees NMS) ---

    public void loadPlugins(ClassLoader frameworkCL, ClassLoader serverCL) {
        for (PluginInfo info : loadOrder) {
            try {
                loadSinglePlugin(info, frameworkCL, serverCL);
            } catch (Exception e) {
                Logger.error(e, "Failed to load plugin: {}", info.getName());
                info.setState(PluginState.FAILED);
            }
        }
    }

    private void loadSinglePlugin(PluginInfo info, ClassLoader frameworkCL, ClassLoader serverCL) throws Exception {
        Map<String, PluginClassLoader> depLoaders = new LinkedHashMap<>();
        for (String depName : info.getResolvedDeps()) {
            PluginInfo depInfo = plugins.get(depName);
            if (depInfo != null && depInfo.getClassLoader() != null) {
                depLoaders.put(depName, depInfo.getClassLoader());
            }
        }

        URL jarUrl = info.getJarPath().toUri().toURL();
        ClassLoader parentCL = serverCL != null
                ? new CompositeParentClassLoader(frameworkCL, serverCL)
                : frameworkCL;

        PluginClassLoader classLoader = new PluginClassLoader(
                info.getName(), new URL[]{jarUrl}, parentCL, depLoaders);
        info.setClassLoader(classLoader);

        TransformBridgeImpl bridge = new TransformBridgeImpl(eventBus, info.getName());
        info.setTransformBridge(bridge);

        // Inject bridge into transform instance
        if (info.getTransformInstance() != null) {
            injectBridge(info.getTransformInstance(), bridge);
        }

        Class<?> mainClass = Class.forName(info.getMainClassName(), true, classLoader);
        KirisamePlugin instance = (KirisamePlugin) mainClass.getDeclaredConstructor().newInstance();

        PluginContext context = new PluginContextImpl(info, eventBus, serviceRegistry, this, bridge);
        Method setContext = KirisamePlugin.class.getDeclaredMethod("_setContext", PluginContext.class);
        setContext.setAccessible(true);
        setContext.invoke(instance, context);

        info.setInstance(instance);
        info.setState(PluginState.LOADED);
        Logger.info("Loaded plugin: {} v{}", info.getName(), info.getDescriptor().version());
    }

    private void injectBridge(ClassTransform transform, TransformBridgeImpl bridge) {
        try {
            var bridgeField = transform.getClass().getDeclaredField("TRANSFORM_BRIDGE");
            bridgeField.setAccessible(true);
            bridgeField.set(null, bridge);
        } catch (NoSuchFieldException ignored) {
        } catch (IllegalAccessException e) {
            Logger.warn("TRANSFORM_BRIDGE field must be public static in: {}", transform.getClass().getName());
        }
    }

    // --- Enable / Disable ---

    public void enablePlugins() {
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

    public void disablePlugins() {
        List<PluginInfo> reversed = new ArrayList<>(loadOrder);
        Collections.reverse(reversed);
        for (PluginInfo info : reversed) {
            if (info.getState() != PluginState.ENABLED) continue;
            try {
                info.getInstance()._onUnload();
                info.setState(PluginState.DISABLED);
            } catch (Exception e) {
                Logger.error(e, "Error disabling plugin: {}", info.getName());
            }
        }
        for (PluginInfo info : reversed) {
            if (info.getClassLoader() != null) {
                try { info.getClassLoader().close(); } catch (IOException ignored) {}
            }
        }
        // Close transform classloaders
        for (ClassLoader cl : transformClassLoaders) {
            try { ((AutoCloseable) cl).close(); } catch (Exception ignored) {}
        }
    }

    // --- Dependency access ---

    public KirisamePlugin getPluginByName(String name) {
        PluginInfo info = plugins.get(name);
        return (info != null && info.getInstance() != null) ? info.getInstance() : null;
    }

    @SuppressWarnings("unchecked")
    public <T extends KirisamePlugin> T getPlugin(Class<T> pluginClass) {
        for (PluginInfo info : plugins.values()) {
            if (info.getInstance() != null && pluginClass.isInstance(info.getInstance())) {
                return (T) info.getInstance();
            }
        }
        return null;
    }

    public Set<String> getPluginNames() { return Collections.unmodifiableSet(plugins.keySet()); }
    public Map<String, PluginInfo> getPlugins() { return Collections.unmodifiableMap(plugins); }

    public List<ClassLoader> getPluginClassLoaders() {
        List<ClassLoader> loaders = new ArrayList<>();
        for (PluginInfo info : loadOrder) {
            if (info.getClassLoader() != null) loaders.add(info.getClassLoader());
        }
        return loaders;
    }

    /**
     * Returns a composite parent classloader for the server, containing the framework CL
     * and all transform classloaders. This ensures Advice classes from plugin transforms
     * are resolvable when the server executes transformed methods.
     */
    public ClassLoader getServerParentClassLoader(ClassLoader frameworkCL) {
        if (transformClassLoaders.isEmpty()) {
            return frameworkCL;
        }
        List<ClassLoader> all = new ArrayList<>();
        all.add(frameworkCL);
        all.addAll(transformClassLoaders);
        return new CompositeParentClassLoader(all.toArray(new ClassLoader[0]));
    }

    /**
     * Composite parent that delegates to both framework and server classloaders.
     * Gives plugin classes access to framework API AND NMS classes.
     */
    static class CompositeParentClassLoader extends ClassLoader {
        private final ClassLoader[] parents;

        CompositeParentClassLoader(ClassLoader... parents) {
            super(parents[0]);
            this.parents = parents;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            for (ClassLoader parent : parents) {
                try { return parent.loadClass(name); }
                catch (ClassNotFoundException ignored) {}
            }
            throw new ClassNotFoundException(name);
        }
    }
}
