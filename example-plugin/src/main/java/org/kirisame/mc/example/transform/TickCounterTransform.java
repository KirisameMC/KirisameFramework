package org.kirisame.mc.example.transform;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.kirisame.mc.api.transform.ClassTransform;
import org.kirisame.mc.api.transform.TransformBridge;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example transform: intercepts MinecraftServer.tickServer() and communicates
 * with ExamplePlugin via the TransformBridge.
 *
 * Demonstrates:
 * - Writing data to the shared bridge (tick count)
 * - Plugin reads the data in its event handlers
 */
public class TickCounterTransform implements ClassTransform {

    /** Injected by the framework at plugin load time. Must be public static. */
    public static TransformBridge TRANSFORM_BRIDGE;

    @Override
    public boolean matches(String className, Class<?> classBeingRedefined) {
        return "net.minecraft.server.MinecraftServer".equals(className);
    }

    @Override
    public AgentBuilder apply(AgentBuilder builder) {
        return builder
                .type(ElementMatchers.named("net.minecraft.server.MinecraftServer"))
                .transform((b, typeDescription, classLoader, module, protectionDomain) ->
                        b.visit(Advice.to(TickAdvice.class)
                                .on(ElementMatchers.named("tickServer"))));
    }

    /**
     * Advice class — injected into MinecraftServer.tickServer().
     * All fields accessed by the advice code must be public (cross-classloader rule).
     */
    public static class TickAdvice {

        public static final AtomicInteger counter = new AtomicInteger(0);

        @Advice.OnMethodExit
        public static void onTickExit() {
            int count = counter.incrementAndGet();

            // Write tick count to the bridge so the plugin can read it
            if (TRANSFORM_BRIDGE != null) {
                TRANSFORM_BRIDGE.setData("server-tick", count);
            }
        }
    }
}
