package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import jebl.util.ProgressListener;

import java.util.*;

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
                new DocumentSelectionSignature(NucleotideSequenceDocument.class, 1, Integer.MAX_VALUE),
                new DocumentSelectionSignature(SequenceAlignmentDocument.class,1, Integer.MAX_VALUE)
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
        Set<String> noReferencesList = new LinkedHashSet<String>();
        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            if (progressListener.isCanceled()) {
                break;
            }
            if(SequenceAlignmentDocument.class.isAssignableFrom(annotatedDocument.getDocumentClass())) {
                SequenceAlignmentDocument alignment = (SequenceAlignmentDocument)annotatedDocument.getDocument();
                for(AnnotatedPluginDocument doc : alignment.getReferencedDocuments()) {
                    if(doc != null) {
                        annotateDocument(fimsDataGetter, failBlog, doc);
                    }
                    else {
                        noReferencesList.add(doc.getName());
                    }
                }
                copyMatchingFieldsToContig(annotatedDocument);
                annotatedDocument.saveDocument();
            }
            else {
                annotateDocument(fimsDataGetter, failBlog, annotatedDocument);
            }
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
        if (!noReferencesList.isEmpty()) {
            StringBuilder b = new StringBuilder("<html>");
            b.append("The following contigs could not be annotated because they did not have reference documents:<br><br>");
            for (String s : noReferencesList) {
                b.append(s).append("<br>");
            }
            b.append("</html>");
            throw new DocumentOperationException(b.toString());
        }
    }

        private static final List<String> FIELDS_TO_NOT_COPY = Arrays.asList(DocumentField.AMBIGUITIES.getCode(),
            DocumentField.BIN.getCode(), DocumentField.CREATED_FIELD.getCode(), DocumentField.DESCRIPTION_FIELD.getCode(),
            DocumentField.FIRST_SEQUENCE_RESIDUES.getCode(), DocumentField.HIGH_QUALITY_PERCENT.getCode(), DocumentField.LOW_QUALITY_PERCENT.getCode(),
            DocumentField.MEDIMUM_QUALITY_PERCENT.getCode(), DocumentField.NAME_FIELD.getCode(), DocumentField.POST_TRIM_LENGTH.getCode(),
            DocumentField.SEQUENCE_LENGTH.getCode(), DocumentField.TOPOLOGY_FIELD.getCode(), DocumentField.UNREAD_FIELD.getCode(),
            PluginDocument.MODIFIED_DATE_FIELD.getCode(), "document_size", DocumentField.SEQUENCE_COUNT.getCode());

    /**
     * copied from AssemblyOperationj
     * @param annotatedContig
     * @throws DocumentOperationException
     */
    private static void copyMatchingFieldsToContig(AnnotatedPluginDocument annotatedContig) throws DocumentOperationException {
        SequenceAlignmentDocument contig = (SequenceAlignmentDocument)annotatedContig.getDocument();
        Map<DocumentField, Object> displayableFieldsToCopy = null;
        for (int i = 0; i < contig.getNumberOfSequences(); i ++) {
            if (i == contig.getContigReferenceSequenceIndex()) {
                continue;
            }
            AnnotatedPluginDocument referencedDocument = contig.getReferencedDocument(i);
            if (referencedDocument == null) {
                return; //one sequence doesn't have a reference so bail on the whole thing
            }
            if (displayableFieldsToCopy == null) {
                displayableFieldsToCopy = new HashMap<DocumentField, Object>();
                for (DocumentField field : referencedDocument.getDisplayableFields()) {
//                    if (field.getCode().startsWith("biocode") || field.getCode().equalsIgnoreCase("tissueid")
//                            || field.getCode().equals(DocumentField.TAXONOMY_FIELD.getCode())
//                            || field.getCode().equals(DocumentField.ORGANISM_FIELD.getCode())
//                            || field.getCode().equals(DocumentField.COMMON_NAME_FIELD.getCode())) {
//                        displayableFieldsToCopy.put(field, referencedDocument.getFieldValue(field));
//                    }
                    if (!FIELDS_TO_NOT_COPY.contains(field.getCode())) {
                        displayableFieldsToCopy.put(field, referencedDocument.getFieldValue(field));
                    }
                }
            } else {
                for (Map.Entry<DocumentField, Object> fieldToCopy : new HashSet<Map.Entry<DocumentField, Object>>(displayableFieldsToCopy.entrySet())) {
                    Object value = referencedDocument.getFieldValue(fieldToCopy.getKey());
                    if (value == null || !value.equals(fieldToCopy.getValue())) {
                        displayableFieldsToCopy.remove(fieldToCopy.getKey());
                    }
                }
            }
            if (displayableFieldsToCopy.isEmpty()) break;
        }
        if (displayableFieldsToCopy == null || displayableFieldsToCopy.isEmpty()) return;

        for (Map.Entry<DocumentField, Object> fieldToCopy : displayableFieldsToCopy.entrySet()) {
            annotatedContig.setFieldValue(fieldToCopy.getKey(), fieldToCopy.getValue());
        }
        annotatedContig.save();
    }

    private static void annotateDocument(FimsDataGetter fimsDataGetter, List<String> failBlog, AnnotatedPluginDocument annotatedDocument) throws DocumentOperationException {
        AnnotateFimsDataOptions.FimsData fimsData;
        fimsData = fimsDataGetter.getFimsData(annotatedDocument);
        if (fimsData == null || fimsData.fimsSample == null) {
            failBlog.add(annotatedDocument.getName());
            return;
        }

        for (DocumentField documentField : fimsData.fimsSample.getFimsAttributes()) {
            annotatedDocument.setFieldValue(documentField, fimsData.fimsSample.getFimsAttributeValue(documentField.getCode()));
        }
        annotatedDocument.setFieldValue(BiocodeUtilities.SEQUENCING_PLATE_FIELD, fimsData.sequencingPlateName);
        annotatedDocument.setFieldValue(BiocodeUtilities.SEQUENCING_WELL_FIELD, fimsData.well.toString());
        annotatedDocument.setFieldValue(BiocodeUtilities.WORKFLOW_NAME_FIELD, fimsData.workflow.getName());
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
        else {
            annotatedDocument.setFieldValue(DocumentField.TAXONOMY_FIELD, null);
        }
        Object organism = annotatedDocument.getFieldValue(DocumentField.ORGANISM_FIELD);
        if (organism != null && !((String)organism).contains(" ")) {
            //the database seems to have cases where just the Genus has been entered in the organism column eventhough the species has been entered in the taxonomy columns -> Throw that crap away
            organism = null;
            annotatedDocument.setFieldValue(DocumentField.ORGANISM_FIELD, null);
        }
        else if (organism == null && genus != null && species != null) {
            annotatedDocument.setFieldValue(DocumentField.ORGANISM_FIELD, genus + " " + species);
        }
        else {
            annotatedDocument.setFieldValue(DocumentField.ORGANISM_FIELD, null);
        }
        annotatedDocument.save();
    }

    public static abstract class FimsDataGetter {
        public abstract AnnotateFimsDataOptions.FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException;
    }
}
