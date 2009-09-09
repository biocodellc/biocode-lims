package com.biomatters.plugins.biocode.submission.genbank.barstool;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.assembler.verify.Pair;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.reaction.PCROptions;
import com.biomatters.plugins.biocode.labbench.reaction.PCRReaction;
import com.biomatters.plugins.biocode.labbench.reaction.PrimerOption;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Richard
 * @version $Id$
 */
public class PrimerExportTableModel extends TabDelimitedExport.ExportTableModel {

    private static final String[] COLUMN_NAMES = {"Sequence_ID", "fwd_primer_seq", "rev_primer_seq", "fwd_primer_name", "rev_primer_name"};

    private final ExportForBarstoolOptions options;

    private final Map<AnnotatedPluginDocument, Pair<Options.OptionValue, Options.OptionValue>> primersMap;

    public PrimerExportTableModel(List<AnnotatedPluginDocument> docs, ExportForBarstoolOptions exportForBarstoolOptions) throws DocumentOperationException {
        super(docs);
        options = exportForBarstoolOptions;
        BiocodeService labbench = BiocodeService.getInstance();
        FIMSConnection fimsConnection = labbench.getActiveFIMSConnection();
        LIMSConnection limsConnection = labbench.getActiveLIMSConnection();
        primersMap = new HashMap<AnnotatedPluginDocument, Pair<Options.OptionValue, Options.OptionValue>>();
        for (AnnotatedPluginDocument doc : docs) {
            Object tissueId = doc.getFieldValue(fimsConnection.getTissueSampleDocumentField());
            if (tissueId == null) {
                throw new DocumentOperationException(doc.getName() + " does not have a tissue id, make sure you have run Annotate with FIMS Data first.");
            }
            WorkflowDocument workflow = BiocodeUtilities.getMostRecentWorkflow(limsConnection, fimsConnection, tissueId);
            PCRReaction pcr = (PCRReaction) workflow.getMostRecentReaction(Reaction.Type.PCR);
            if (pcr == null) {
                throw new DocumentOperationException(doc.getName() + " doesn't have an associated PCR reaction in LIMS, primers cannot be determined.");
            }
            Options.OptionValue forwardPrimer = (Options.OptionValue) pcr.getOptions().getValue(PCROptions.PRIMER_OPTION_ID);
            Options.OptionValue reversePrimer = (Options.OptionValue) pcr.getOptions().getValue(PCROptions.PRIMER_REVERSE_OPTION_ID);
            if (forwardPrimer != null && forwardPrimer.getName().equals(PrimerOption.NO_PRIMER_VALUE.getName())) {
                forwardPrimer = null;
            }
            if (reversePrimer != null && reversePrimer.getName().equals(PrimerOption.NO_PRIMER_VALUE.getName())) {
                reversePrimer = null;
            }
            primersMap.put(doc, new Pair<Options.OptionValue, Options.OptionValue>(forwardPrimer, reversePrimer));
        }
    }

    @Override
    int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    String getColumnName(int columnIndex) {
        return COLUMN_NAMES[columnIndex];
    }

    @Override
    Object getValue(AnnotatedPluginDocument doc, int columnIndex) {
        Options.OptionValue forwardPrimer = primersMap.get(doc).getItemA();
        Options.OptionValue reversePrimer = primersMap.get(doc).getItemB();
        switch (columnIndex) {
            case 0:
                return options.getSequenceId(doc);
            case 1:
                return forwardPrimer == null ? "" : forwardPrimer.getDescription();
            case 2:
                return reversePrimer == null ? "" : reversePrimer.getDescription();
            case 3:
                return forwardPrimer == null ? "" : forwardPrimer.getName();
            case 4:
                return reversePrimer == null ? "" : reversePrimer.getName();
            default:
                return null;
        }
    }
}
