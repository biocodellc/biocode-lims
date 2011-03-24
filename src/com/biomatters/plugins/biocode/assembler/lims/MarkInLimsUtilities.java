package com.biomatters.plugins.biocode.assembler.lims;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.*;
import com.biomatters.geneious.publicapi.plugin.SequenceSelection;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.implementations.SequenceExtractionUtilities;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Steve
 * @version $Id$
 */
public class MarkInLimsUtilities {
    static Map<AnnotatedPluginDocument, SequenceDocument> getDocsToMark(AnnotatedPluginDocument[] annotatedDocuments, SequenceSelection selection) throws DocumentOperationException {
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
                if(BiocodeUtilities.isAlignmentOfChromatograms(document) || BiocodeUtilities.isAlignmentOfContigConsensusSequences(document)) {
                    for (int i = 0; i < alignment.getNumberOfSequences(); i ++) {
                        sequenceCount++;
                        if (i == alignment.getContigReferenceSequenceIndex()) continue;
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
                        docsToMark.put(alignment.getReferencedDocument(i), extractedSequence);
                    }
                }
                else {
                    sequenceCount+=alignment.getNumberOfSequences();
                    docsToMark.put(document, null);
                }
            } else {
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
}
