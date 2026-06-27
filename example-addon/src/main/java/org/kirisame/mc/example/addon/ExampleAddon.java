package org.kirisame.mc.example.addon;

import org.kirisame.mc.api.event.EventHandler;
import org.kirisame.mc.api.event.impl.PlayerJoinEvent;
import org.kirisame.mc.api.plugin.Dependency;
import org.kirisame.mc.api.plugin.KirisamePlugin;
import org.kirisame.mc.api.plugin.PluginDescriptor;
import org.kirisame.mc.example.ExamplePlugin;

/**
 * Example addon that depends on ExamplePlugin.
 * Demonstrates inter-plugin dependency and cross-plugin class access.
 */
@PluginDescriptor(
        name = "ExampleAddon",
        version = "1.0.0",
        author = "Kirisame",
        description = "An example addon that depends on ExamplePlugin",
        dependencies = @Dependency(plugin = "ExamplePlugin")
)
public class ExampleAddon extends KirisamePlugin {

    @Override
    protected void onLoad() {
        // Access the dependency plugin instance
        ExamplePlugin examplePlugin = getContext().getDependency(ExamplePlugin.class);
        getLogger().info("ExampleAddon loaded! Tick count from ExamplePlugin: {}", examplePlugin.getTickCount());

        // Register for events
        getContext().getEventBus().register(this);
    }

    @Override
    protected void onUnload() {
        getLogger().info("ExampleAddon unloaded!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        getLogger().info("[Addon] Welcome, {}!", event.getPlayerName());
    }
}
