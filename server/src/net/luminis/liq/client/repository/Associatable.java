package net.luminis.liq.client.repository;

import java.util.List;

/**
 * The Associatable interface is to be implemented by any object that wants to be
 * used in an Association.
 */
public interface Associatable {
    /**
     * Adds the association to this object, which has the given class. The 'other side' of
     * the association will now show up when filtering for <code>clazz</code>, which is the
     * class of the other end of the association.
     */
    @SuppressWarnings("unchecked")
    public <T extends Associatable> void add(Association association, Class<T> clazz);
    /**
     * Removes the association from this object, with the given class. The 'other side' of
     * the association will no longer show up when filtering for <code>clazz</code>.
     */
    @SuppressWarnings("unchecked")
    public <T extends Associatable> void remove(Association association, Class<T> clazz);
    /**
     * Gets all Associatable objects of the <code>clazz</code> with which this object
     * is associated. If <code>clazz</code> is not in use, this function will return
     * an empty list.
     */
    public <T extends Associatable> List<T> getAssociations(Class<T> clazz);
    /**
     * Checks whether this object is related with <code>obj</code>, which is to be of class
     * <code>clazz</code>. Will also return <code>false</code> when the class does not match.
     */
    public <T extends Associatable> boolean isAssociated(Object obj, Class<T> clazz);
    /**
     * Returns the associations that exist between this object and the other, of the
     * given <code>clazz</code>, in a typed list of associations <code>associationType</code>.
     * Will return an empty list if no associations exist.
     */
    @SuppressWarnings("unchecked")
    public <T extends Associatable, A extends Association> List<A> getAssociationsWith(Associatable other, Class<T> clazz, Class<A> associationType);
}
