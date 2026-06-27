package org.kirisame.mc.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Launcher configuration loaded from launcher.json.
 */
public record LauncherConfig(
        String kirisameJar,
        String agentJar,
        String javaHome,
        List<String> jvmArgs,
        List<String> gameArgs
) {
    private static final String CONFIG_FILE = "launcher.json";

    /**
     * Loads configuration from launcher.json in the working directory,
     * falling back to bundled defaults.
     */
    public static LauncherConfig load() {
        Path configPath = Path.of(System.getProperty("user.dir"), CONFIG_FILE);
        JsonObject json = null;

        // Try external file first
        if (Files.exists(configPath)) {
            try (InputStream is = Files.newInputStream(configPath)) {
                json = new Gson().fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
            } catch (IOException e) {
                System.err.println("[Kirisame Launcher] Failed to load " + CONFIG_FILE + ": " + e.getMessage());
            }
        }

        // Fall back to bundled config
        if (json == null) {
            try (InputStream is = LauncherConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
                if (is != null) {
                    json = new Gson().fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
                }
            } catch (IOException e) {
                System.err.println("[Kirisame Launcher] Failed to load bundled config: " + e.getMessage());
            }
        }

        if (json == null) {
            json = new JsonObject();
        }

        String kirisameJar = getString(json, "kirisameJar", "kirisame-core-1.0-SNAPSHOT-all.jar");
        String agentJar = getString(json, "agentJar", "kirisame-agent-1.0-SNAPSHOT-all.jar");
        String javaHome = getString(json, "javaHome", System.getProperty("java.home"));
        List<String> jvmArgs = getStringList(json, "jvmArgs");
        List<String> gameArgs = getStringList(json, "gameArgs");

        return new LauncherConfig(kirisameJar, agentJar, javaHome, jvmArgs, gameArgs);
    }

    private static String getString(JsonObject json, String key, String defaultValue) {
        return json.has(key) ? json.get(key).getAsString() : defaultValue;
    }

    private static List<String> getStringList(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        json.getAsJsonArray(key).forEach(e -> list.add(e.getAsString()));
        return list;
    }
}
