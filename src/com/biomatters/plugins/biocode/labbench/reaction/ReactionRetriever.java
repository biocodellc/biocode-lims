package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;

import java.util.Collection;

/**
 *
 * @author Gen Li
 *         Created on 10/02/15 4:53 PM
 */
public interface ReactionRetriever<T extends Reaction, T2, T3> {
    public Collection<T> retrieve(T2 source, T3 retrieveBy) throws DatabaseServiceException;
}
