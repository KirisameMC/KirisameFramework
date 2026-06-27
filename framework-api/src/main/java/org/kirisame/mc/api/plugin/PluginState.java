package org.kirisame.mc.api.plugin;

/**
 * Represents the lifecycle state of a plugin.
 */
public enum PluginState {
    /** JAR file discovered, not yet processed. */
    DISCOVERED,

    /** Dependencies have been validated and load order resolved. */
    DEPS_RESOLVED,

    /** ClassLoader created, plugin instance instantiated, context injected. */
    LOADED,

    /** {@link KirisamePlugin#onLoad()} has been called. */
    ENABLED,

    /** {@link KirisamePlugin#onUnload()} has been called. */
    DISABLED,

    /** Plugin loading or execution failed. */
    FAILED
}
