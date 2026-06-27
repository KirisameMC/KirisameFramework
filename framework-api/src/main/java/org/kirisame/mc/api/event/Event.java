package org.kirisame.mc.api.event;

/**
 * Base class for all events in the framework.
 */
public abstract class Event {

    private boolean handled = false;

    /**
     * Returns true if this event has been handled by a handler.
     */
    public final boolean isHandled() {
        return handled;
    }

    /**
     * Marks this event as handled.
     */
    public final void setHandled(boolean handled) {
        this.handled = handled;
    }
}
