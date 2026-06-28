package org.kirisame.mc.core.minecraft;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.tinylog.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads and starts the Minecraft server JAR via reflection.
 */
public class MinecraftServerLoader {

    private final Path serverJarPath;
    private URLClassLoader serverClassLoader;
    private String mcVersion;

    public MinecraftServerLoader(Path serverJarPath) {
        this.serverJarPath = serverJarPath;
    }

    /**
     * Loads the server JAR and detects the Minecraft version.
     *
     * @param parentClassLoader the parent classloader (should include plugin classloaders
     *                          so Advice classes from plugins are visible to the server)
     * @return a ClassLoader that can access MC classes
     */
    public ClassLoader load(ClassLoader parentClassLoader) throws IOException {
        if (!Files.exists(serverJarPath)) {
            throw new IOException("Server JAR not found: " + serverJarPath);
        }

        URL jarUrl = serverJarPath.toUri().toURL();
        serverClassLoader = new URLClassLoader(new URL[]{jarUrl}, parentClassLoader);

        // Try to read version.json from the JAR
        try {
            readVersion();
        } catch (Exception e) {
            Logger.warn("Could not read version.json from server JAR: {}", e.getMessage());
            mcVersion = "unknown";
        }

        Logger.info("Loaded server JAR: {} (version: {})", serverJarPath.getFileName(), mcVersion);
        return serverClassLoader;
    }

    private void readVersion() throws IOException {
        try (InputStream is = serverClassLoader.getResourceAsStream("version.json")) {
            if (is != null) {
                JsonObject json = new Gson().fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
                mcVersion = json.has("name") ? json.get("name").getAsString() : "unknown";
            }
        }
    }

    /**
     * Starts the Minecraft server by invoking its main method reflectively.
     */
    public void startServer(String[] args) throws Exception {
        if (serverClassLoader == null) {
            throw new IllegalStateException("Server JAR not loaded. Call load() first.");
        }

        Class<?> mainClass = Class.forName("net.minecraft.bundler.Main", true, serverClassLoader);
        Method mainMethod = mainClass.getMethod("main", String[].class);
        Logger.info("Starting Minecraft server...");

        Thread serverThread = new Thread(() -> {
            try {
                mainMethod.invoke(null, (Object) args);
            } catch (Exception e) {
                Logger.error(e, "Minecraft server exited with error");
            }
        }, "Server thread");

        serverThread.setContextClassLoader(serverClassLoader);
        serverThread.start();
    }

    public String getMcVersion() {
        return mcVersion;
    }

    public ClassLoader getServerClassLoader() {
        return serverClassLoader;
    }
}
