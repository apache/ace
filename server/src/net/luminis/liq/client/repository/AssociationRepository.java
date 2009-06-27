package net.luminis.liq.client.repository;

import java.util.List;
import java.util.Map;

/**
 * Represents a basic repository of associations. The associations are to be of type
 * <code>T</code>, associating types <code>L</code> and <code>R</code>.
 */
public interface AssociationRepository<L extends Associatable, R extends Associatable, T extends Association<L, R>> extends ObjectRepository<T>{
    /**
     * Creates a static association between two filters for objects, stores it,
     * and returns the association object. This association will link all objects
     * that apply to the filters in an m-to-n fashion.
     */
    public T create(String left, String right);
    /**
     * Creates a static association between two objects, stores it,
     * and returns the association object.
     */
    public T create(L left, R right);
    /**
     * Creates an association between the given objects, with the <code>Props</code> containing
     * additional directives for the endpoints, stores it, and returns the association.
     */
    public T create(L left, Map<String, String> leftProps, R right, Map<String, String> rightProps);
    /**
     * Creates a static association between two lists of objects, stores it,
     * and returns the association object.
     */
    public T create(List<L> left, List<R> right);
    /**
     * Removes the given association, and deletes the association from the left- and right
     * side of the association.
     */
    public void remove(T entity);
}
