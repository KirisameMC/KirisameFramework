package org.kirisame.mc.core;

import org.kirisame.mc.core.lifecycle.LifecycleManager;

/**
 * Main entry point for the KirisameFramework core.
 * Can be launched directly or via the launcher.
 */
public final class KirisameFramework {

    private static final KirisameFramework INSTANCE = new KirisameFramework();

    private LifecycleManager lifecycle;

    public static KirisameFramework getInstance() {
        return INSTANCE;
    }

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        System.out.println("[Kirisame] KirisameFramework starting...");
        INSTANCE.start(args);
    }

    public void start(String[] args) {
        lifecycle = new LifecycleManager();
        lifecycle.start(args);
    }

    public LifecycleManager getLifecycle() {
        return lifecycle;
    }
}
