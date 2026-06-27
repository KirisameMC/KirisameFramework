package org.kirisame.mc.core.plugin;

import org.kirisame.mc.api.plugin.Dependency;
import org.kirisame.mc.api.plugin.PluginDescriptor;
import org.kirisame.mc.api.plugin.PluginState;
import org.kirisame.mc.api.plugin.KirisamePlugin;
import org.kirisame.mc.api.transform.ClassTransform;

import java.nio.file.Path;
import java.util.*;

/**
 * Holds all metadata and runtime state for a single loaded plugin.
 */
public class PluginInfo {

    private final Path jarPath;
    private final PluginDescriptor descriptor;
    private final String mainClassName;
    private final String transformClassName;

    private PluginState state = PluginState.DISCOVERED;
    private PluginClassLoader classLoader;
    private KirisamePlugin instance;
    private ClassTransform transformInstance;

    // Dependency info (populated during resolution)
    private final List<String> requiredDeps = new ArrayList<>();
    private final List<String> optionalDeps = new ArrayList<>();
    private final Set<String> resolvedDeps = new LinkedHashSet<>();

    public PluginInfo(Path jarPath, PluginDescriptor descriptor, String mainClassName, String transformClassName) {
        this.jarPath = jarPath;
        this.descriptor = descriptor;
        this.mainClassName = mainClassName;
        this.transformClassName = transformClassName;

        // Parse dependencies from the annotation
        for (Dependency dep : descriptor.dependencies()) {
            if (dep.required()) {
                requiredDeps.add(dep.plugin());
            } else {
                optionalDeps.add(dep.plugin());
            }
        }
    }

    // --- Getters ---

    public Path getJarPath() { return jarPath; }
    public PluginDescriptor getDescriptor() { return descriptor; }
    public String getName() { return descriptor.name(); }
    public String getMainClassName() { return mainClassName; }
    public String getTransformClassName() { return transformClassName; }
    public PluginState getState() { return state; }
    public PluginClassLoader getClassLoader() { return classLoader; }
    public KirisamePlugin getInstance() { return instance; }
    public ClassTransform getTransformInstance() { return transformInstance; }
    public List<String> getRequiredDeps() { return Collections.unmodifiableList(requiredDeps); }
    public List<String> getOptionalDeps() { return Collections.unmodifiableList(optionalDeps); }
    public Set<String> getResolvedDeps() { return Collections.unmodifiableSet(resolvedDeps); }

    // --- Setters ---

    public void setState(PluginState state) { this.state = state; }
    public void setClassLoader(PluginClassLoader classLoader) { this.classLoader = classLoader; }
    public void setInstance(KirisamePlugin instance) { this.instance = instance; }
    public void setTransformInstance(ClassTransform transformInstance) { this.transformInstance = transformInstance; }
    public void addResolvedDep(String name) { resolvedDeps.add(name); }

    /**
     * Returns all dependency names (both required and optional).
     */
    public List<String> getAllDeps() {
        List<String> all = new ArrayList<>(requiredDeps);
        all.addAll(optionalDeps);
        return all;
    }

    @Override
    public String toString() {
        return "PluginInfo{name=" + getName() + ", state=" + state + "}";
    }
}
