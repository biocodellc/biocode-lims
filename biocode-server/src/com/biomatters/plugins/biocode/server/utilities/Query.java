package com.biomatters.plugins.biocode.server.utilities;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;
import java.util.*;


/**
 * @author Gen Li
 *         Created on 5/06/14 11:55 AM
 */
/* Composite Query data structure. */
abstract class Query {
    abstract LimsSearchResult execute(Map<String, Object> tissuesWorkflowsPlatesSequences, Set<String> tissuesToMatch) throws DatabaseServiceException;
}