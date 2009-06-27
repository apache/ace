package net.luminis.liq.client.repository.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.luminis.liq.client.repository.Association;
import net.luminis.liq.client.repository.AssociationRepository;
import net.luminis.liq.client.repository.RepositoryObject;

/**
 * A basic association repository. It cannot be directly instantiated, since some functionality
 * is delagated to deriving classes, being
 * <bl>
 * <li>The creation of new inhabitants</li>
 * <li>The generation of filter strings based on objects</li>
 * </bl>
 *
 * @param <L> The left side of the associations which will be stored in this repository.
 * @param <R> The right side of the associations which will be stored in this repository.
 * @param <I> An implementation of the association from <code>L</code> to <code>R</code>.
 * @param <T> The non-generic Association interface that <code>I</code> implements.
 */
public abstract class AssociationRepositoryImpl<L extends RepositoryObject, R extends RepositoryObject, I extends AssociationImpl<L, R, T>, T extends Association<L, R>> extends ObjectRepositoryImpl<I, T> implements AssociationRepository<L, R, T> {
    private Object m_lock = new Object();

    public AssociationRepositoryImpl(ChangeNotifier notifier, String xmlNode) {
        super(notifier, xmlNode);
    }

    @SuppressWarnings("unchecked")
    public T create(String left, int leftCardinality, String right, int rightCardinality) {
        synchronized (m_lock) {
            T association = null;
            try {
                Map<String, String> attributes = new HashMap<String, String>();
                attributes.put(Association.LEFT_ENDPOINT, left);
                attributes.put(Association.RIGHT_ENDPOINT, right);
                attributes.put(Association.LEFT_CARDINALITY, "" + leftCardinality);
                attributes.put(Association.RIGHT_CARDINALITY, "" + rightCardinality);
                association = (T) createNewInhabitant(attributes);
                add(association);
            }
            catch (Exception e) {
                // We have not been able to instantiate our constructor. Not much to do about that.
                e.printStackTrace();
            }
            return association;
        }
    }

    public T create(String left, String right) {
        return create(left, Integer.MAX_VALUE, right, Integer.MAX_VALUE);
    }

    public T create(L left, Map<String, String> leftProps, R right, Map<String, String> rightProps) {
        return create(left.getAssociationFilter(leftProps), left.getCardinality(leftProps),
            right.getAssociationFilter(rightProps), right.getCardinality(rightProps));
    }

    public T create(L left, R right) {
        return create(left, null, right, null);
    }

    public T create(List<L> left, List<R> right) {
        if ((left == null) || left.isEmpty()) {
            throw new IllegalArgumentException("The left side of the association cannot be empty.");
        }
        if ((right == null) || right.isEmpty()) {
            throw new IllegalArgumentException("The right side of the association cannot be empty.");
        }

        StringBuilder leftFilter = new StringBuilder("(|");
        for (L l : left) {
            leftFilter.append(l.getAssociationFilter(null));
        }
        leftFilter.append(")");

        StringBuilder rightFilter = new StringBuilder("(|");
        for (R r : right) {
            rightFilter.append(r.getAssociationFilter(null));
        }
        rightFilter.append(")");

        return create(leftFilter.toString(), Integer.MAX_VALUE, rightFilter.toString(), Integer.MAX_VALUE);
    }

    @Override
    public void remove(T entity) {
        synchronized (m_lock) {
            super.remove(entity);
            entity.remove();
        }
    }
}
