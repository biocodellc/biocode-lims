package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideGraphSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.OligoSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionOption;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.GeneralUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.WorkflowBuilder;
import com.biomatters.plugins.biocode.labbench.fims.MySQLFimsConnection;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingOptions;
import com.biomatters.plugins.biocode.labbench.reaction.PCROptions;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.google.common.collect.HashMultimap;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import java.util.*;
import java.util.regex.Pattern;

public class AnnotateUtilities {
    public static final List<String> FIELDS_TO_NOT_COPY = Arrays.asList(
            DocumentField.AMBIGUITIES.getCode(),
            DocumentField.BIN.getCode(),
            DocumentField.CREATED_FIELD.getCode(),
            DocumentField.DESCRIPTION_FIELD.getCode(),
            DocumentField.FIRST_SEQUENCE_RESIDUES.getCode(),
            DocumentField.HIGH_QUALITY_PERCENT.getCode(),
            DocumentField.LOW_QUALITY_PERCENT.getCode(),
            DocumentField.MEDIMUM_QUALITY_PERCENT.getCode(),
            DocumentField.NAME_FIELD.getCode(),
            DocumentField.POST_TRIM_LENGTH.getCode(),
            DocumentField.SEQUENCE_LENGTH.getCode(),
            DocumentField.TOPOLOGY_FIELD.getCode(),
            DocumentField.UNREAD_FIELD.getCode(),
            PluginDocument.MODIFIED_DATE_FIELD.getCode(),
            "document_size",
            DocumentField.SEQUENCE_COUNT.getCode()
    );

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
                copyMatchingFieldsToContig(annotatedDocument, getDocumentFieldCodesWithoutDuplicates(fieldsAnnotated));
                copyMatchingDocumentNotesToContig(annotatedDocument);
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
     * Code for Sequencing Primer note type defined in GenBank Submission plugin
     */
    private static final String PRIMER_NOTE_TYPE = "sequencingPrimer";

    /**
     * Copies matching document notes from sequences referenced by an assembly to the assembly itself.  Only copies the
     * note if all values are the same.  The only exception is the Sequencing Primer note type defined by the GenBank
     * submission plugin that gets merged so that any non-null field values are copied across if all sequences have the
     * same value.
     * <br><br>
     * <b>Note</b>: This method was originally written for the Moorea Biocode Project.  It is duplicated in the AssemblyOperation in
     * the Alignment Plugin.  Any changes to this method need to be made there too.
     *
     * @param annotatedContig The contig assembly to copy notes to from it's references
     * @throws DocumentOperationException if documents cannot be loaded or edited
     */
    private static void copyMatchingDocumentNotesToContig(AnnotatedPluginDocument annotatedContig) throws DocumentOperationException {
        SequenceAlignmentDocument contig = (SequenceAlignmentDocument)annotatedContig.getDocument();
        Map<String, DocumentNote> documentNotesToCopy = null;
        for (int i = 0; i < contig.getNumberOfSequences(); i ++) {
            if (i == contig.getContigReferenceSequenceIndex()) {
                continue;
            }
            AnnotatedPluginDocument referencedDocument = contig.getReferencedDocument(i);
            if (referencedDocument == null) {
                return; //one sequence doesn't have a reference so bail on the whole thing
            }
            if (documentNotesToCopy == null) {
                documentNotesToCopy = new LinkedHashMap<String, DocumentNote>();
                AnnotatedPluginDocument.DocumentNotes documentNotes = referencedDocument.getDocumentNotes(false);
                for (DocumentNote note : documentNotes.getAllNotes()) {
                    documentNotesToCopy.put(note.getNoteTypeCode(), note);
                }
            } else {
                for (Map.Entry<String, DocumentNote> entry : new LinkedHashSet<Map.Entry<String, DocumentNote>>(documentNotesToCopy.entrySet())) {
                    DocumentNote note = referencedDocument.getDocumentNotes(false).getNote(entry.getKey());
                    if(!notesAreEqual(note, entry.getValue())) {
                       documentNotesToCopy.remove(entry.getKey());
                    }
                }
            }
        }
        if(documentNotesToCopy == null) {
            return;  // Contig had no sequences to copy from
        }

        //noinspection StatementWithEmptyBody
        if(documentNotesToCopy.get(PRIMER_NOTE_TYPE) == null) {
            DocumentNote sequencingPrimerNote = hackToGetSequencingPrimerNoteToCopy(contig);
            if(sequencingPrimerNote != null) {
                documentNotesToCopy.put(sequencingPrimerNote.getNoteTypeCode(), sequencingPrimerNote);
            }
        } else {
            //no need to do this if the note is already being copied...
        }

        if (documentNotesToCopy.isEmpty()) return;

        AnnotatedPluginDocument.DocumentNotes contigNotes = annotatedContig.getDocumentNotes(true);
        for (Map.Entry<String, DocumentNote> noteToCopy : documentNotesToCopy.entrySet()) {
            DocumentNote existingNote = noteToCopy.getValue();
            contigNotes.setNote(existingNote);
        }
        contigNotes.saveNotes();


    }

    private static DocumentNote hackToGetSequencingPrimerNoteToCopy(SequenceAlignmentDocument contig) {
        DocumentNoteType sequencingPrimerNoteType = DocumentNoteUtilities.getNoteType(PRIMER_NOTE_TYPE);
        if(sequencingPrimerNoteType == null) {
            return null;  // GenBank Submission plugin not initialized
        }

        HashMultimap<String, Object> seenFieldValues = HashMultimap.create();
        for (AnnotatedPluginDocument refDoc : contig.getReferencedDocuments()) {
            if(refDoc == null) {
                continue;
            }

            DocumentNote noteOnRef = refDoc.getDocumentNotes(false).getNote(PRIMER_NOTE_TYPE);
            if(noteOnRef == null) {
                continue;
            }
            for (DocumentNoteField field : sequencingPrimerNoteType.getFields()) {
                Object value = noteOnRef.getFieldValue(field.getCode());
                if(value != null) {
                    seenFieldValues.put(field.getCode(), value);
                }
            }
        }
        if(seenFieldValues.isEmpty()) {
            return null;
        }

        DocumentNote newNote = sequencingPrimerNoteType.createDocumentNote();
        for (Map.Entry<String, Collection<Object>> entry : seenFieldValues.asMap().entrySet()) {
            Collection<Object> valuesForField = entry.getValue();
            if(valuesForField.size() == 1) {
                newNote.setFieldValue(entry.getKey(), valuesForField.iterator().next());
            }
        }
        return newNote;
    }

    private static boolean notesAreEqual(DocumentNote note1, DocumentNote note2) {
        if(note1 == null || note2 == null) {
            return false;
        }
        if(!note1.getNoteTypeCode().equals(note2.getNoteTypeCode())) {
            return false;
        }

        List<DocumentNoteField> fields1 = note1.getFields();

        for (DocumentNoteField fields : fields1) {
            Object value1 = note1.getFieldValue(fields.getCode());
            Object value2 = note2.getFieldValue(fields.getCode());
            if(!GeneralUtilities.safeEquals(value1, value2)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Attempts to copy across all field values that are equal in all referenced documents to the document that
     * references them.  No existing field values on the document will be overridden unless specified.
     * <br><br>
     * <b>Note</b>: This method is identical to the method with the same name inside the com.biomatters.plugins.alignment.assembly.AssemblyOperation class.
     * Until further established upon, changes that are made to either of the two methods should also be made in the other.
     *
     * @param annotatedContig The document to copy fields to from the documents it references
     * @param codesOfOverridableFields A set of {@link com.biomatters.geneious.publicapi.documents.DocumentField} codes
     *                                 for which values will be copied across regardless of if the contig already has a
     *                                 value for that field.
     * @throws DocumentOperationException if there is a problem loading or saving the document
     */
    private static void copyMatchingFieldsToContig(AnnotatedPluginDocument annotatedContig, Set<String> codesOfOverridableFields) throws DocumentOperationException {
        SequenceAlignmentDocument contig = (SequenceAlignmentDocument)annotatedContig.getDocument();
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
                displayableFieldsToCopy = new LinkedHashMap<DocumentField, Object>();
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
                for (Map.Entry<DocumentField, Object> fieldToCopy : new LinkedHashSet<Map.Entry<DocumentField, Object>>(displayableFieldsToCopy.entrySet())) {
                    Object value = referencedDocument.getFieldValue(fieldToCopy.getKey());
                    if (value == null || !value.equals(fieldToCopy.getValue())) {
                        displayableFieldsToCopy.remove(fieldToCopy.getKey());
                    }
                }
            }

        }
        if (displayableFieldsToCopy == null || displayableFieldsToCopy.isEmpty()) {
            return;
        }

        for (Map.Entry<DocumentField, Object> fieldAndValue : displayableFieldsToCopy.entrySet()) {
            DocumentField field = fieldAndValue.getKey();
            if (annotatedContig.getFieldValue(field) == null || codesOfOverridableFields.contains(field.getCode())) {
                annotatedContig.setFieldValue(field, fieldAndValue.getValue());
            }
        }

        annotatedContig.save();
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
            fieldsAnnotated.add(annotateDocumentAndReturnField(annotatedDocument, BiocodeUtilities.SEQUENCING_WELL_FIELD, fimsData.well.toString()));
            fieldsAnnotated.add(annotateDocumentAndReturnField(annotatedDocument, BiocodeUtilities.TRACE_ID_FIELD, fimsData.sequencingPlateName + "." + fimsData.well.toString()));
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
        DocumentNote primerNote = notes.getNote(PRIMER_NOTE_TYPE);
        if (primerNote == null) {
            DocumentNoteType primerNoteType = DocumentNoteUtilities.getNoteType(PRIMER_NOTE_TYPE);
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
                Object isForwardString = annotatedDocument.getFieldValue(WorkflowBuilder.IS_FORWARD_FIELD.getCode());
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

    static List<Options.OptionValue> getOptionValuesForFimsFields() {
        List<DocumentField> fields = BiocodeService.getInstance().getActiveFIMSConnection().getSearchAttributes();
        List<Options.OptionValue> values = new ArrayList<Options.OptionValue>();
        for(DocumentField field : fields) {
            values.add(new Options.OptionValue(field.getCode(), field.getName(), field.getDescription()));
        }
        return values;
    }

    static DocumentField getDocumentFieldForOptionValue(Options.OptionValue optionValue) {
        for (DocumentField candidate : BiocodeService.getInstance().getActiveFIMSConnection().getSearchAttributes()) {
            if(candidate.getCode().equals(optionValue.getName())) {
                return candidate;
            }
        }
        return null;
    }
}