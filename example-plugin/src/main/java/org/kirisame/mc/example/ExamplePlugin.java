package org.kirisame.mc.example;

import org.kirisame.mc.api.event.EventHandler;
import org.kirisame.mc.api.event.impl.PlayerJoinEvent;
import org.kirisame.mc.api.event.impl.ServerTickEvent;
import org.kirisame.mc.api.plugin.KirisamePlugin;
import org.kirisame.mc.api.plugin.PluginDescriptor;

/**
 * Example plugin demonstrating basic framework usage.
 * Has no dependencies on other plugins.
 */
@PluginDescriptor(
        name = "ExamplePlugin",
        version = "1.0.0",
        author = "Kirisame",
        description = "A basic example plugin with no dependencies"
)
public class ExamplePlugin extends KirisamePlugin {

    /** Declares the transform class for the framework to load. */
    public static final String TRANSFORM_CLASS = "org.kirisame.mc.example.transform.TickCounterTransform";

    private int tickCount;

    @Override
    protected void onLoad() {
        getContext().getEventBus().register(this);
        getLogger().info("ExamplePlugin loaded!");
    }

    @Override
    protected void onUnload() {
        getLogger().info("ExamplePlugin unloaded after {} ticks", tickCount);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        getLogger().info("Player joined: {}", event.getPlayerName());
    }

    @EventHandler
    public void onTick(ServerTickEvent event) {
        tickCount++;
        // Read data written by the transform via TransformBridge
        if (tickCount % 400 == 0) {
            Object serverTick = getContext().getTransformBridge().getData("server-tick", "N/A");
            getLogger().info("20s passed, plugin-tick={}, server-tick={}", tickCount, serverTick);
        }
    }

    /**
     * Returns the current tick count. Used by ExampleAddon as a dependency service.
     */
    public int getTickCount() {
        return tickCount;
    }
}
