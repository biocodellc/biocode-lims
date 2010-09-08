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

        public FimsData(WorkflowDocument workflowDocument, String sequencingPlateName, BiocodeUtilities.Well well) {
            this.fimsSample = workflowDocument != null ? workflowDocument.getFimsSample() : null;
            this.workflow = workflowDocument;
            this.sequencingPlateName = sequencingPlateName;
            this.well = well;
        }

    public FimsData(FimsSample sample, String plateName, BiocodeUtilities.Well well) {
        this.fimsSample = sample;
        this.sequencingPlateName = plateName;
        this.well = well;
    }
}
