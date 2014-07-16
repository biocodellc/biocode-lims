package com.biomatters.plugins.biocode.server.utilities;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;

import javax.ws.rs.NotFoundException;
import java.util.*;

/**
 * @author Gen Li
 *         Created on 28/05/14 11:59 AM
 */
public class RestUtilities {
    private RestUtilities() {
        // Can't instantiate
    }

    public static <T> T getOnlyItemFromList(List<T> list, String noItemErrorMessage) {
        if (list.isEmpty()) {
            throw new NotFoundException(noItemErrorMessage);
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

        List<DocumentField> searchAttributes = new ArrayList<DocumentField>(LIMSConnection.getSearchAttributes());
        searchAttributes.addAll(BiocodeService.getInstance().getActiveFIMSConnection().getSearchAttributes());

        return new QueryParser(searchAttributes).parseQuery(query).execute(TISSUES_WORKFLOWS_PLATES_SEQUENCES, tissuesToMatch);
    }

    public static List<String> getListFromString(String stringList) {
        if(stringList == null) {
            return null;
        }
        List<String> strings = new ArrayList<String>();
        for (String item : Arrays.asList(stringList.split(","))) {
            String toAdd = item.trim();
            if(!toAdd.isEmpty()) {
                strings.add(item);
            }
        }
        return strings;
    }
}