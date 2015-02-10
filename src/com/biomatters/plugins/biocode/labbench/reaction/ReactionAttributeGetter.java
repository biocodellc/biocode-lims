package com.biomatters.plugins.biocode.labbench.reaction;

/**
 *
 * @author Gen Li
 *         Created on 10/02/15 3:39 PM
 */
public interface ReactionAttributeGetter<T> {
    public T get(Reaction reaction);

    public String getAttributeName();
}
