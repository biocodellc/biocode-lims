package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.OligoSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.reaction.PCROptions;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import jebl.util.ProgressListener;

import java.util.*;

public class AnnotateUtilities {
    public static final List<String> FIELDS_TO_NOT_COPY = Arrays.asList(DocumentField.AMBIGUITIES.getCode(),
                DocumentField.BIN.getCode(), DocumentField.CREATED_FIELD.getCode(), DocumentField.DESCRIPTION_FIELD.getCode(),
                DocumentField.FIRST_SEQUENCE_RESIDUES.getCode(), DocumentField.HIGH_QUALITY_PERCENT.getCode(), DocumentField.LOW_QUALITY_PERCENT.getCode(),
                DocumentField.MEDIMUM_QUALITY_PERCENT.getCode(), DocumentField.NAME_FIELD.getCode(), DocumentField.POST_TRIM_LENGTH.getCode(),
                DocumentField.SEQUENCE_LENGTH.getCode(), DocumentField.TOPOLOGY_FIELD.getCode(), DocumentField.UNREAD_FIELD.getCode(),
                PluginDocument.MODIFIED_DATE_FIELD.getCode(), "document_size", DocumentField.SEQUENCE_COUNT.getCode());;

    private AnnotateUtilities() {
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
            if (SequenceAlignmentDocument.class.isAssignableFrom(annotatedDocument.getDocumentClass())) {
                SequenceAlignmentDocument alignment = (SequenceAlignmentDocument) annotatedDocument.getDocument();
                for (AnnotatedPluginDocument doc : alignment.getReferencedDocuments()) {
                    if (doc != null) {
                        annotateDocument(fimsDataGetter, failBlog, doc);
                    } else {
                        noReferencesList.add(doc.getName());
                    }
                }
                copyMatchingFieldsToContig(annotatedDocument);
                annotatedDocument.saveDocument();
            } else {
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

    /**
     * copied from AssemblyOperationj
     *
     * @param annotatedContig
     * @throws com.biomatters.geneious.publicapi.plugin.DocumentOperationException
     *
     */
    public static void copyMatchingFieldsToContig(AnnotatedPluginDocument annotatedContig) throws DocumentOperationException {
        SequenceAlignmentDocument contig = (SequenceAlignmentDocument) annotatedContig.getDocument();
        Map<DocumentField, Object> displayableFieldsToCopy = null;
        for (int i = 0; i < contig.getNumberOfSequences(); i++) {
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

    public static void annotateDocument(FimsDataGetter fimsDataGetter, List<String> failBlog, AnnotatedPluginDocument annotatedDocument) throws DocumentOperationException {
        FimsData fimsData;
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
        annotatedDocument.setFieldValue(BiocodeUtilities.TRACE_ID_FIELD, fimsData.sequencingPlateName + "." + fimsData.well.toString());
        if (fimsData.workflow != null) {
            annotatedDocument.setFieldValue(BiocodeUtilities.WORKFLOW_NAME_FIELD, fimsData.workflow.getName());
        }
        StringBuilder taxonomy = new StringBuilder();
        String genus = null;
        String species = null;
        for (DocumentField documentField : fimsData.fimsSample.getTaxonomyAttributes()) {
            Object taxon = fimsData.fimsSample.getFimsAttributeValue(documentField.getCode());
            if (documentField.getName().equalsIgnoreCase("genus")) {
                genus = (String) taxon;
            }
            if (documentField.getName().toLowerCase().contains("speci")) {
                species = (String) taxon;
                break;
            }
            if (taxon != null) {
                taxonomy.append(taxon).append("; ");
            }
        }
        if (taxonomy.length() > 0) {
            annotatedDocument.setFieldValue(DocumentField.TAXONOMY_FIELD, taxonomy.substring(0, taxonomy.length() - 2));
        } else {
            annotatedDocument.setFieldValue(DocumentField.TAXONOMY_FIELD, null);
        }
        Object organism = annotatedDocument.getFieldValue(DocumentField.ORGANISM_FIELD);
        if (organism != null && !((String) organism).contains(" ")) {
            //the database seems to have cases where just the Genus has been entered in the organism column eventhough the species has been entered in the taxonomy columns -> Throw that crap away
            organism = null;
            annotatedDocument.setFieldValue(DocumentField.ORGANISM_FIELD, null);
        } else if (organism == null && genus != null && species != null) {
            annotatedDocument.setFieldValue(DocumentField.ORGANISM_FIELD, genus + " " + species);
        } else {
            annotatedDocument.setFieldValue(DocumentField.ORGANISM_FIELD, null);
        }

        //annotate the primers...
        AnnotatedPluginDocument.DocumentNotes notes = annotatedDocument.getDocumentNotes(true);
        DocumentNote note = notes.getNote("sequencingPrimer");
        if (note == null) {
            DocumentNoteType sequencingPrimerType = DocumentNoteUtilities.getNoteType("sequencingPrimer");
            if (sequencingPrimerType != null) {
                note = sequencingPrimerType.createDocumentNote();
            }
        }
        if (note != null && fimsData.workflow != null && fimsData.workflow.getMostRecentReaction(Reaction.Type.PCR) != null) {
            Reaction pcrReaction = fimsData.workflow.getMostRecentReaction(Reaction.Type.PCR);
            AnnotatedPluginDocument forwardPrimer = null;
            List<AnnotatedPluginDocument> value = (List<AnnotatedPluginDocument>) pcrReaction.getOptions().getValue(PCROptions.PRIMER_OPTION_ID);
            if (value.size() > 0) {
                forwardPrimer = value.get(0);
            }
            AnnotatedPluginDocument reversePrimer = null;
            value = (List<AnnotatedPluginDocument>) pcrReaction.getOptions().getValue(PCROptions.PRIMER_REVERSE_OPTION_ID);
            if (value.size() > 0) {
                reversePrimer = value.get(0);
            }

            if (forwardPrimer != null) {
                note.setFieldValue("fwd_primer_name", forwardPrimer.getName());
                OligoSequenceDocument sequence = (OligoSequenceDocument) forwardPrimer.getDocument();
                note.setFieldValue("fwd_primer_seq", sequence.getBindingSequence().toString());
            }
            if (reversePrimer != null) {
                note.setFieldValue("rev_primer_name", reversePrimer.getName());
                OligoSequenceDocument sequence = (OligoSequenceDocument) reversePrimer.getDocument();
                note.setFieldValue("rev_primer_seq", sequence.getBindingSequence().toString());
            }
            notes.setNote(note);
            notes.saveNotes();
        }

        annotatedDocument.save();
    }
}