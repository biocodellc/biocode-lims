package com.biomatters.plugins.biocode.server.utilities.query;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PlateDocument;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Gen Li
 *         Created on 5/06/14 12:02 PM
 */
public abstract class CompoundQuery extends Query {
    private Query LHS, RHS;

    public CompoundQuery(Query LHS, Query RHS) {
        this.LHS = LHS;
        this.RHS = RHS;
    }

    public LimsSearchResult execute(Map<String, Object> tissuesWorkflowsPlatesSequences, Set<String> tissuesToMatch) throws DatabaseServiceException {
        LimsSearchResult LHSResult = LHS.execute(tissuesWorkflowsPlatesSequences, tissuesToMatch),
                         RHSResult = RHS.execute(tissuesWorkflowsPlatesSequences, tissuesToMatch);

        return combineResults(LHSResult.getTissueIds(),
                              RHSResult.getTissueIds(),
                              LHSResult.getWorkflows(),
                              RHSResult.getWorkflows(),
                              LHSResult.getPlates(),
                              RHSResult.getPlates(),
                              LHSResult.getSequenceIds(),
                              RHSResult.getSequenceIds());
    }

    public Query getLHS() { return LHS; }

    public Query getRHS() { return RHS; }

    protected final LimsSearchResult combineResults(List<String>       LHSTissueSamples,
                                          List<String>       RHSTissueSamples,
                                          List<WorkflowDocument> LHSWorkflows,
                                          List<WorkflowDocument> RHSWorkflows,
                                          List<PlateDocument>    LHSPlates,
                                          List<PlateDocument>    RHSPlates,
                                          List<Integer>          LHSSequenceIds,
                                          List<Integer>          RHSSequenceIds) {
        LimsSearchResult combinedResult = new LimsSearchResult();

        combinedResult.addAllTissueSamples(combineLists(LHSTissueSamples, RHSTissueSamples));
        combinedResult.addAllWorkflows(combineLists(LHSWorkflows, RHSWorkflows));
        combinedResult.addAllPlates(combineLists(LHSPlates, RHSPlates));
        combinedResult.addAllSequenceIDs(combineLists(LHSSequenceIds, RHSSequenceIds));

        return combinedResult;
    }

    public abstract <T> List<T> combineLists(List<T> one, List<T> two);
}