package com.biomatters.plugins.moorea.assembler.annotate;

import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.moorea.MooreaPlugin;
import com.biomatters.plugins.moorea.labbench.ConnectionException;
import com.biomatters.plugins.moorea.labbench.FimsSample;
import com.biomatters.plugins.moorea.labbench.MooreaLabBenchService;
import com.biomatters.plugins.moorea.labbench.fims.FIMSConnection;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class AnnotateFimsDataOperation extends DocumentOperation {

    public GeneiousActionOptions getActionOptions() {
        GeneiousActionOptions geneiousActionOptions = new GeneiousActionOptions("Annotate with FIMS Data...",
                "Annotate sequences/assemblies with data from the Field Information Management System. eg. Taxonomy, Collector")
                .setInPopupMenu(true, 0.2);
        return GeneiousActionOptions.createSubmenuActionOptions(MooreaPlugin.getSuperBiocodeAction(), geneiousActionOptions);
    }

    public String getHelp() {
        return "Select one or more sequences or assemblies to attempt to annotate them with data from the FIMS (Field Information Management System).";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(PluginDocument.class, 1, Integer.MAX_VALUE)
        };
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        if (MooreaLabBenchService.getInstance().getActiveFIMSConnection() == null) {
            throw new DocumentOperationException("You must connect to the lab bench service first");
        }
        return new AnnotateFimsDataOptions();
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options o) throws DocumentOperationException {
        AnnotateFimsDataOptions options = (AnnotateFimsDataOptions) o;
        MooreaLabBenchService mooreaLabBenchService = MooreaLabBenchService.getInstance();
        FIMSConnection activeFIMSConnection = mooreaLabBenchService.getActiveFIMSConnection();
        if (activeFIMSConnection == null) {
            throw new DocumentOperationException("You must connect to the lab bench service first");
        }
        CompositeProgressListener compositeProgress = new CompositeProgressListener(progressListener, annotatedDocuments.length);
        if (compositeProgress.getRootProgressListener() instanceof ProgressFrame) {
            ((ProgressFrame)compositeProgress.getRootProgressListener()).setCancelButtonLabel("Stop");
        }
        List<String> failBlog = new ArrayList<String>();
        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            compositeProgress.beginSubtask();
            if (compositeProgress.isCanceled()) {
                break;
            }

            FimsSample tissue;
            try {
                tissue = options.getTissueRecord(annotatedDocument, activeFIMSConnection);
            } catch (ConnectionException e) {
                throw new DocumentOperationException("Failed to connect to FIMS", e);
            }
            if (tissue == null) {
                failBlog.add(annotatedDocument.getName());
                continue;
            }

            for (DocumentField documentField : tissue.getFimsAttributes()) {
                annotatedDocument.setFieldValue(documentField, tissue.getFimsAttributeValue(documentField.getCode()));
            }
            StringBuilder taxonomy = new StringBuilder();
            for (DocumentField documentField : tissue.getTaxonomyAttributes()) {
                Object taxon = tissue.getFimsAttributeValue(documentField.getCode());
                if (taxon != null) {
                    taxonomy.append(taxon.toString()).append("; ");
                }
            }
            if (taxonomy.length() > 0) {
                annotatedDocument.setFieldValue(DocumentField.TAXONOMY_FIELD, taxonomy.substring(0, taxonomy.length() - 2));
            }
            annotatedDocument.save();
        }
        if (!failBlog.isEmpty()) {
            StringBuilder b = new StringBuilder("<html>");
            b.append("Tissue records could not be found for the following sequences:<br><br>");
            for (String s : failBlog) {
                b.append(s).append("<br>");
            }
            b.append("</html>");
            throw new DocumentOperationException(b.toString());
        }
        return null;
    }
}
