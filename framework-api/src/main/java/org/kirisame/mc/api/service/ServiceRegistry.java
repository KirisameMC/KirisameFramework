package org.kirisame.mc.api.service;

import java.util.Optional;

/**
 * Service registry for inter-plugin service discovery.
 * Plugins can register service implementations that other plugins can look up.
 */
public interface ServiceRegistry {

    /**
     * Registers a service provider.
     * @throws IllegalArgumentException if a provider is already registered for this type
     */
    <T> void register(Class<T> serviceType, T provider);

    /**
     * Gets a registered service provider.
     * @throws java.util.NoSuchElementException if no provider is registered
     */
    <T> T get(Class<T> serviceType);

    /**
     * Tries to get a registered service provider.
     */
    <T> Optional<T> tryGet(Class<T> serviceType);
}
