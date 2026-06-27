package org.kirisame.mc.agent.transform;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.kirisame.mc.agent.AgentMessageDispatcher;
import org.kirisame.mc.api.event.impl.ServerStartEvent;

/**
 * Instruments DedicatedServer.initServer to capture the initialization result.
 */
public class TransformDedicatedServer {

    public AgentBuilder apply(AgentBuilder builder) {
        return builder
                .type(ElementMatchers.named("net.minecraft.server.dedicated.DedicatedServer"))
                .transform((b, typeDescription, classLoader, module, protectionDomain) ->
                        b.visit(Advice.to(InitServerAdvice.class)
                                .on(ElementMatchers.named("initServer"))));
    }

    public static class InitServerAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Return boolean success) {
            if (success) {
                AgentMessageDispatcher.dispatch(new ServerStartEvent());
            }
        }
    }
}
