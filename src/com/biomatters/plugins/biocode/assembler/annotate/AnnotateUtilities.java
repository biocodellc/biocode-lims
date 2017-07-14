package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideGraphSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.OligoSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionOption;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.fims.MySQLFimsConnection;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingOptions;
import com.biomatters.plugins.biocode.labbench.reaction.PCROptions;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import java.util.*;
import java.util.regex.Pattern;

public class AnnotateUtilities {

    public static final DocumentField NOTES_FIELD = new DocumentField("Assembly Notes", "", "assemblyNotes", String.class, false, false);
    public static final DocumentField PROGRESS_FIELD = new DocumentField("Progress", "", "progress", String.class, true, false);
    public static final DocumentField EDITS_FIELD = new DocumentField("# Edits", "The number of manual edits made in the assembly", "edits", Integer.class, true, false);
    public static final DocumentField REFERENCE_SEQ_FIELD = new DocumentField("Reference Sequence", "", "refSeqId", String.class, false, false);
    public static final DocumentField TRIM_PARAMS_FWD_FIELD = new DocumentField("Trim Params (fwd)", "", "trimParamsFwd", String.class, false, false);
    public static final DocumentField TRIM_PARAMS_REV_FIELD = new DocumentField("Trim Params (rev)", "", "trimParamsRev", String.class, false, false);
    public static final DocumentField TECHNICIAN_FIELD =  new DocumentField("Technician", "", "technician", String.class, true, false);
    public static final DocumentField AMBIGUITIES_FIELD = new DocumentField("# Ambiguities", "", "ambiguities", Integer.class, true, false);
    public static final DocumentField LIMS_ID = new DocumentField("id", "", "lims_id", Integer.class, false, false);
    public static final DocumentField ASSEMBLY_PARAMS_FIELD = new DocumentField("Assembly Parameters", "", "assemblyParams", String.class, false, false);

    private static final String FORWARD_PCR_PRIMER_NAME_DOCUMENT_NOTE_FIELD_CODE            = "fwd_primer_name";
    private static final String FORWARD_PCR_PRIMER_SEQUENCE_DOCUMENT_NOTE_FIELD_CODE        = "fwd_primer_seq";
    private static final String REVERSE_PCR_PRIMER_NAME_DOCUMENT_NOTE_FIELD_CODE            = "rev_primer_name";
    private static final String REVERSE_PCR_PRIMER_SEQUENCE_DOCUMENT_NOTE_FIELD_CODE        = "rev_primer_seq";
    private static final String FORWARD_SEQUENCING_PRIMER_NAME_DOCUMENT_NOTE_FIELD_CODE     = "seq_fwd_primer_name";
    private static final String FORWARD_SEQUENCING_PRIMER_SEQUENCE_DOCUMENT_NOTE_FIELD_CODE = "seq_fwd_primer_seq";
    private static final String REVERSE_SEQUENCING_PRIMER_NAME_DOCUMENT_NOTE_FIELD_CODE     = "seq_rev_primer_name";
    private static final String REVERSE_SEQUENCING_PRIMER_SEQUENCE_DOCUMENT_NOTE_FIELD_CODE = "seq_rev_primer_seq";

    private AnnotateUtilities() {
    }

    /**
     * Annotates documents using a supplied {@link FimsDataGetter}.  In the case of a MySQL FIMS will also offer to
     * remove old FIMS fields.
     *
     * @param annotatedDocuments The documents to annotate
     * @param progressListener To report progress to
     * @param fimsDataGetter Used to retrieve FIMS data
     * @param askAboutOldFields True if we should ask the user if they want to remove old FIMS fields.  False to leave old fields
     *
     * @throws DocumentOperationException if we are unable to annotate for some reason
     */
    public static void annotateFimsData(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, FimsDataGetter fimsDataGetter, boolean askAboutOldFields) throws DocumentOperationException {
        Set<DocumentField> oldFields = new HashSet<DocumentField>();
        Set<DocumentField> newFields = new HashSet<DocumentField>();
        Set<AnnotatedPluginDocument> docsAnnotated = new HashSet<AnnotatedPluginDocument>();

        CompositeProgressListener realProgress = new CompositeProgressListener(progressListener, annotatedDocuments.length);
        realProgress.setMessage("Accessing FIMS...");
        List<String> failBlog = new ArrayList<String>();
        Set<String> noReferencesList = new LinkedHashSet<String>();
        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            if (realProgress.isCanceled() || !BiocodeService.getInstance().isLoggedIn()) {
                break;
            }
            realProgress.beginSubtask("Annotating " + annotatedDocument.getName());
            if (SequenceAlignmentDocument.class.isAssignableFrom(annotatedDocument.getDocumentClass())) {
                // Older versions of the Biocode plugin would incorrectly copy this field across to the alignment doc.
                // Wipe out the value that has been set and let Geneious calculate it normally.
                annotatedDocument.setFieldValue(DocumentField.NUCLEOTIDE_SEQUENCES_WITH_QUALITY_COUNT, null);

                Set<DocumentField> fieldsAnnotated = new HashSet<DocumentField>();
                SequenceAlignmentDocument alignment = (SequenceAlignmentDocument) annotatedDocument.getDocument();
                CompositeProgressListener progressForAlignment = new CompositeProgressListener(realProgress, alignment.getNumberOfSequences());
                for (int i = 0; i < alignment.getNumberOfSequences(); i ++)  {
                    progressForAlignment.beginSubtask();
                    AnnotatedPluginDocument referencedDocument = alignment.getReferencedDocument(i);
                    if (referencedDocument != null) {
                        docsAnnotated.add(referencedDocument);
                        fieldsAnnotated = annotateDocument(fimsDataGetter, failBlog, referencedDocument, true);
                    } else {
                        noReferencesList.add(alignment.getSequence(i).getName());
                    }
                }
                List<AnnotatedPluginDocument> documentsContainingReads = ContigNotesAndFieldCopying.getDocumentsContainingReads(annotatedDocument);
                ContigNotesAndFieldCopying.copyMatchingFieldsToContig(annotatedDocument, documentsContainingReads, getDocumentFieldCodesWithoutDuplicates(fieldsAnnotated));
                ContigNotesAndFieldCopying.copyMatchingDocumentNotesToContig(annotatedDocument, documentsContainingReads);
                newFields.addAll(fieldsAnnotated);
            } else {
                newFields.addAll(annotateDocument(fimsDataGetter, failBlog, annotatedDocument, true));
            }
            docsAnnotated.add(annotatedDocument);
        }

        for (AnnotatedPluginDocument annotatedDocument : docsAnnotated) {
            for (DocumentField field : annotatedDocument.getDisplayableFields()) {
                if(field.getCode().startsWith(MySQLFimsConnection.FIELD_PREFIX) || Pattern.matches("\\d+", field.getCode())) {
                    if(annotatedDocument.getFieldValue(field) != null) {
                        oldFields.add(field);
                    }
                }
            }
        }

        oldFields.removeAll(newFields);
        if(askAboutOldFields && !newFields.isEmpty() && !oldFields.isEmpty()) {
            Set<String> newNames = new HashSet<String>();
            for (DocumentField newField : newFields) {
                newNames.add(newField.getName());
            }
            Set<String> duplicateNames = new HashSet<String>();
            for (DocumentField oldField : oldFields) {
                if(newNames.contains(oldField.getName())) {
                    duplicateNames.add(oldField.getName());
                }
            }

            String remove = "Remove Fields";
            Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(new String[]{remove, "Keep Fields"},
                    "Old FIMS Fields Detected", null, Dialogs.DialogIcon.QUESTION);
            StringBuilder message = new StringBuilder("<html>Geneious has detected <strong>" + oldFields.size() +
                    "</strong> previously annotated FIMS fields that are not in your current FIMS. ");
            if(!duplicateNames.isEmpty()) {
                message.append("<strong>").append(duplicateNames.size()).append(
                        "</strong> of these have duplicate names with current FIMS fields. ");
            }

            message.append("Do you want to remove these fields?\n\n<strong>Fields</strong>:\n");
            for (DocumentField oldField : oldFields) {
                message.append(oldField.getName());
                if(duplicateNames.contains(oldField.getName())) {
                    message.append(" (duplicate)");
                }
                message.append("\n");
            }
            message.append("</html>");
            Object choice = Dialogs.showDialog(dialogOptions, message.toString());
            if(remove.equals(choice)) {
                for (AnnotatedPluginDocument annotatedDocument : docsAnnotated) {
                    boolean savePluginDocToo = false;
                    for (DocumentField oldField : oldFields) {
                        // The API doesn't let us remove the field from the document, but we can set the value to null.
                        annotatedDocument.setFieldValue(oldField, null);

                        if(annotatedDocument.getFieldValue(oldField) != null) {
                            // Due to a problem elsewhere, when generating a consensus, Geneious copies APD fields into
                            // the new plugin document.  And due to a limitation/bug in core you can't wipe out
                            // PluginDocument fields by using the APD.  So we have to load the PluginDocument and clear the
                            // field on it.
                            PluginDocument pluginDoc = annotatedDocument.getDocumentOrNull();
                            if(pluginDoc instanceof AbstractPluginDocument) {
                                savePluginDocToo = true;
                                ((AbstractPluginDocument) pluginDoc).setFieldValue(oldField, null);
                            } else {
                                // We can't load the doc or it isn't of a type we can edit
                            }
                        }
                    }
                    if(savePluginDocToo) {
                        annotatedDocument.saveDocument();
                    } else {
                        annotatedDocument.save();  // Means we end up saving the same doc twice.  However this should be an infrequent operation.
                    }
                }
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
     * Returns the codes of the supplied fields (without duplicates).
     *
     * @param fields Fields that returned codes are of.
     * @return Codes of the supplied fields (without duplicates).
     */
    private static Set<String> getDocumentFieldCodesWithoutDuplicates(Set<DocumentField> fields) {
        Set<String> uniqueCodesFromFields = new HashSet<String>();

        for (DocumentField field : fields)
            uniqueCodesFromFields.add(field.getCode());

        return uniqueCodesFromFields;
    }


    /**
     * Annotates a document with data from a {@link FimsDataGetter}
     *
     * @param fimsDataGetter Used to get the FIMS fields and values
     * @param failBlog To add failure messages to, for example when there are no FIMs fields associated with the document
     * @param annotatedDocument The document to annotate
     * @param updateModifiedDate true to update the modified date when saving.  False to leave it as is.
     * @return All FIMS/LIMS fields {@link DocumentField}s that were annotated onto the document
     * @throws DocumentOperationException
     */
    public static Set<DocumentField> annotateDocument(FimsDataGetter fimsDataGetter, List<String> failBlog, AnnotatedPluginDocument annotatedDocument, boolean updateModifiedDate) throws DocumentOperationException {
        FimsData fimsData = fimsDataGetter.getFimsData(annotatedDocument);

        if (fimsData == null || fimsData.fimsSample == null) {
            failBlog.add(annotatedDocument.getName());

            return Collections.emptySet();
        }

        HashSet<DocumentField> fieldsAnnotated = new HashSet<DocumentField>();

        for (DocumentField field : fimsData.fimsSample.getFimsAttributes()) {
            fieldsAnnotated.add(annotateDocumentAndReturnField(annotatedDocument, field, fimsData.fimsSample.getFimsAttributeValue(field.getCode())));
        }

        if (fimsData.sequencingPlateName != null) {
            fieldsAnnotated.add(annotateDocumentAndReturnField(annotatedDocument, BiocodeUtilities.SEQUENCING_PLATE_FIELD, fimsData.sequencingPlateName));
        }

        if (fimsData.reactionStatus != null) {
            fieldsAnnotated.add(annotateDocumentAndReturnField(annotatedDocument, BiocodeUtilities.REACTION_STATUS_FIELD, fimsData.reactionStatus));
        }

        if (fimsData.sequencingPlateName != null && fimsData.well != null) {
            if (!containsDocumentFieldWithCode(fieldsAnnotated, BiocodeUtilities.SEQUENCING_WELL_FIELD.getCode())) {
                fieldsAnnotated.add(annotateDocumentAndReturnField(annotatedDocument, BiocodeUtilities.SEQUENCING_WELL_FIELD, fimsData.well.toString()));
            }
            if (!containsDocumentFieldWithCode(fieldsAnnotated, BiocodeUtilities.TRACE_ID_FIELD.getCode())) {
                fieldsAnnotated.add(annotateDocumentAndReturnField(annotatedDocument, BiocodeUtilities.TRACE_ID_FIELD, fimsData.sequencingPlateName + "." + fimsData.well.toString()));
            }
        }

        if (fimsData.workflow != null) {
            fieldsAnnotated.add(annotateDocumentAndReturnField(annotatedDocument, BiocodeUtilities.WORKFLOW_NAME_FIELD, fimsData.workflow.getName()));
        }

        if (fimsData.extractionId != null) {
            fieldsAnnotated.add(annotateDocumentAndReturnField(annotatedDocument, LIMSConnection.EXTRACTION_ID_FIELD, fimsData.extractionId));
        }

        if (fimsData.extractionBarcode != null && !fimsData.extractionBarcode.isEmpty()) {
            fieldsAnnotated.add(annotateDocumentAndReturnField(annotatedDocument, BiocodeUtilities.EXTRACTION_BARCODE_FIELD, fimsData.extractionBarcode));
        }

        String TAXONOMY_FIELD_INTRA_SEPARATOR = "; ";
        String ORGANISM_FIELD_INTRA_SEPARATOR = " ";
        StringBuilder taxonomyFieldValuesBuilder = new StringBuilder();
        StringBuilder organismBuilder = new StringBuilder();

        for (DocumentField documentField : fimsData.fimsSample.getTaxonomyAttributes()) {
            String documentFieldName = documentField.getName();

            Object taxon = fimsData.fimsSample.getFimsAttributeValue(documentField.getCode());

            if (!(taxon instanceof String)) {
                continue;
            }

            String taxonAsString = String.valueOf(taxon);

            if (taxonAsString.isEmpty()) {
                continue;
            }

            fieldsAnnotated.add(annotateDocumentAndReturnField(
                    annotatedDocument,
                    new DocumentField(documentFieldName, documentField.getDescription(), documentField.getCode(), documentField.getValueType(), false, false),
                    fimsData.fimsSample.getFimsAttributeValue(documentField.getCode()))
            );

            if (organismBuilder.length() == 0) {
                if (documentFieldName.equalsIgnoreCase("genus")) {
                    organismBuilder.append(taxonAsString);
                }

                if (taxonomyFieldValuesBuilder.length() != 0) {
                    taxonomyFieldValuesBuilder.append(TAXONOMY_FIELD_INTRA_SEPARATOR);
                }

                taxonomyFieldValuesBuilder.append(taxonAsString);
            } else {
                organismBuilder.append(ORGANISM_FIELD_INTRA_SEPARATOR).append(taxonAsString);
            }
        }

        String taxonomy = taxonomyFieldValuesBuilder.length() == 0 ? null : taxonomyFieldValuesBuilder.toString();
        fieldsAnnotated.add(annotateDocumentAndReturnField(annotatedDocument, DocumentField.TAXONOMY_FIELD, taxonomy));

        String organism = organismBuilder.length() == 0 ? null : organismBuilder.toString();
        fieldsAnnotated.add(annotateDocumentAndReturnField(annotatedDocument, DocumentField.ORGANISM_FIELD, organism));

        //annotate the primers...
        AnnotatedPluginDocument.DocumentNotes notes = annotatedDocument.getDocumentNotes(true);
        DocumentNote primerNote = notes.getNote(ContigNotesAndFieldCopying.SEQ_PRIMER_NOTE_TYPE);
        if (primerNote == null) {
            DocumentNoteType primerNoteType = DocumentNoteUtilities.getNoteType(ContigNotesAndFieldCopying.SEQ_PRIMER_NOTE_TYPE);
            if (primerNoteType != null) {
                primerNote = primerNoteType.createDocumentNote();
            }
        }

        boolean savedDocument = false;
        if (primerNote != null && fimsData.workflow != null) {
            Reaction pcrReaction = fimsData.workflow.getMostRecentReaction(Reaction.Type.PCR);
            Reaction forwardSequencingReaction = fimsData.workflow.getMostRecentSequencingReaction(true);
            Reaction reverseSequencingReaction = fimsData.workflow.getMostRecentSequencingReaction(false);
            Boolean directionForTrace = getDirectionForTrace(annotatedDocument);

            AnnotatedPluginDocument forwardPCRPrimer = null;
            AnnotatedPluginDocument reversePCRPrimer = null;
            AnnotatedPluginDocument forwardSequencingPrimer = null;
            AnnotatedPluginDocument reverseSequencingPrimer = null;

            if (pcrReaction != null) {
                forwardPCRPrimer = getPrimer(pcrReaction, PCROptions.PRIMER_OPTION_ID);
                reversePCRPrimer = getPrimer(pcrReaction, PCROptions.PRIMER_REVERSE_OPTION_ID);
            }

            if (hasAllSequencingPrimerNoteFields(primerNote)) {
                if (forwardSequencingReaction != null) {
                    forwardSequencingPrimer = getPrimer(forwardSequencingReaction, CycleSequencingOptions.PRIMER_OPTION_ID);
                }

                if (reverseSequencingReaction != null) {
                    reverseSequencingPrimer = getPrimer(reverseSequencingReaction, CycleSequencingOptions.PRIMER_OPTION_ID);
                }
            }

            if (forwardPCRPrimer != null) {
                primerNote.setFieldValue(FORWARD_PCR_PRIMER_NAME_DOCUMENT_NOTE_FIELD_CODE, forwardPCRPrimer.getName());
                primerNote.setFieldValue(FORWARD_PCR_PRIMER_SEQUENCE_DOCUMENT_NOTE_FIELD_CODE, ((OligoSequenceDocument) forwardPCRPrimer.getDocument()).getBindingSequence().toString());
            }

            if (reversePCRPrimer != null) {
                primerNote.setFieldValue(REVERSE_PCR_PRIMER_NAME_DOCUMENT_NOTE_FIELD_CODE, reversePCRPrimer.getName());
                primerNote.setFieldValue(REVERSE_PCR_PRIMER_SEQUENCE_DOCUMENT_NOTE_FIELD_CODE, ((OligoSequenceDocument)reversePCRPrimer.getDocument()).getBindingSequence().toString());
            }

            if (forwardSequencingPrimer != null && (directionForTrace == null || directionForTrace)) {
                primerNote.setFieldValue(FORWARD_SEQUENCING_PRIMER_NAME_DOCUMENT_NOTE_FIELD_CODE, forwardSequencingPrimer.getName());
                primerNote.setFieldValue(FORWARD_SEQUENCING_PRIMER_SEQUENCE_DOCUMENT_NOTE_FIELD_CODE, ((OligoSequenceDocument)forwardSequencingPrimer.getDocument()).getBindingSequence().toString());
            }

            if (reverseSequencingPrimer != null && (directionForTrace == null || !directionForTrace)) {
                primerNote.setFieldValue(REVERSE_SEQUENCING_PRIMER_NAME_DOCUMENT_NOTE_FIELD_CODE, reverseSequencingPrimer.getName());
                primerNote.setFieldValue(REVERSE_SEQUENCING_PRIMER_SEQUENCE_DOCUMENT_NOTE_FIELD_CODE, ((OligoSequenceDocument)reverseSequencingPrimer.getDocument()).getBindingSequence().toString());
            }

            notes.setNote(primerNote);
            notes.saveNotes();
            savedDocument = true;
        }

        if (!savedDocument) {
            annotatedDocument.save(updateModifiedDate);
        }

        return fieldsAnnotated;
    }

    private static boolean containsDocumentFieldWithCode(Collection<DocumentField> fields, String code) {
        for (DocumentField field : fields) {
            if (field.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAllSequencingPrimerNoteFields(DocumentNote note) {
        return hasDocumentNoteField(note, FORWARD_SEQUENCING_PRIMER_NAME_DOCUMENT_NOTE_FIELD_CODE)
                && hasDocumentNoteField(note, FORWARD_SEQUENCING_PRIMER_SEQUENCE_DOCUMENT_NOTE_FIELD_CODE)
                && hasDocumentNoteField(note, REVERSE_SEQUENCING_PRIMER_NAME_DOCUMENT_NOTE_FIELD_CODE)
                && hasDocumentNoteField(note, REVERSE_SEQUENCING_PRIMER_SEQUENCE_DOCUMENT_NOTE_FIELD_CODE);
    }

    private static boolean hasDocumentNoteField(DocumentNote note, String code) {
        for (DocumentNoteField documentNoteField : note.getFields()) {
            if (documentNoteField.getCode().equals(code)) {
                return true;
            }
        }

        return false;
    }

    private static DocumentField annotateDocumentAndReturnField(AnnotatedPluginDocument document, DocumentField field, Object value) {
        document.setFieldValue(field, value);
        return field;
    }

    /**
     *
     * @param annotatedDocument The document to get the direction for
     * @return The direction of a trace or null if the annotatedDocument is not a trace or has not had the direction set.
     * @throws DocumentOperationException if there is a problem loading the document
     */
    static Boolean getDirectionForTrace(AnnotatedPluginDocument annotatedDocument) throws DocumentOperationException {
        Boolean directionForTrace = null;
        if(NucleotideGraphSequenceDocument.class.isAssignableFrom(annotatedDocument.getDocumentClass())) {
            NucleotideGraphSequenceDocument graphSeq = (NucleotideGraphSequenceDocument) annotatedDocument.getDocument();
            if(graphSeq.getChromatogramLength() > 0) {
                Object isForwardString = annotatedDocument.getFieldValue(BiocodeUtilities.IS_FORWARD_FIELD.getCode());
                directionForTrace = isForwardString == null ? null : Boolean.valueOf(isForwardString.toString());
            }
        }
        return directionForTrace;
    }

    private static AnnotatedPluginDocument getPrimer(Reaction pcrOrSequencingReaction, String optionKey) {
        AnnotatedPluginDocument forwardPrimer = null;
        DocumentSelectionOption option = (DocumentSelectionOption)pcrOrSequencingReaction.getOptions().getOption(optionKey);
        List<AnnotatedPluginDocument> value = option.getDocuments();
        if (value.size() > 0) {
            forwardPrimer = value.get(0);
        }
        return forwardPrimer;
    }

    public static void setSequencingPrimerNote(AnnotatedPluginDocument document,
                                               String forwardPrimerName,
                                               String forwardPrimerSequence,
                                               String reversePrimerName,
                                               String reversePrimerSequence) {
        String code = "sequencingPrimer";
        AnnotatedPluginDocument.DocumentNotes notes = document.getDocumentNotes(true);
        DocumentNote note = notes.getNote(code);

        if (note == null) {
            DocumentNoteType type = DocumentNoteUtilities.getNoteType(code);
            if (type != null) {
                note = type.createDocumentNote();
            }
        }

        if (note != null) {
            if (!isNullOrEmpty(forwardPrimerName)) {
                note.setFieldValue("fwd_primer_name", forwardPrimerName);
            }
            if (!isNullOrEmpty(forwardPrimerSequence)) {
                note.setFieldValue("fwd_primer_seq", forwardPrimerSequence);
            }
            if (!isNullOrEmpty(reversePrimerName)) {
                note.setFieldValue("rev_primer_name", reversePrimerName);
            }
            if (!isNullOrEmpty(reversePrimerSequence)){
                note.setFieldValue("rev_primer_seq", reversePrimerSequence);
            }

            notes.setNote(note);

            notes.saveNotes();
        }
    }

    private static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }
}