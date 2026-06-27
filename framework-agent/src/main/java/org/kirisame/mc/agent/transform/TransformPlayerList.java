package org.kirisame.mc.agent.transform;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.kirisame.mc.agent.AgentMessageDispatcher;
import org.kirisame.mc.api.event.impl.PlayerJoinEvent;
import org.kirisame.mc.api.event.impl.PlayerQuitEvent;

/**
 * Instruments PlayerList.placeNewPlayer and PlayerList.remove to fire player join/quit events.
 */
public class TransformPlayerList {

    public AgentBuilder apply(AgentBuilder builder) {
        return builder
                .type(ElementMatchers.named("net.minecraft.server.players.PlayerList"))
                .transform((b, typeDescription, classLoader, module, protectionDomain) ->
                        b.visit(Advice.to(PlaceNewPlayerAdvice.class)
                                .on(ElementMatchers.named("placeNewPlayer")
                                        .and(ElementMatchers.takesArguments(2))))
                         .visit(Advice.to(RemoveAdvice.class)
                                .on(ElementMatchers.named("remove")
                                        .and(ElementMatchers.takesArguments(1)))));
    }

    public static class PlaceNewPlayerAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Argument(0) Object connection, @Advice.Argument(1) Object player) {
            AgentMessageDispatcher.dispatch(new PlayerJoinEvent(player));
        }
    }

    public static class RemoveAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Argument(0) Object player) {
            AgentMessageDispatcher.dispatch(new PlayerQuitEvent(player));
        }
    }
}
