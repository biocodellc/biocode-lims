package com.biomatters.plugins.biocode.server.utilities;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;

import javax.ws.rs.core.NoContentException;
import java.util.*;

/**
 * @author Gen Li
 *         Created on 28/05/14 11:59 AM
 */
public class RestUtilities {

    private RestUtilities() {
        // Can't instantiate
    }

    public static <T> T getOnlyItemFromList(List<T> list, String noItemErrorMessage) throws NoContentException {
        if (list.isEmpty()) {
            throw new NoContentException(noItemErrorMessage);
        }
        return list.get(0);
    }

    public static LimsSearchResult getSearchResults(String query,
                                                    boolean retrieveTissues,
                                                    boolean retrieveWorkflows,
                                                    boolean retrievePlates,
                                                    boolean retrieveSequenceIds,
                                                    Set<String> tissuesToMatch) throws DatabaseServiceException {

        Map<String, Object> TISSUES_WORKFLOWS_PLATES_SEQUENCES = BiocodeService.getSearchDownloadOptions(retrieveTissues,
                                                                                                         retrieveWorkflows,
                                                                                                         retrievePlates,
                                                                                                         retrieveSequenceIds);

        Query q = new QueryParser().parseQuery(query);
        return q.execute(TISSUES_WORKFLOWS_PLATES_SEQUENCES, tissuesToMatch);
    }
}