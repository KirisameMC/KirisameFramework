package org.kirisame.mc.api.plugin;

import org.kirisame.mc.api.event.EventBus;
import org.kirisame.mc.api.service.ServiceRegistry;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Facade providing plugins access to framework services.
 * Injected into every {@link KirisamePlugin} upon loading.
 */
public interface PluginContext {

    /** Returns the event bus for registering and posting events. */
    EventBus getEventBus();

    /** Returns the service registry for inter-plugin service discovery. */
    ServiceRegistry getServiceRegistry();

    /** Returns this plugin's descriptor annotation. */
    PluginDescriptor getDescriptor();

    /** Returns a per-plugin data directory (created automatically under plugins/{name}/). */
    Path getDataDirectory();

    /** Returns a logger scoped to this plugin. */
    PluginLogger getLogger();

    // --- Dependency access ---

    /**
     * Gets a loaded dependency plugin instance by class.
     * @throws org.kirisame.mc.api.exception.DependencyException if the dependency is not declared or not loaded
     */
    <T extends KirisamePlugin> T getDependency(Class<T> pluginClass);

    /**
     * Tries to get a loaded dependency plugin instance by class.
     * Returns empty if the dependency is not declared or not loaded (for optional dependencies).
     */
    <T extends KirisamePlugin> Optional<T> tryGetDependency(Class<T> pluginClass);

    /**
     * Gets a loaded dependency plugin instance by name.
     * @throws org.kirisame.mc.api.exception.DependencyException if the dependency is not declared or not loaded
     */
    KirisamePlugin getDependencyByName(String name);
}
