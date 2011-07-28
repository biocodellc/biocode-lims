package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;

/**
 * @author Steve
 * @version $Id: FimsData.java 36017 2010-08-30 19:32:39Z steve $
 */
public class FimsData {

    FimsSample fimsSample;
    String sequencingPlateName;
    BiocodeUtilities.Well well;
    WorkflowDocument workflow;
    String extractionId;
    String extractionBarcode;

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
            }
        }

    public FimsData(FimsSample sample, String plateName, BiocodeUtilities.Well well) {
        this.fimsSample = sample;
        this.sequencingPlateName = plateName;
        this.well = well;
    }
}
