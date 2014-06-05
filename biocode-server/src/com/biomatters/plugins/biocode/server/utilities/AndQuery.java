package com.biomatters.plugins.biocode.server.utilities;

import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PlateDocument;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;

import java.util.List;

/**
 * @author Gen Li
 *         Created on 5/06/14 12:01 PM
 */
class AndQuery extends CompoundQuery {

    AndQuery(Query LHS, Query RHS) {
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

        List<FimsSample> lesserNumberOfTissueSamples;
        List<FimsSample> greaterNumberOfTissueSamples;

        List<WorkflowDocument> lesserNumberOfWorkflows;
        List<WorkflowDocument> greaterNumberOfWorkflows;

        List<PlateDocument> lesserNumberOfPlates;
        List<PlateDocument> greaterNumberOfPlates;

        List<Integer> lesserNumberOfSequenceIds;
        List<Integer> greaterNumberOfSequenceIds;

        /* Differentiate LHS-RHS list pairs based on size. */
        if (LHSTissueSamples.size() <= RHSTissueSamples.size()) {
            lesserNumberOfTissueSamples = LHSTissueSamples;
            greaterNumberOfTissueSamples = RHSTissueSamples;
        } else {
            lesserNumberOfTissueSamples = RHSTissueSamples;
            greaterNumberOfTissueSamples = LHSTissueSamples;
        }

        if (LHSWorkflows.size() <= RHSWorkflows.size()) {
            lesserNumberOfWorkflows = LHSWorkflows;
            greaterNumberOfWorkflows = RHSWorkflows;
        } else {
            lesserNumberOfWorkflows = RHSWorkflows;
            greaterNumberOfWorkflows = LHSWorkflows;
        }

        if (LHSPlates.size() <= RHSPlates.size()) {
            lesserNumberOfPlates = LHSPlates;
            greaterNumberOfPlates = RHSPlates;
        } else {
            lesserNumberOfPlates = RHSPlates;
            greaterNumberOfPlates = LHSPlates;
        }

        if (LHSSequenceIds.size() <= RHSSequenceIds.size()) {
            lesserNumberOfSequenceIds = LHSSequenceIds;
            greaterNumberOfSequenceIds = RHSSequenceIds;
        } else {
            lesserNumberOfSequenceIds = RHSSequenceIds;
            greaterNumberOfSequenceIds = LHSSequenceIds;
        }

        /* Build combined result. */
        for (FimsSample tissueSample : lesserNumberOfTissueSamples) {
            if (greaterNumberOfTissueSamples.contains(tissueSample)) {
                combinedResult.addTissueSample(tissueSample);
            }
        }
        for (WorkflowDocument workflow : lesserNumberOfWorkflows) {
            if (greaterNumberOfWorkflows.contains(workflow)) {
                combinedResult.addWorkflow(workflow);
            }
        }
        for (PlateDocument plate : lesserNumberOfPlates) {
            if (greaterNumberOfPlates.contains(plate)) {
                combinedResult.addPlate(plate);
            }
        }
        for (Integer sequenceId : lesserNumberOfSequenceIds) {
            if (greaterNumberOfSequenceIds.contains(sequenceId)) {
                combinedResult.addSequenceID(sequenceId);
            }
        }

        return combinedResult;
    }
}