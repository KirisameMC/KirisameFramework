package org.kirisame.mc.api.event;

/**
 * Event bus for registering listeners and dispatching events.
 * Implementation is provided by the core module.
 */
public interface EventBus {

    /**
     * Registers a listener object. All methods annotated with {@link EventHandler}
     * will be discovered and registered.
     */
    void register(Object listener);

    /**
     * Unregisters all handlers belonging to the given listener object.
     */
    void unregister(Object listener);

    /**
     * Posts an event to all registered handlers.
     * @return the same event instance (allowing callers to inspect cancellation state)
     */
    <T extends Event> T post(T event);
}
