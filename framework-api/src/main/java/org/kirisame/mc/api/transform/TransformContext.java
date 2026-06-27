package org.kirisame.mc.api.transform;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * Provides access to the {@link Instrumentation} instance and the base {@link AgentBuilder}.
 * Available during transform application.
 */
public interface TransformContext {
    Instrumentation getInstrumentation();
    AgentBuilder getBaseBuilder();
}
