package org.kirisame.mc.core.plugin;

import org.kirisame.mc.api.event.EventBus;
import org.kirisame.mc.api.exception.DependencyException;
import org.kirisame.mc.api.plugin.KirisamePlugin;
import org.kirisame.mc.api.plugin.PluginContext;
import org.kirisame.mc.api.plugin.PluginDescriptor;
import org.kirisame.mc.api.plugin.PluginLogger;
import org.kirisame.mc.api.service.ServiceRegistry;
import org.kirisame.mc.api.transform.TransformBridge;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Implementation of {@link PluginContext} providing plugins with framework access.
 */
public class PluginContextImpl implements PluginContext {

    private final PluginInfo info;
    private final EventBus eventBus;
    private final ServiceRegistry serviceRegistry;
    private final PluginManager pluginManager;
    private final PluginLogger logger;
    private final TransformBridge transformBridge;

    public PluginContextImpl(PluginInfo info, EventBus eventBus,
                             ServiceRegistry serviceRegistry, PluginManager pluginManager,
                             TransformBridge transformBridge) {
        this.info = info;
        this.eventBus = eventBus;
        this.serviceRegistry = serviceRegistry;
        this.pluginManager = pluginManager;
        this.logger = new PluginLoggerImpl(info.getName());
        this.transformBridge = transformBridge;
    }

    @Override
    public EventBus getEventBus() {
        return eventBus;
    }

    @Override
    public ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }

    @Override
    public PluginDescriptor getDescriptor() {
        return info.getDescriptor();
    }

    @Override
    public Path getDataDirectory() {
        return info.getJarPath().getParent().resolve(info.getName());
    }

    @Override
    public PluginLogger getLogger() {
        return logger;
    }

    @Override
    public TransformBridge getTransformBridge() {
        return transformBridge;
    }

    @Override
    public <T extends KirisamePlugin> T getDependency(Class<T> pluginClass) {
        T plugin = pluginManager.getPlugin(pluginClass);
        if (plugin == null) {
            throw new DependencyException(
                    "Plugin '" + info.getName() + "' tried to access dependency "
                            + pluginClass.getSimpleName() + " which is not loaded");
        }

        String depName = null;
        for (var entry : pluginManager.getPlugins().entrySet()) {
            if (entry.getValue().getInstance() == plugin) {
                depName = entry.getKey();
                break;
            }
        }

        if (depName == null || !info.getResolvedDeps().contains(depName)) {
            throw new DependencyException(
                    "Plugin '" + info.getName() + "' tried to access dependency '"
                            + depName + "' which is not declared as a dependency");
        }

        return plugin;
    }

    @Override
    public <T extends KirisamePlugin> Optional<T> tryGetDependency(Class<T> pluginClass) {
        try {
            return Optional.ofNullable(getDependency(pluginClass));
        } catch (DependencyException e) {
            return Optional.empty();
        }
    }

    @Override
    public KirisamePlugin getDependencyByName(String name) {
        if (!info.getResolvedDeps().contains(name)) {
            throw new DependencyException(
                    "Plugin '" + info.getName() + "' tried to access dependency '"
                            + name + "' which is not declared");
        }
        KirisamePlugin plugin = pluginManager.getPluginByName(name);
        if (plugin == null) {
            throw new DependencyException(
                    "Plugin '" + info.getName() + "' tried to access dependency '"
                            + name + "' which is not loaded");
        }
        return plugin;
    }
}
