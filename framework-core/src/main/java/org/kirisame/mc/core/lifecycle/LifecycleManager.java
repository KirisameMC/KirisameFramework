package org.kirisame.mc.core.lifecycle;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.kirisame.mc.agent.AgentBridge;
import org.kirisame.mc.api.command.CommandSender;
import org.kirisame.mc.api.event.EventBus;
import org.kirisame.mc.api.event.impl.ServerStartEvent;
import org.kirisame.mc.api.event.impl.ServerStopEvent;
import org.kirisame.mc.api.event.impl.ServerTickEvent;
import org.kirisame.mc.api.exception.DependencyException;
import org.kirisame.mc.api.exception.PluginLoadException;
import org.kirisame.mc.core.command.CommandManager;
import org.kirisame.mc.core.console.ConsoleInterceptor;
import org.kirisame.mc.core.console.ConsoleParser;
import org.kirisame.mc.core.console.message.ParsedMessage;
import org.kirisame.mc.core.event.EventBusImpl;
import org.kirisame.mc.core.minecraft.MinecraftServerLoader;
import org.kirisame.mc.core.plugin.PluginManager;
import org.kirisame.mc.core.service.ServiceRegistryImpl;
import org.kirisame.mc.agent.AgentMessageDispatcher;
import org.tinylog.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Manages the KirisameFramework lifecycle as a state machine.
 *
 * States: INIT → DISCOVER_PLUGINS → RESOLVE_DEPS → LOAD_PLUGINS
 *       → WAIT_SERVER → ENABLE_PLUGINS → RUNNING → SHUTDOWN
 */
public class LifecycleManager {

    public enum State {
        INIT, DISCOVER_PLUGINS, RESOLVE_DEPS, LOAD_PLUGINS,
        WAIT_SERVER, ENABLE_PLUGINS, RUNNING, SHUTDOWN
    }

    private volatile State state = State.INIT;
    private volatile boolean running = true;
    private volatile boolean serverStarted = false;

    // Core components
    private EventBusImpl eventBus;
    private ServiceRegistryImpl serviceRegistry;
    private CommandManager commandManager;
    private PluginManager pluginManager;
    private MinecraftServerLoader serverLoader;
    private ConsoleInterceptor consoleInterceptor;
    private ConsoleParser consoleParser;

    // Configuration
    private JsonObject config;
    private Path workDir;

    public void start(String[] args) {
        try {
            init();
            discoverPlugins();
            resolveDependencies();
            loadPlugins();
            startMinecraftServer(args);
            waitForServer();
            enablePlugins();
            registerAgentBridge();
            runMainLoop();
        } catch (Exception e) {
            Logger.error(e, "Fatal error in lifecycle");
        } finally {
            shutdown();
        }
    }

    private void init() {
        state = State.INIT;
        workDir = Path.of(System.getProperty("user.dir"));

        // Load config
        config = loadConfig();

        // Initialize core components
        eventBus = new EventBusImpl();
        serviceRegistry = new ServiceRegistryImpl();
        commandManager = new CommandManager();

        // Register built-in commands
        registerBuiltinCommands();

        // Install console interceptor
        consoleParser = new ConsoleParser(eventBus);
        consoleInterceptor = new ConsoleInterceptor(
                this::onConsoleMessage,
                this::onConsoleCommand
        );
        consoleInterceptor.install();

        Logger.info("Framework initialized, workdir: {}", workDir);
    }

    private void discoverPlugins() {
        state = State.DISCOVER_PLUGINS;
        Path pluginsDir = workDir.resolve("plugins");
        pluginManager = new PluginManager(pluginsDir, eventBus, serviceRegistry);
        pluginManager.discoverPlugins();
    }

    private void resolveDependencies() {
        state = State.RESOLVE_DEPS;
        try {
            pluginManager.resolveDependencies();
        } catch (DependencyException e) {
            Logger.error("Dependency resolution failed: {}", e.getMessage());
            Logger.error("Aborting plugin loading. Please fix plugin dependencies.");
            // Continue without plugins rather than crashing
            throw e;
        }
    }

    private void loadPlugins() {
        state = State.LOAD_PLUGINS;
        pluginManager.loadPlugins(getClass().getClassLoader());
    }

    private void startMinecraftServer(String[] args) {
        String jarName = getConfigString("server.jar", "server.jar");
        Path serverJar = workDir.resolve(jarName);

        try {
            serverLoader = new MinecraftServerLoader(serverJar);
            ClassLoader serverClassLoader = serverLoader.load();
            serverLoader.startServer(args);
        } catch (Exception e) {
            Logger.error(e, "Failed to start Minecraft server");
            throw new RuntimeException("Cannot start server", e);
        }
    }

    private void waitForServer() {
        state = State.WAIT_SERVER;
        Logger.info("Waiting for Minecraft server to start...");

        // Wait for ServerStartEvent to be posted (via agent or console)
        int maxWait = 300; // 5 minutes timeout
        int waited = 0;
        while (!serverStarted && waited < maxWait) {
            try {
                Thread.sleep(1000);
                waited++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        if (!serverStarted) {
            Logger.warn("Server start timeout ({}s). Proceeding anyway.", maxWait);
        }
    }

    private void enablePlugins() {
        state = State.ENABLE_PLUGINS;
        pluginManager.enablePlugins();
    }

    private void registerAgentBridge() {
        // Register the event bus with the agent dispatcher so agent-injected code can post events
        AgentMessageDispatcher.registerEventBus(eventBus);
        Logger.info("Agent message dispatcher registered");
    }

    private void runMainLoop() {
        state = State.RUNNING;
        Logger.info("Framework main loop started");

        while (running) {
            try {
                // Post tick event
                eventBus.post(new ServerTickEvent());

                // Check if server thread is still alive
                boolean serverAlive = false;
                for (Thread thread : Thread.getAllStackTraces().keySet()) {
                    if ("Server thread".equals(thread.getName()) && thread.isAlive()) {
                        serverAlive = true;
                        break;
                    }
                }

                if (!serverAlive && serverStarted) {
                    Logger.info("Server thread has stopped");
                    break;
                }

                Thread.sleep(50); // ~20 ticks per second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void shutdown() {
        state = State.SHUTDOWN;
        Logger.info("Framework shutting down...");

        // Uninstall console interceptor
        if (consoleInterceptor != null) {
            consoleInterceptor.uninstall();
        }

        // Post server stop event
        ServerStopEvent stopEvent = eventBus.post(new ServerStopEvent());
        if (stopEvent.isCancelled()) {
            // Write reboot flag for the launcher
            try {
                Files.writeString(workDir.resolve("REBOOT_FLAG.flag"), "reboot");
                Logger.info("Reboot requested by plugin");
            } catch (IOException e) {
                Logger.error(e, "Failed to write reboot flag");
            }
        }

        // Disable plugins
        if (pluginManager != null) {
            pluginManager.disablePlugins();
        }

        Logger.info("Framework shutdown complete");
    }

    // --- Console handling ---

    private void onConsoleMessage(ParsedMessage message) {
        if (consoleParser != null) {
            consoleParser.parse(message);
        }
    }

    private void onConsoleCommand(String input) {
        if (commandManager != null) {
            CommandSender sender = System.out::println;
            commandManager.dispatch(input.trim(), sender);
        }
    }

    private void registerBuiltinCommands() {
        // Built-in stop command
        commandManager.registerCommand("stop", "Stops the server", (sender, args) -> {
            sender.sendMessage("Stopping server...");
            running = false;
        });

        // Built-in plugins command
        commandManager.registerCommand("plugins", "Lists loaded plugins", (sender, args) -> {
            if (pluginManager == null) {
                sender.sendMessage("Plugin manager not initialized");
                return;
            }
            sender.sendMessage("Loaded plugins:");
            for (String name : pluginManager.getPluginNames()) {
                var info = pluginManager.getPlugins().get(name);
                sender.sendMessage("  - " + name + " v" + info.getDescriptor().version()
                        + " [" + info.getState() + "]");
            }
        });
    }

    // --- Configuration ---

    private JsonObject loadConfig() {
        Path configPath = workDir.resolve("kirisame.json");
        if (Files.exists(configPath)) {
            try (InputStream is = Files.newInputStream(configPath)) {
                return new Gson().fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
            } catch (IOException e) {
                Logger.warn("Failed to load config from {}: {}", configPath, e.getMessage());
            }
        }

        // Fall back to bundled config
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("kirisame.json")) {
            if (is != null) {
                return new Gson().fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
            }
        } catch (IOException e) {
            Logger.warn("Failed to load bundled config: {}", e.getMessage());
        }

        return new JsonObject();
    }

    private String getConfigString(String path, String defaultValue) {
        String[] parts = path.split("\\.");
        JsonObject current = config;
        for (int i = 0; i < parts.length - 1; i++) {
            if (current.has(parts[i])) {
                current = current.getAsJsonObject(parts[i]);
            } else {
                return defaultValue;
            }
        }
        String lastKey = parts[parts.length - 1];
        return current.has(lastKey) ? current.get(lastKey).getAsString() : defaultValue;
    }

    // --- Event listeners ---

    public void onServerStart(ServerStartEvent event) {
        serverStarted = true;
    }

    // --- Getters ---

    public State getState() { return state; }
    public EventBus getEventBus() { return eventBus; }
    public ServiceRegistryImpl getServiceRegistry() { return serviceRegistry; }
    public CommandManager getCommandManager() { return commandManager; }
    public PluginManager getPluginManager() { return pluginManager; }
    public boolean isRunning() { return running; }
    public void stop() { running = false; }
}
