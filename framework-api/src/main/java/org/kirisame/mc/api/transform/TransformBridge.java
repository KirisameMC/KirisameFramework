package org.kirisame.mc.api.transform;

import org.kirisame.mc.api.event.Event;
import org.kirisame.mc.api.event.EventBus;

/**
 * Per-plugin bridge between a ClassTransform (running in the server classloader)
 * and the plugin instance (running in the plugin classloader).
 *
 * <p>Transforms cannot directly access plugin classes due to classloader isolation.
 * This bridge provides two communication channels:
 * <ul>
 *   <li><b>Shared data map</b> — transforms write values, plugins read them</li>
 *   <li><b>Event dispatch</b> — transforms post custom events, plugins listen via @EventHandler</li>
 * </ul>
 *
 * <h3>Usage in a Transform (server classloader context):</h3>
 * <pre>{@code
 * public static class MyAdvice {
 *     public static TransformBridge bridge;
 *
 *     @Advice.OnMethodExit
 *     public static void onExit() {
 *         bridge.setData("tps", getCurrentTps());
 *         bridge.dispatchEvent(new MyCustomEvent(...));
 *     }
 * }
 * }</pre>
 *
 * <h3>Usage in a Plugin (plugin classloader context):</h3>
 * <pre>{@code
 * @Override
 * protected void onLoad() {
 *     TransformBridge bridge = getContext().getTransformBridge();
 *     // Read data written by the transform
 *     double tps = (double) bridge.getData("tps", 20.0);
 * }
 * }</pre>
 */
public interface TransformBridge {

    /**
     * Stores a value in the shared data map.
     * Thread-safe.
     *
     * @param key   the data key
     * @param value the value to store
     */
    void setData(String key, Object value);

    /**
     * Retrieves a value from the shared data map.
     *
     * @param key          the data key
     * @param defaultValue the default value if the key is not present
     * @return the stored value, or defaultValue
     */
    Object getData(String key, Object defaultValue);

    /**
     * Checks if a key exists in the shared data map.
     */
    boolean hasData(String key);

    /**
     * Removes a value from the shared data map.
     */
    void removeData(String key);

    /**
     * Dispatches an event through the plugin's event bus.
     * Can be called from the server classloader context (transform/Advice code).
     *
     * @param event the event to dispatch
     */
    void dispatchEvent(Event event);
}
