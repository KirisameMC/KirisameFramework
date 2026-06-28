package org.kirisame.mc.core.console;

import java.io.PrintStream;

/**
 * Holds a reference to the original System.out BEFORE the ConsoleInterceptor wraps it.
 * All framework logging should go through this to avoid being re-intercepted.
 */
public final class OriginalOutput {

    private static volatile PrintStream originalOut;

    /**
     * Must be called BEFORE installing ConsoleInterceptor.
     */
    public static void capture() {
        originalOut = System.out;
    }

    /**
     * Returns the original System.out (before interception).
     */
    public static PrintStream get() {
        PrintStream out = originalOut;
        return out != null ? out : System.out;
    }
}
