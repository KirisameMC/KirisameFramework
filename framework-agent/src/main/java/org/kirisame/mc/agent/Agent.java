package org.kirisame.mc.agent;

import java.lang.instrument.Instrumentation;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import org.kirisame.mc.agent.transform.*;

/**
 * Java Agent premain entry point.
 * Instruments Minecraft server classes before they are loaded.
 */
public class Agent {

    public static void premain(String args, Instrumentation inst) {
        System.out.println("[Kirisame] Agent premain loaded");

        AgentBuilder builder = new AgentBuilder.Default(new ByteBuddy().with(TypeValidation.DISABLED))
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .ignore(nameStartsWith("net.bytebuddy.")
                        .or(nameStartsWith("java."))
                        .or(nameStartsWith("javax."))
                        .or(nameStartsWith("sun."))
                        .or(nameStartsWith("jdk."))
                        .or(nameStartsWith("com.sun."))
                        .or(nameStartsWith("org.tinylog."))
                        .or(nameStartsWith("com.intellij."))
                        .or(nameStartsWith("org.kirisame.mc.agent.")));

        // Apply built-in transforms
        builder = BuiltInTransforms.applyAll(builder);

        // Start the agent and install transformers
        builder.installOn(inst);

        // Capture for lambda
        final AgentBuilder finalBuilder = builder;

        // Spawn a thread to bridge Instrumentation + AgentBuilder to the core framework
        Thread bridgeThread = new Thread(() -> {
            System.out.println("[Kirisame] Agent bridge thread started, waiting for core framework...");
            while (true) {
                try {
                    // Wait for the core to load AgentBridge
                    Class<?> bridgeClass = Class.forName("org.kirisame.mc.agent.AgentBridge");
                    // Set the values
                    var instField = bridgeClass.getDeclaredField("instrumentation");
                    instField.setAccessible(true);
                    instField.set(null, inst);

                    var builderField = bridgeClass.getDeclaredField("agentBuilder");
                    builderField.setAccessible(true);
                    builderField.set(null, finalBuilder);

                    System.out.println("[Kirisame] Agent bridge established");
                    break;
                } catch (ClassNotFoundException e) {
                    // Core not loaded yet, keep waiting
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("[Kirisame] Agent bridge error: " + e.getMessage());
                    break;
                }
            }
        }, "kirisame-agent-bridge");
        bridgeThread.setDaemon(true);
        bridgeThread.start();
    }
}
