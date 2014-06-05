package com.biomatters.plugins.biocode.server.utilities;

import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PlateDocument;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;

import java.util.List;

/**
 * @author Gen Li
 *         Created on 5/06/14 12:02 PM
 */

class XorQuery extends CompoundQuery {

    XorQuery(Query LHS, Query RHS) {
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

        for (FimsSample tissueSample : LHSTissueSamples) {
            if (!RHSTissueSamples.contains(tissueSample)) {
                combinedResult.addTissueSample(tissueSample);
            }
        }
        for (FimsSample tissueSample : RHSTissueSamples) {
            if (!LHSTissueSamples.contains(tissueSample)) {
                combinedResult.addTissueSample(tissueSample);
            }
        }

        for (WorkflowDocument workflow : LHSWorkflows) {
            if (!RHSWorkflows.contains(workflow)) {
                combinedResult.addWorkflow(workflow);
            }
        }
        for (WorkflowDocument workflow : RHSWorkflows) {
            if (!LHSWorkflows.contains(workflow)) {
                combinedResult.addWorkflow(workflow);
            }
        }

        for (PlateDocument plate : LHSPlates) {
            if (!RHSPlates.contains(plate)) {
                combinedResult.addPlate(plate);
            }
        }
        for (PlateDocument plate : RHSPlates) {
            if (!LHSPlates.contains(plate)) {
                combinedResult.addPlate(plate);
            }
        }

        for (Integer sequenceId : LHSSequenceIds) {
            if (!RHSSequenceIds.contains(sequenceId)) {
                combinedResult.addSequenceID(sequenceId);
            }
        }
        for (Integer plate : RHSSequenceIds) {
            if (!LHSSequenceIds.contains(plate)) {
                combinedResult.addSequenceID(plate);
            }
        }

        return combinedResult;
    }
}