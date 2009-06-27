package net.luminis.liq.client.repository.helper;

/**
 * Interface for resolving properties about the template's
 * environment which are to be used by an ArtifactPreprocessor.
 */
public interface PropertyResolver {
    /**
     * Gets a property, based on the given key. If the key cannot be found, <code>null</code>
     * can be used.
     * @param key A key to some property. Cannot be null.
     * @return The property identified by <code>key</code> if it can be found, <code>null</code> otherwise.
     */
    public String get(String key);
}
