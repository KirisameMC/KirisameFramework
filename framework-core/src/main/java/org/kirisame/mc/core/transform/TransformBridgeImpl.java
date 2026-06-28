package org.kirisame.mc.core.transform;

import org.kirisame.mc.api.event.Event;
import org.kirisame.mc.api.event.EventBus;
import org.kirisame.mc.api.transform.TransformBridge;
import org.tinylog.Logger;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe implementation of TransformBridge.
 * One instance per plugin, shared between the plugin and its transform.
 */
public class TransformBridgeImpl implements TransformBridge {

    private final ConcurrentHashMap<String, Object> data = new ConcurrentHashMap<>();
    private final EventBus eventBus;
    private final String pluginName;

    public TransformBridgeImpl(EventBus eventBus, String pluginName) {
        this.eventBus = eventBus;
        this.pluginName = pluginName;
    }

    @Override
    public void setData(String key, Object value) {
        data.put(key, value);
    }

    @Override
    public Object getData(String key, Object defaultValue) {
        return data.getOrDefault(key, defaultValue);
    }

    @Override
    public boolean hasData(String key) {
        return data.containsKey(key);
    }

    @Override
    public void removeData(String key) {
        data.remove(key);
    }

    @Override
    public void dispatchEvent(Event event) {
        try {
            eventBus.post(event);
        } catch (Exception e) {
            Logger.error(e, "Error dispatching event from transform bridge of plugin '{}'", pluginName);
        }
    }
}
