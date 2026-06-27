package org.kirisame.mc.api.event.impl;

import org.kirisame.mc.api.event.Cancellable;
import org.kirisame.mc.api.event.Event;

/**
 * Fired when the server is about to stop.
 * Cancelling this event will trigger a reboot via the launcher.
 */
public class ServerStopEvent extends Event implements Cancellable {

    private boolean cancelled;

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
