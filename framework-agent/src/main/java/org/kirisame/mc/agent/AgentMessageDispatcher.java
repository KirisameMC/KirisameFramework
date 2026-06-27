package org.kirisame.mc.agent;

/**
 * Static bridge for dispatching events from agent-injected code to the core event bus.
 * The agent injects calls to {@link #dispatch(String, Object)} into MC methods.
 * The core framework registers its event bus here after initialization.
 */
public class AgentMessageDispatcher {

    private static volatile Object eventBus;
    private static volatile java.lang.reflect.Method postMethod;

    /**
     * Called by the core framework to register its event bus.
     */
    public static void registerEventBus(Object bus) {
        eventBus = bus;
        try {
            postMethod = bus.getClass().getMethod("post", org.kirisame.mc.api.event.Event.class);
        } catch (NoSuchMethodException e) {
            System.err.println("[Kirisame] Failed to find EventBus.post method: " + e.getMessage());
        }
    }

    /**
     * Dispatches an event to the core event bus.
     * Called by injected bytecode in MC methods.
     *
     * @param event the event to dispatch
     */
    public static void dispatch(org.kirisame.mc.api.event.Event event) {
        Object bus = eventBus;
        java.lang.reflect.Method method = postMethod;
        if (bus != null && method != null) {
            try {
                method.invoke(bus, event);
            } catch (Exception e) {
                System.err.println("[Kirisame] Failed to dispatch event: " + e.getMessage());
            }
        }
    }
}
