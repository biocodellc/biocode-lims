package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;

import java.util.List;

/**
 *
 * @author Gen Li
 *         Created on 10/02/15 4:53 PM
 */
public interface ExtractionReactionRetriever<T> {
    public List<ExtractionReaction> retrieve(LIMSConnection limsConnection, T retrieveBy) throws DatabaseServiceException;
}
