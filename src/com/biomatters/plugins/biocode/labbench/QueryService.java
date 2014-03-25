package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;

import java.util.List;

/**
 * Handles the communication between the FIMS/LIMS connections and the database service
 *
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 25/03/14 3:03 PM
 */
public abstract class QueryService {


    public abstract void retrieve(Query query, RetrieveCallback callback);
    public abstract void deletePlates(List<PlateDocument> plates);
    public abstract void deleteSequences(List<String> sequenceIds);
}
