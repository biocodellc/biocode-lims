package com.biomatters.plugins.moorea.assembler.annotate;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.moorea.MooreaPlugin;
import com.biomatters.plugins.moorea.MooreaUtilities;
import com.biomatters.plugins.moorea.labbench.ConnectionException;
import com.biomatters.plugins.moorea.labbench.FimsSample;
import com.biomatters.plugins.moorea.labbench.MooreaLabBenchService;
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
                new DocumentSelectionSignature(NucleotideSequenceDocument.class, 1, Integer.MAX_VALUE)
        };
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        if (!MooreaLabBenchService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(MooreaUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        return new AnnotateFimsDataOptions(documents);
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options o) throws DocumentOperationException {
        AnnotateFimsDataOptions options = (AnnotateFimsDataOptions) o;
        progressListener.setIndeterminateProgress();
        progressListener.setMessage("Accessing FIMS...");
        List<String> failBlog = new ArrayList<String>();
        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            FimsSample tissue;
            try {
                tissue = options.getTissueRecord(annotatedDocument);
            } catch (ConnectionException e) {
                throw new DocumentOperationException("Failed to connect to FIMS: " + e.getMessage(), e);
            }
            if (progressListener.isCanceled()) {
                break;
            }
            if (tissue == null) {
                failBlog.add(annotatedDocument.getName());
                continue;
            }

            for (DocumentField documentField : tissue.getFimsAttributes()) {
                annotatedDocument.setFieldValue(documentField, tissue.getFimsAttributeValue(documentField.getCode()));
            }
            StringBuilder taxonomy = new StringBuilder();
            String genus = null;
            String species = null;
            for (DocumentField documentField : tissue.getTaxonomyAttributes()) {
                Object taxon = tissue.getFimsAttributeValue(documentField.getCode());
                if (documentField.getName().equalsIgnoreCase("genus")) {
                    genus = (String)taxon;
                }
                if (documentField.getName().toLowerCase().contains("speci")) {
                    species = (String)taxon;
                    break;
                }
                if (taxon != null) {
                    taxonomy.append(taxon).append("; ");
                }
            }
            if (taxonomy.length() > 0) {
                annotatedDocument.setFieldValue(DocumentField.TAXONOMY_FIELD, taxonomy.substring(0, taxonomy.length() - 2));
            }
            Object organism = annotatedDocument.getFieldValue(DocumentField.ORGANISM_FIELD);
            if (organism != null && !((String)organism).contains(" ")) {
                //the database seems to have cases where just the Genus has been entered in the organism column eventhough the species has been entered in the taxonomy columns -> Throw that crap away
                organism = null;
                annotatedDocument.setFieldValue(DocumentField.ORGANISM_FIELD, null);
            }
            if (organism == null && genus != null && species != null) {
                annotatedDocument.setFieldValue(DocumentField.ORGANISM_FIELD, genus + " " + species);
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
