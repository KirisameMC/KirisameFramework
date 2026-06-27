package org.kirisame.mc.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import org.kirisame.mc.agent.transform.TransformChatHandler;
import org.kirisame.mc.agent.transform.TransformDedicatedServer;
import org.kirisame.mc.agent.transform.TransformMinecraftServer;
import org.kirisame.mc.agent.transform.TransformPlayerList;

/**
 * Applies all built-in transforms to the agent builder.
 */
public final class BuiltInTransforms {

    private BuiltInTransforms() {}

    /**
     * Applies all built-in transforms to the given builder.
     */
    public static AgentBuilder applyAll(AgentBuilder builder) {
        builder = new TransformPlayerList().apply(builder);
        builder = new TransformChatHandler().apply(builder);
        builder = new TransformMinecraftServer().apply(builder);
        builder = new TransformDedicatedServer().apply(builder);
        return builder;
    }
}
