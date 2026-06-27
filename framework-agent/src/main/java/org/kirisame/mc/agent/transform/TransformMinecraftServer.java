package org.kirisame.mc.agent.transform;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.kirisame.mc.agent.AgentMessageDispatcher;
import org.kirisame.mc.api.event.impl.ServerStartEvent;
import org.kirisame.mc.api.event.impl.ServerTickEvent;

/**
 * Instruments MinecraftServer methods:
 * - tickServer: fires ServerTickEvent after each tick
 * - runServer: fires ServerStartEvent when the server is ready
 */
public class TransformMinecraftServer {

    public AgentBuilder apply(AgentBuilder builder) {
        return builder
                .type(ElementMatchers.named("net.minecraft.server.MinecraftServer"))
                .transform((b, typeDescription, classLoader, module, protectionDomain) ->
                        b.visit(Advice.to(TickServerAdvice.class)
                                .on(ElementMatchers.named("tickServer")))
                         .visit(Advice.to(RunServerAdvice.class)
                                .on(ElementMatchers.named("runServer"))));
    }

    public static class TickServerAdvice {
        @Advice.OnMethodExit
        public static void onExit() {
            AgentMessageDispatcher.dispatch(new ServerTickEvent());
        }
    }

    public static class RunServerAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This Object server) {
            AgentMessageDispatcher.dispatch(new ServerStartEvent());
        }
    }
}
