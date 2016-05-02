package com.biomatters.plugins.biocode.assembler.lims;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.*;
import com.biomatters.geneious.publicapi.plugin.SequenceSelection;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.implementations.SequenceExtractionUtilities;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;

import java.util.*;

/**
 * @author Steve
 */
public class MarkInLimsUtilities {

    /**
     * Gets a mapping of contigs -> consensus sequences from the input documents.
     *
     * <br>Possible input / output:
     * <ol>
     *     <li>Contigs  / Contig -> null (consensus needs to be generated)</li>
     *     <li>Alignments of contig consensus sequences. / referenced contig -> consensus sequence from alignment</li>
     *     <li>Alignments of consensus sequences. / referenced consensus -> consensus sequence from alignment</li>
     *     <li>Standalone consensus sequence / Parent Assembly or consensus sequence if not possible -> consensus sequence</li>
     *     <li>Standalone traces / Trace -> Trace</li>
     * </ol>
     * <strong>Note</strong>: This method respects selection within an alignment.
     *
     * @param annotatedDocuments The input documents
     * @param selection The current selection
     * @return A map from the AnnotatedPluginDocument to the consensus SequenceDocument
     * @throws DocumentOperationException
     */
    public static Map<AnnotatedPluginDocument, SequenceDocument> getDocsToMark(AnnotatedPluginDocument[] annotatedDocuments, SequenceSelection selection) throws DocumentOperationException {
        InputType inputType = InputType.determineInputType(annotatedDocuments);

        Map<AnnotatedPluginDocument, SequenceDocument> docsToMark = new HashMap<AnnotatedPluginDocument, SequenceDocument>();
        int sequenceCount = -1;
        for (AnnotatedPluginDocument document : annotatedDocuments) {
            boolean isAlignment = SequenceAlignmentDocument.class.isAssignableFrom(document.getDocumentClass());
            if (isAlignment) {
                if (!(((SequenceAlignmentDocument)document.getDocument()).getSequence(0) instanceof NucleotideSequenceDocument)) {
                    throw new DocumentOperationException("Selected alignment \"" + document.getName() + "\" is not an alignment of DNA sequences");
                }
            } else if (!NucleotideSequenceDocument.class.isAssignableFrom(document.getDocumentClass())) {
                throw new DocumentOperationException("Selected sequence \"" + document.getName() + "\" is not DNA");

            }

            if (isAlignment) {
                SequenceAlignmentDocument alignment = (SequenceAlignmentDocument)document.getDocument();
                if(inputType == InputType.CONTIGS) {
                    sequenceCount+=alignment.getNumberOfSequences();
                    docsToMark.put(document, null);
                } else {
                    for (int i = 0; i < alignment.getNumberOfSequences(); i ++) {
                        sequenceCount++;
                        if (i == alignment.getContigReferenceSequenceIndex()) continue;
                        if(alignment.getReferencedDocument(i) == null) {
                            throw new DocumentOperationException("The document "+document.getName()+" contains at least " +
                                    "one sequence that is missing a link to its original sequence, and cannot be marked " +
                                    "as passed or failed.  <br><br>Links to original sequences are typically lost by " +
                                    "making changes (for example inserting a base), and then choosing not to apply the " +
                                    "changes to the original sequences when saving the alignment or contig.");
                        }
                        SequenceDocument sequenceToExtract = alignment.getSequence(i);

                        if(!sequenceSelectionIsEmpty(selection) && selection.getSelectedSequenceCount() > 0) {
                            boolean found = false;
                            for(SequenceSelection.SelectionInterval interval : selection.getIntervals(true)) {
                                if(interval.getMinResidue() == interval.getMaxResidue()) {
                                    continue;
                                }
                                if(interval.getSequenceIndex().getSequenceIndex() == sequenceCount) {
                                    if(interval.getMinResidue() > sequenceToExtract.getCharSequence().getLeadingGapsLength() || interval.getMaxResidue() < sequenceToExtract.getCharSequence().getTrailingGapsStartIndex()) {
                                        throw new DocumentOperationException("Please select only entire sequences.  Partial sequences cannot be marked as pass or fail");
                                    }
                                    found = true;
                                    break;
                                }
                            }
                            if(!found) {
                                continue;
                            }
                        }

                        SequenceExtractionUtilities.ExtractionOptions extractionOptions = new SequenceExtractionUtilities.ExtractionOptions(0, sequenceToExtract.getSequenceLength());
                        SequenceDocument extractedSequence = SequenceExtractionUtilities.extract(sequenceToExtract, extractionOptions);
                        // referenced doc will either be a contig or a consensus sequence
                        AnnotatedPluginDocument referencedDocument = alignment.getReferencedDocument(i);
                        if(!SequenceAlignmentDocument.class.isAssignableFrom(referencedDocument.getDocumentClass())) {
                            // Was a consensus.  Try to find the real assembly if we can.
                            AnnotatedPluginDocument realAssembly = getAssemblyFromConsensus(referencedDocument);
                            if(realAssembly != null) {
                                referencedDocument = realAssembly;
                            }
                        }
                        docsToMark.put(referencedDocument, extractedSequence);
                    }
                }
            } else if(inputType == InputType.CONSENSUS_SEQS) {
                sequenceCount++;
                AnnotatedPluginDocument assembly = getAssemblyFromConsensus(document);
                if(assembly != null) {
                    docsToMark.put(assembly, ((NucleotideSequenceDocument)document.getDocument()));
                } else {
                    docsToMark.put(document, ((NucleotideSequenceDocument)document.getDocument()));
                }
            } else {
                // Standalone traces
                sequenceCount++;
                docsToMark.put(document, ((NucleotideSequenceDocument)document.getDocument()));
            }
        }
        return docsToMark;
    }

    private static boolean sequenceSelectionIsEmpty(SequenceSelection selection) {
        if(selection == null) {
            return true;
        }
        for(SequenceSelection.SelectionInterval interval : selection.getIntervals(true)) {
            if(interval.getMinResidue() != interval.getMaxResidue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * This method serializes the edits made to a sequence (or all sequences in an alinment except the reference sequence) in the following way:
     * <ul>
     * <li>for each sequence: if a referenced document exists, and BiocodeUtilities.SEQUENCING_PLATE_FIELD and BiocodeUtilities.SEQUENCING_WELL_FIELD exist on it, they are printed out separated by a comma</li>
     * <li>an opening square bracket</li>
     * </ul>
     * The following repeated for each edit record:
     * <ul>
     * <li>The type of the annotation (eg Editing History Replacement)</li>
     * <li>A colon</li>
     * <li>The original bases</li>
     * <li>The new bases (an empty string for deletion annotations)</li>
     * <li>The minimum index of the edit</li>
     * <li>The maximum index of the edit</li>
     * </ul>
     * and finally:
     * <ul>
     * <li>A closing square bracket</li>
     * </ul>
     * For example: m037_CSF,A4[Editing History Replacement:T,G,455,455,Editing History Replacement:A,T,706,706]
     * @param doc - a SequenceDocument or SequenceAlignmentDocument
     * @return the edit annotations, serialized to string as above.
     * @throws DocumentOperationException
     */
    static String getEditRecords(AnnotatedPluginDocument doc, SequenceDocument consensus) throws DocumentOperationException{
        if(SequenceAlignmentDocument.class.isAssignableFrom(doc.getDocumentClass())) {
            return getEditRecords((SequenceAlignmentDocument)doc.getDocument(), consensus);
        }
        else if(SequenceDocument.class.isAssignableFrom(doc.getDocumentClass())){
            return getEditRecords((SequenceDocument)doc.getDocument(), getPlateLocationOrName(doc));
        }
        assert false;
        return "";
    }



    private static String getEditRecords(SequenceAlignmentDocument contig, SequenceDocument consensus) throws DocumentOperationException{
        List<String> editEntries = new ArrayList<String>();
        if(consensus != null) {
            editEntries.add(getEditRecords(consensus, "consensus"));
        }
        for (int i = 0; i < contig.getSequences().size(); i++) {
            SequenceDocument doc = contig.getSequences().get(i);
            if (i == contig.getContigReferenceSequenceIndex()) {
                continue;
            }
            AnnotatedPluginDocument reference = contig.getReferencedDocument(i);
            String name;
            if(reference != null) {
                name = getPlateLocationOrName(reference);
            }
            else {
                name = doc.getName();
            }
            String value = getEditRecords(doc, name);
            if(value != null) {
                editEntries.add(value);
            }

        }
        return StringUtilities.join(",",editEntries);

    }

    private static String getPlateLocationOrName(AnnotatedPluginDocument reference) {
        String name;
        if(reference.getFieldValue(BiocodeUtilities.SEQUENCING_PLATE_FIELD) != null && reference.getFieldValue(BiocodeUtilities.SEQUENCING_WELL_FIELD) != null) {
            name = reference.getFieldValue(BiocodeUtilities.SEQUENCING_PLATE_FIELD)+","+reference.getFieldValue(BiocodeUtilities.SEQUENCING_WELL_FIELD);
        }
        else {
            name = reference.getName();
        }
        return name;
    }

    private static String getEditRecords(SequenceDocument doc, String documentId) {
        StringBuilder builder = new StringBuilder();
        builder.append(documentId);
        builder.append("[");
        List<String> editRecords = new ArrayList<String>();
        for (SequenceAnnotation annotation : doc.getSequenceAnnotations()) {
            if (annotation.getType().equals(SequenceAnnotation.TYPE_EDITING_HISTORY_DELETION) || annotation.getType().equals(SequenceAnnotation.TYPE_EDITING_HISTORY_INSERTION) || annotation.getType().equals(SequenceAnnotation.TYPE_EDITING_HISTORY_REPLACEMENT)) {
                SequenceAnnotationInterval interval = annotation.getIntervals().get(0);
                editRecords.add(annotation.getType() + ":" + annotation.getQualifierValue(SequenceAnnotationQualifier.EDITING_HISTORY_ORIGINAL_BASES)+","+getBases(doc.getCharSequence(), interval)+","+interval.getMinimumIndex()+","+interval.getMaximumIndex());
            }
        }
        String value = "";
        if (editRecords.size() > 0) {
            builder.append(StringUtilities.join(",", editRecords));
            builder.append("]");
            value = builder.toString();
        }
        return value;
    }

    private static CharSequence getBases(CharSequence sequence, SequenceAnnotationInterval interval) {
        if(interval.isBetweenBases()) {
            return "";
        }
        int minimum = interval.getMinimumIndex() - 1;
        int maximum = interval.getMaximumIndex();
        return sequence.subSequence(minimum, maximum);
    }

    /**
     * Tries to use operation records to find the original traces a consensus was made from.  Matches based on workflow
     * name and well.
     *
     * @param consensusDoc
     * @return a list of trace documents
     */
    public static AnnotatedPluginDocument getAssemblyFromConsensus(AnnotatedPluginDocument consensusDoc) throws DocumentOperationException {
        String workflow = String.valueOf(consensusDoc.getFieldValue(BiocodeUtilities.WORKFLOW_NAME_FIELD));
        String well = String.valueOf(consensusDoc.getFieldValue(BiocodeUtilities.SEQUENCING_WELL_FIELD));
        if(workflow == null || well == null) {
            return null;
        }

        URN parentRecordUrn = consensusDoc.getParentOperationRecord();
        if(parentRecordUrn == null) {
            return null;
        }
        AnnotatedPluginDocument parentRecordDoc = DocumentUtilities.getDocumentByURN(parentRecordUrn);
        if(parentRecordDoc == null) {
            return null;
        }
        OperationRecordDocument parentRecord = (OperationRecordDocument) parentRecordDoc.getDocument();
        if(!"Generate_Consensus".equals(parentRecord.getOperationId()) && !"extraction".equals(parentRecord.getOperationId())) {
            return null;  // Wasn't generated with the consensus operation
        }

        AnnotatedPluginDocument assembly = null;
        List<URN> inputUrns = parentRecord.getInputDocuments();
        for (URN inputUrn : inputUrns) {
            AnnotatedPluginDocument inputDoc = DocumentUtilities.getDocumentByURN(inputUrn);
            if(inputDoc == null) {
                continue;
            }
            String inputWorkflow = String.valueOf(inputDoc.getFieldValue(BiocodeUtilities.WORKFLOW_NAME_FIELD));
            String inputWell = String.valueOf(inputDoc.getFieldValue(BiocodeUtilities.SEQUENCING_WELL_FIELD));
            if(workflow.equals(inputWorkflow) && well.equals(inputWell)) {
                assembly = inputDoc;
            }
        }
        if(assembly == null || !(SequenceAlignmentDocument.class.isAssignableFrom(assembly.getDocumentClass()))) {
            return null;
        } else {
            SequenceAlignmentDocument contig = (SequenceAlignmentDocument) assembly.getDocument();
            if(!contig.isContig()) {
                return null;
            } else {
                return assembly;
            }
        }
    }
}
