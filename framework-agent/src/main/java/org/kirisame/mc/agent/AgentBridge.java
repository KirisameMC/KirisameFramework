package org.kirisame.mc.agent;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * Static bridge between the agent and the core framework.
 * The agent's premain thread sets these values; the core framework reads them later.
 */
public class AgentBridge {

    private static volatile Instrumentation instrumentation;
    private static volatile AgentBuilder agentBuilder;

    /**
     * Returns the {@link Instrumentation} instance, or null if the agent hasn't bridged yet.
     */
    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }

    /**
     * Returns the {@link AgentBuilder} with built-in transforms applied, or null.
     */
    public static AgentBuilder getAgentBuilder() {
        return agentBuilder;
    }

    /**
     * Returns true if the agent bridge has been established.
     */
    public static boolean isAvailable() {
        return instrumentation != null && agentBuilder != null;
    }
}
