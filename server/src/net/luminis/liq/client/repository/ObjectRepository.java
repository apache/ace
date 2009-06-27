package net.luminis.liq.client.repository;

import java.util.List;
import java.util.Map;

import org.osgi.framework.Filter;

/**
 * A basic object repository, storing implementers of RepositoryObject.
 */
public interface ObjectRepository<T extends RepositoryObject> {
    /**
     * Removes the given entity from this repository. Will silently fail
     * when the entity does not exist in this repository.
     */
    public void remove(T entity);
    /**
     * Gets a list of all entities in this repository.
     */
    public List<T> get();
    /**
     * Returns a list of all entities in this repository that satisfy
     * the conditions set in <code>filter</code>. If none match, an
     * empty list will be returned.
     */
    public List<T> get(Filter filter);

    /**
     * Creates a new inhabitant based on the given attributes. The object
     * will be stored in this repository's store, and will be returned.
     * @throws IllegalArgumentException Will be thrown when the attributes cannot be accepted.
     */
    public T create(Map<String, String> attributes, Map<String, String> tags) throws IllegalArgumentException;
}
