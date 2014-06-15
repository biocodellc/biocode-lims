package com.biomatters.plugins.biocode.server.utilities;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;

import java.util.Map;
import java.util.Set;

/**
 * @author Gen Li
 *         Created on 5/06/14 12:01 PM
 */
abstract class BasicQuery extends Query {
    @Override
    LimsSearchResult execute(Map<String, Object> tissuesWorkflowsPlatesSequences, Set<String> tissuesToMatch) throws DatabaseServiceException {
        return BiocodeService.getInstance().getActiveLIMSConnection().getMatchingDocumentsFromLims(createGeneiousQuery(tissuesWorkflowsPlatesSequences), tissuesToMatch, null);
    }

    abstract com.biomatters.geneious.publicapi.databaseservice.Query createGeneiousQuery(Map<String, Object> tissuesWorkflowsPlatesSequences);
}