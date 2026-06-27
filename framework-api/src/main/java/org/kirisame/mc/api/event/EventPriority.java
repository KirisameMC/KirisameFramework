package org.kirisame.mc.api.event;

/**
 * Event handler priority. Handlers are invoked from LOWEST to HIGHEST, then MONITOR.
 */
public enum EventPriority {
    LOWEST,
    LOW,
    NORMAL,
    HIGH,
    HIGHEST,
    MONITOR
}
