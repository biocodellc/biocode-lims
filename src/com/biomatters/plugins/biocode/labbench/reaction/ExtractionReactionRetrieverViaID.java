package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;

import java.util.Collection;
import java.util.List;

/**
 *
 * @author Gen Li
 *         Created on 10/02/15 4:58 PM
 */
public class ExtractionReactionRetrieverViaID implements ReactionRetriever<ExtractionReaction, LIMSConnection, List<String>> {
    public Collection<ExtractionReaction> retrieve(LIMSConnection limsConnection, List<String> extractionIDs) throws DatabaseServiceException {
        return limsConnection.getExtractionsForIds(extractionIDs);
    }
}
