package org.kirisame.mc.api.plugin;

/**
 * Base class for all Kirisame plugins.
 * Subclasses should override {@link #onLoad()} and {@link #onUnload()} for lifecycle hooks.
 */
public abstract class KirisamePlugin {

    private PluginContext context;

    /**
     * Called when the plugin is enabled (after all dependencies are loaded).
     */
    protected void onLoad() {}

    /**
     * Called when the server is shutting down (before dependencies are unloaded).
     */
    protected void onUnload() {}

    /**
     * Returns the plugin context, providing access to the framework API.
     */
    protected final PluginContext getContext() {
        return context;
    }

    /**
     * Returns a logger scoped to this plugin.
     */
    protected final PluginLogger getLogger() {
        return context.getLogger();
    }

    // Internal: called by the framework to inject context
    @SuppressWarnings("unused")
    public final void _setContext(PluginContext context) {
        if (this.context != null) {
            throw new IllegalStateException("PluginContext already set for plugin: " + getClass().getName());
        }
        this.context = context;
    }

    // Internal: called by the framework to trigger lifecycle (public bridge to protected method)
    public final void _onLoad() { onLoad(); }
    public final void _onUnload() { onUnload(); }
}
