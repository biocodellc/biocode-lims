package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.reaction.ReactionOptions;

/**
 * @author Steve
 */
public class FimsData {

    FimsSample fimsSample;
    String sequencingPlateName;
    BiocodeUtilities.Well well;
    WorkflowDocument workflow;
    String extractionId;
    String extractionBarcode;
    String reactionStatus;

    public FimsData(WorkflowDocument workflowDocument, String sequencingPlateName, BiocodeUtilities.Well well) {
        this.fimsSample = workflowDocument != null ? workflowDocument.getFimsSample() : null;
        this.workflow = workflowDocument;
        this.sequencingPlateName = sequencingPlateName;
        this.well = well;
        if(workflowDocument != null) {
            Reaction extraction = workflowDocument.getMostRecentReaction(Reaction.Type.Extraction); //there is only one extraction per workflow, getMostRecent is just an easy way to get it :)
            if(extraction != null) {
                extractionId = extraction.getExtractionId();
                extractionBarcode = (String)extraction.getFieldValue("extractionBarcode");
            }
            for(Reaction r : workflowDocument.getReactions(Reaction.Type.CycleSequencing)) {
                if(r.getPlateName().equals(sequencingPlateName) && well.toString().equals(r.getLocationString())) {
                    reactionStatus = (String)r.getFieldValue(ReactionOptions.RUN_STATUS);
                }
            }
        }
    }

    public FimsData(FimsSample sample, String plateName, BiocodeUtilities.Well well) {
        this.fimsSample = sample;
        this.sequencingPlateName = plateName;
        this.well = well;
    }
}
