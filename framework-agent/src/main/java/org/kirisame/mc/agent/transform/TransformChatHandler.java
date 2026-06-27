package org.kirisame.mc.agent.transform;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.kirisame.mc.agent.AgentMessageDispatcher;
import org.kirisame.mc.api.event.impl.PlayerChatEvent;

/**
 * Instruments ServerGamePacketListenerImpl.broadcastChatMessage to fire chat events.
 */
public class TransformChatHandler {

    public AgentBuilder apply(AgentBuilder builder) {
        return builder
                .type(ElementMatchers.named("net.minecraft.server.network.ServerGamePacketListenerImpl"))
                .transform((b, typeDescription, classLoader, module, protectionDomain) ->
                        b.visit(Advice.to(BroadcastChatAdvice.class)
                                .on(ElementMatchers.named("broadcastChatMessage"))));
    }

    public static class BroadcastChatAdvice {
        @Advice.OnMethodExit
        public static void onExit(
                @Advice.This Object handler,
                @Advice.Argument(0) Object message) {
            try {
                // Extract player from the connection handler
                var playerField = handler.getClass().getDeclaredField("player");
                playerField.setAccessible(true);
                Object player = playerField.get(handler);
                String msg = message != null ? message.toString() : "";
                AgentMessageDispatcher.dispatch(new PlayerChatEvent(player, msg));
            } catch (Exception e) {
                // Silently ignore — MC versions may differ
            }
        }
    }
}
