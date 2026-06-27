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
        if (tickCount % 400 == 0) {
            getLogger().info("20 seconds passed, tick={}", tickCount);
        }
    }

    /**
     * Returns the current tick count. Used by ExampleAddon as a dependency service.
     */
    public int getTickCount() {
        return tickCount;
    }
}
