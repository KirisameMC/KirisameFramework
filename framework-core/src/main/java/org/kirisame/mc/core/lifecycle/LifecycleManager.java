package org.kirisame.mc.core.lifecycle;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.kirisame.mc.agent.AgentBridge;
import org.kirisame.mc.api.command.CommandSender;
import org.kirisame.mc.api.event.EventHandler;
import org.kirisame.mc.api.event.EventBus;
import org.kirisame.mc.api.event.impl.ServerStartEvent;
import org.kirisame.mc.api.event.impl.ServerStopEvent;
import org.kirisame.mc.api.event.impl.ServerTickEvent;
import org.kirisame.mc.api.exception.DependencyException;
import org.kirisame.mc.api.exception.PluginLoadException;
import org.kirisame.mc.core.command.CommandManager;
import org.kirisame.mc.core.console.ConsoleInterceptor;
import org.kirisame.mc.core.console.ConsoleParser;
import org.kirisame.mc.core.console.OriginalOutput;
import org.kirisame.mc.core.console.SystemInInterceptor;
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
import java.util.List;
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
    private SystemInInterceptor systemInInterceptor;
    private ConsoleParser consoleParser;

    // Configuration
    private JsonObject config;
    private Path workDir;

    public void start(String[] args) {
        try {
            init();
            discoverPlugins();
            resolveDependencies();
            applyPluginTransforms();  // Before server start (reads bytecode from JARs)
            startMinecraftServer(args);
            waitForServer();
            loadPlugins();            // After server start (PluginCL now sees NMS)
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

        // Register this lifecycle manager as an event listener (for ServerStartEvent)
        eventBus.register(this);

        // Register built-in commands
        registerBuiltinCommands();

        // Capture original System.out BEFORE installing the interceptor
        OriginalOutput.capture();

        // Install console interceptor (output side)
        consoleParser = new ConsoleParser(eventBus);
        consoleInterceptor = new ConsoleInterceptor(
                this::onConsoleMessage,
                this::onConsoleCommand
        );
        consoleInterceptor.install();

        // Install System.in interceptor (input side) for !! commands
        try {
            systemInInterceptor = new SystemInInterceptor(this::onConsoleCommand);
            systemInInterceptor.install();
            Logger.info("System.in interceptor installed");
        } catch (Exception e) {
            Logger.warn(e, "Failed to install System.in interceptor");
        }

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
        ClassLoader frameworkCL = getClass().getClassLoader();
        ClassLoader serverCL = serverLoader != null ? serverLoader.getServerClassLoader() : null;
        pluginManager.loadPlugins(frameworkCL, serverCL);
    }

    private void applyPluginTransforms() {
        pluginManager.applyTransforms();
    }

    private void startMinecraftServer(String[] args) {
        String jarName = getConfigString("server.jar", "server.jar");
        Path serverJar = workDir.resolve(jarName);

        // Server CL parent includes framework CL + transform classloaders
        // so Advice classes from plugin transforms are resolvable
        ClassLoader frameworkCL = getClass().getClassLoader();
        ClassLoader serverParentCL = pluginManager.getServerParentClassLoader(frameworkCL);

        try {
            serverLoader = new MinecraftServerLoader(serverJar);
            ClassLoader serverClassLoader = serverLoader.load(serverParentCL);
            serverLoader.startServer(args);
        } catch (Exception e) {
            Logger.error(e, "Failed to start Minecraft server");
            throw new RuntimeException("Cannot start server", e);
        }
    }

    private void waitForServer() {
        state = State.WAIT_SERVER;
        Logger.info("Waiting for Minecraft server to start...");

        boolean serverThreadSeen = false;
        int maxWait = 300; // 5 minutes timeout
        int waited = 0;

        while (!serverStarted && waited < maxWait) {
            try {
                Thread.sleep(1000);
                waited++;

                // Check if "Server thread" is alive
                boolean threadAlive = isServerThreadAlive();

                if (threadAlive) {
                    serverThreadSeen = true;
                } else if (serverThreadSeen) {
                    // Server thread started then died → crash during startup
                    Logger.error("Server thread crashed during startup!");
                    return;
                }

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

                boolean serverAlive = isServerThreadAlive();

                // Break if server has started and thread died (normal stop or crash)
                if (serverStarted && !serverAlive) {
                    Logger.info("Server thread has stopped");
                    break;
                }

                // Break if server thread died before ever starting (crash during init)
                if (!serverStarted && !serverAlive) {
                    Logger.error("Server thread is not running and server was never started — likely crashed");
                    break;
                }

                Thread.sleep(50); // ~20 ticks per second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (!running) {
            Logger.info("Framework stopped by command");
        }
    }

    private boolean isServerThreadAlive() {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if ("Server thread".equals(thread.getName()) && thread.isAlive()) {
                return true;
            }
        }
        return false;
    }

    private void shutdown() {
        state = State.SHUTDOWN;
        Logger.info("Framework shutting down...");

        // Post server stop event — plugins can cancel to request a reboot
        ServerStopEvent stopEvent = eventBus.post(new ServerStopEvent());
        if (stopEvent.isCancelled()) {
            try {
                Files.writeString(workDir.resolve("REBOOT_FLAG.flag"), "reboot");
                Logger.info("Reboot requested by plugin");
            } catch (IOException e) {
                Logger.error(e, "Failed to write reboot flag");
            }
        }

        // Disable plugins (in reverse dependency order)
        if (pluginManager != null) {
            pluginManager.disablePlugins();
        }

        // Uninstall interceptors
        if (systemInInterceptor != null) {
            systemInInterceptor.uninstall();
        }
        if (consoleInterceptor != null) {
            consoleInterceptor.uninstall();
        }

        Logger.info("Framework shutdown complete");

        // Force exit to ensure the JVM terminates
        // (daemon threads or leaked non-daemon threads could keep the process alive)
        System.exit(0);
    }

    // --- Console handling ---

    private void onConsoleMessage(ParsedMessage message) {
        if (consoleParser != null) {
            consoleParser.parse(message);
        }
    }

    private void onConsoleCommand(String input) {
        if (commandManager != null) {
            // Use OriginalOutput to avoid re-interception
            CommandSender sender = msg -> OriginalOutput.get().println(msg);
            commandManager.dispatch(input.trim(), sender);
        }
    }

    private void registerBuiltinCommands() {
        // Built-in stop command — force terminate
        commandManager.registerCommand("stop", "Force terminates the server and framework", (sender, args) -> {
            sender.sendMessage("Force stopping...");
            System.exit(0);
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

    @EventHandler
    public void onServerStart(ServerStartEvent event) {
        if (!serverStarted) {
            serverStarted = true;
            Logger.info("Server started (detected via event)");
        }
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
