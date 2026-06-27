package org.kirisame.mc.api.transform;

import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * Interface for plugin-provided bytecode transforms.
 * Plugins that declare a transform class in their descriptor will have it applied
 * before the server starts.
 */
public interface ClassTransform {

    /**
     * Returns true if this transform targets the given class.
     *
     * @param className            the fully qualified class name being loaded
     * @param classBeingRedefined  null on initial load, non-null on retransformation
     */
    boolean matches(String className, Class<?> classBeingRedefined);

    /**
     * Apply bytecode modifications via ByteBuddy's AgentBuilder.
     * Called once for each matching class.
     *
     * @param builder the current agent builder (return a modified copy)
     * @return the modified builder
     */
    AgentBuilder apply(AgentBuilder builder);
}
