package org.kirisame.mc.api.event;

/**
 * Interface for events that can be cancelled.
 * When an event is cancelled, handlers with {@code ignoreCancelled = true} will still receive it.
 */
public interface Cancellable {
    boolean isCancelled();
    void setCancelled(boolean cancelled);
}
