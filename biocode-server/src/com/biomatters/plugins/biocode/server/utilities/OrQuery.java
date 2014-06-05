package com.biomatters.plugins.biocode.server.utilities;

import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PlateDocument;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Gen Li
 *         Created on 5/06/14 12:02 PM
 */
class OrQuery extends CompoundQuery {

    OrQuery(Query LHS, Query RHS) {
        super(LHS, RHS);
    }

    @Override
    LimsSearchResult combineResults(List<FimsSample> LHSTissueSamples,
                                    List<FimsSample> RHSTissueSamples,
                                    List<WorkflowDocument> LHSWorkflows,
                                    List<WorkflowDocument> RHSWorkflows,
                                    List<PlateDocument> LHSPlates,
                                    List<PlateDocument> RHSPlates,
                                    List<Integer> LHSSequenceIds,
                                    List<Integer> RHSSequenceIds) {

        LimsSearchResult combinedResult = new LimsSearchResult();

        Set<FimsSample> combinedSampleTissues = new HashSet<FimsSample>();
        Set<WorkflowDocument> combinedWorkflowDocuments = new HashSet<WorkflowDocument>();
        Set<PlateDocument> combinedPlateDocuments = new HashSet<PlateDocument>();
        Set<Integer> combinedSequenceIds = new HashSet<Integer>();

        combinedSampleTissues.addAll(LHSTissueSamples);
        combinedSampleTissues.addAll(RHSTissueSamples);

        combinedWorkflowDocuments.addAll(LHSWorkflows);
        combinedWorkflowDocuments.addAll(RHSWorkflows);

        combinedPlateDocuments.addAll(LHSPlates);
        combinedPlateDocuments.addAll(RHSPlates);

        combinedSequenceIds.addAll(LHSSequenceIds);
        combinedSequenceIds.addAll(RHSSequenceIds);

        combinedResult.addAllTissueSamples(combinedSampleTissues);
        combinedResult.addAllWorkflows(combinedWorkflowDocuments);
        combinedResult.addAllPlates(combinedPlateDocuments);
        combinedResult.addAllSequenceIDs(combinedSequenceIds);

        return combinedResult;
    }
}