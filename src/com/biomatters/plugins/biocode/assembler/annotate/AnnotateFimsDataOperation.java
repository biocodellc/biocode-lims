package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
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
        return GeneiousActionOptions.createSubmenuActionOptions(BiocodePlugin.getSuperBiocodeAction(), geneiousActionOptions);
    }

    public String getHelp() {
        return "Select one or more sequencing reads to attempt to annotate them with data from the FIMS (Field Information Management System).";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(NucleotideSequenceDocument.class, 1, Integer.MAX_VALUE)
        };
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        if (!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        return new AnnotateFimsDataOptions(documents);
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options o) throws DocumentOperationException {
        final AnnotateFimsDataOptions options = (AnnotateFimsDataOptions) o;
        FimsDataGetter fimsDataGetter = new FimsDataGetter() {
            @Override
            public AnnotateFimsDataOptions.FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException {
                try {
                    return options.getFimsData(document);
                } catch (ConnectionException e) {
                    throw new DocumentOperationException("Failed to connect to FIMS: " + e.getMessage(), e);
                }
            }
        };
        annotateFimsData(annotatedDocuments, progressListener, fimsDataGetter);
        return null;
    }

    public static void annotateFimsData(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, FimsDataGetter fimsDataGetter) throws DocumentOperationException {
        progressListener.setIndeterminateProgress();
        progressListener.setMessage("Accessing FIMS...");
        List<String> failBlog = new ArrayList<String>();
        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            AnnotateFimsDataOptions.FimsData fimsData;
            fimsData = fimsDataGetter.getFimsData(annotatedDocument);
            if (progressListener.isCanceled()) {
                break;
            }
            if (fimsData.fimsSample == null) {
                failBlog.add(annotatedDocument.getName());
                continue;
            }

            for (DocumentField documentField : fimsData.fimsSample.getFimsAttributes()) {
                annotatedDocument.setFieldValue(documentField, fimsData.fimsSample.getFimsAttributeValue(documentField.getCode()));
            }
            annotatedDocument.setFieldValue(BiocodeUtilities.SEQUENCING_PLATE_FIELD, fimsData.sequencingPlateName);
            annotatedDocument.setFieldValue(BiocodeUtilities.SEQUENCING_WELL_FIELD, fimsData.well.toString());
            StringBuilder taxonomy = new StringBuilder();
            String genus = null;
            String species = null;
            for (DocumentField documentField : fimsData.fimsSample.getTaxonomyAttributes()) {
                Object taxon = fimsData.fimsSample.getFimsAttributeValue(documentField.getCode());
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
            b.append("Tissue records could not be found for the following sequences (the wells may have been empty):<br><br>");
            for (String s : failBlog) {
                b.append(s).append("<br>");
            }
            b.append("</html>");
            throw new DocumentOperationException(b.toString());
        }
    }

    public static abstract class FimsDataGetter {
        public abstract AnnotateFimsDataOptions.FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException;
    }
}
