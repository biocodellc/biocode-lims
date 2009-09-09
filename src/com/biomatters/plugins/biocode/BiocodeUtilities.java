package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideGraphSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperation;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.PluginUtilities;
import com.biomatters.plugins.biocode.assembler.SetReadDirectionOperation;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import jebl.util.ProgressListener;

import java.sql.SQLException;
import java.util.*;

/**
 * @author Richard
 * @version $Id$
 */
public class BiocodeUtilities {

    public static final String NOT_CONNECTED_ERROR_MESSAGE = "<html><b>You must connect to the lab bench service first.</b><br><br>" +
            "To connect, right-click on the Biocode service in the Sources panel to the left.";

    public static final DocumentField SEQUENCING_PLATE_FIELD = DocumentField.createStringField("Sequencing Plate", "Name of the cycle sequencing plate in the LIMS that this read was created from", "sequencingPlateName", false, false);
    public static final DocumentField SEQUENCING_WELL_FIELD = DocumentField.createStringField("Sequencing Well", "Well location on the cycle sequencing plate in the LIMS that this read was created from", "sequencingPlateWell", false, false);

    public static Options getConsensusOptions(AnnotatedPluginDocument[] selectedDocuments) throws DocumentOperationException {
        DocumentOperation consensusOperation = PluginUtilities.getDocumentOperation("Generate_Consensus");
        if (consensusOperation == null) {
            return null;
        }
        return consensusOperation.getOptions(selectedDocuments);
    }

    /**
     *
     * @return generated consensus if doc is an alignment, doc if doc is a sequence, null if doc is a sequence with trace information (obviously isn't a consensus sequence)
     */
    public static AnnotatedPluginDocument getConsensusSequence(AnnotatedPluginDocument doc, Options consensusOptions) throws DocumentOperationException {
        if (SequenceAlignmentDocument.class.isAssignableFrom(doc.getDocumentClass())) {
            DocumentOperation consensusOperation = PluginUtilities.getDocumentOperation("Generate_Consensus");
            return consensusOperation.performOperation(new AnnotatedPluginDocument[]{doc}, ProgressListener.EMPTY, consensusOptions).get(0);
        } else { //SequenceDocument
            SequenceDocument sequence = (SequenceDocument) doc.getDocument();
            if (sequence instanceof NucleotideGraphSequenceDocument) {
                NucleotideGraphSequenceDocument nucleotideGraph = (NucleotideGraphSequenceDocument) sequence;
                if (nucleotideGraph.hasChromatogramValues(0) || nucleotideGraph.hasChromatogramValues(1) ||
                        nucleotideGraph.hasChromatogramValues(2) || nucleotideGraph.hasChromatogramValues(3)) {
                    return null; //the sequence can't be a consensus sequence, return null because there is no consensus sequence to add to the database
                }
            }
            return doc;
        }
    }

    public static WorkflowDocument getMostRecentWorkflow(LIMSConnection limsConnection, FIMSConnection fimsConnection, Object tissueId) throws DocumentOperationException {
        WorkflowDocument mostRecent = null;
        try {
            Query fimsQuery = Query.Factory.createFieldQuery(fimsConnection.getTissueSampleDocumentField(), Condition.CONTAINS, tissueId);
            List<FimsSample> tissues = fimsConnection.getMatchingSamples(fimsQuery);
            if (tissues.size() != 1) {
                return null;
            }

            List<WorkflowDocument> workflows = limsConnection.getMatchingWorkflowDocuments(null, tissues);
            for (WorkflowDocument workflow : workflows) {
                if (mostRecent == null || workflow.getCreationDate().after(mostRecent.getCreationDate())) {
                    mostRecent = workflow;
                }
            }
        } catch (SQLException e) {
            throw new DocumentOperationException("Failed to connect to LIMS: " + e.getMessage(), e);
        } catch (ConnectionException e) {
            throw new DocumentOperationException("Failed to connect to FIMS: " + e.getMessage(), e);
        }
        return mostRecent;
    }

    /**
     *
     * @param docs any mixture of contigs and alignments referencing contigs
     * @return all contigs in the selected documents (referenced or otherwise) mapped to the consensus sequence that should be used for each or null if a consensus should be generated
     * @throws DocumentOperationException
     */
    public static Map<AnnotatedPluginDocument, String> getContigDocuments(AnnotatedPluginDocument[] docs) throws DocumentOperationException {
        Map<AnnotatedPluginDocument, String> contigDocumentsMap = new HashMap<AnnotatedPluginDocument, String>();
        for (AnnotatedPluginDocument document : docs) {
            if (!(((SequenceAlignmentDocument)document.getDocument()).getSequence(0) instanceof NucleotideSequenceDocument)) {
                throw new DocumentOperationException("Selected alignment \"" + document.getName() + "\" is not an alignment of DNA sequences");
            }
            if (isAlignmentOfContigs(document)) {
                SequenceAlignmentDocument alignment = (SequenceAlignmentDocument)document.getDocument();
                for (int i = 0; i < alignment.getNumberOfSequences(); i ++) {
                    if (i == alignment.getContigReferenceSequenceIndex()) continue;
                    contigDocumentsMap.put(alignment.getReferencedDocument(i), alignment.getSequence(i).getSequenceString().replace("-", ""));
                }
            } else {
                contigDocumentsMap.put(document, null);
            }
        }
        return contigDocumentsMap;
    }

    public enum ReadDirection {
        NONE("N"), FORWARD("F"), REVERSE("R");

        private final String barstoolString;

        ReadDirection(String barstoolString) {
            this.barstoolString = barstoolString;
        }

        public String getBarstoolString() {
            return barstoolString;
        }
    }

    public static ReadDirection getNoReadDirectionValue(Collection<AnnotatedPluginDocument> documents) throws DocumentOperationException {
        ReadDirection noReadDirectionValue = ReadDirection.NONE;
        for (AnnotatedPluginDocument document : documents) {
            List<AnnotatedPluginDocument> traceDocs;
            if (SequenceAlignmentDocument.class.isAssignableFrom(document.getDocumentClass())) {
                traceDocs = new ArrayList<AnnotatedPluginDocument>();
                SequenceAlignmentDocument contig = (SequenceAlignmentDocument) document.getDocument();
                for (int i = 0; i < contig.getNumberOfSequences(); i ++) {
                    if (i == contig.getContigReferenceSequenceIndex()) continue;
                    AnnotatedPluginDocument traceDoc = contig.getReferencedDocument(i);
                    if (traceDoc == null) {
                        throw new DocumentOperationException("The contig " + document.getName() + " is missing a reference for sequence " + contig.getSequence(i).getName() + ".");
                    }
                    traceDocs.add(traceDoc);
                }
            } else {
                traceDocs = Collections.singletonList(document);
            }
            boolean bothFound = false;
            for (AnnotatedPluginDocument traceDoc : traceDocs) {
                Object isForwardValue = traceDoc.getFieldValue(SetReadDirectionOperation.IS_FORWARD_FIELD);
                if (isForwardValue == null) {
                    continue;
                }
                if ((Boolean)isForwardValue) {
                    if (noReadDirectionValue == ReadDirection.FORWARD) {
                        noReadDirectionValue = ReadDirection.NONE;
                        bothFound = true;
                        break;
                    } else {
                        noReadDirectionValue = ReadDirection.REVERSE;
                    }
                } else {
                    if (noReadDirectionValue == ReadDirection.REVERSE) {
                        noReadDirectionValue = ReadDirection.NONE;
                        bothFound = true;
                        break;
                    } else {
                        noReadDirectionValue = ReadDirection.FORWARD;
                    }
                }
            }
            if (bothFound) {
                break;
            }
        }
        return noReadDirectionValue;
    }

    public static final class Well {
        public final char letter;
        public final int number;

        public Well(char letter, int number) {
            this.letter = letter;
            this.number = number;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Well well = (Well) o;

            if (letter != well.letter) return false;
            //noinspection RedundantIfStatement
            if (number != well.number) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = (int) letter;
            result = 31 * result + number;
            return result;
        }

        public Well(String wellName) {
            if(wellName == null || wellName.length() < 2) {
                throw new IllegalArgumentException("wellName must be in the form A1, or A01");
            }
            wellName = wellName.toUpperCase();
            char letter = wellName.toCharArray()[0];
            if(letter < 65 || letter > 90) {
                throw new IllegalArgumentException("wellName must be in the form A1, or A01");
            }
            this.letter = letter;

            int number;
            try {
                number = Integer.parseInt(wellName.substring(1));
            }
            catch(NumberFormatException ex) {
                throw new IllegalArgumentException("wellName must be in the form A1, or A01");
            }
            this.number = number;
        }

        /**
         * zero indexed...
         * @return
         */
        public int row() {
            return ((int)letter)-65;
        }

        /**
         * zero indexed...
         * @return
         */
        public int col() {
            return number-1;
        }

        /**
         *
         * @return eg. "A1"
         */
        @Override
        public String toString() {
            return "" + letter + number;
        }

        /**
         *
         * @return eg. "A01"
         */
        public String toPaddedString() {
            String number = "" + this.number;
            return "" + letter + (number.length() < 2 ? "0" : "") + number;
        }
    }

    public static final class LatLong {

        public final double latitude;
        public final double longitude;

        public LatLong(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String toBarstoolFormat() {
            return "" + Math.abs(latitude) + (latitude < 0 ? " S " : " N ") + Math.abs(longitude) + (longitude < 0 ? " W" : " E");
        }
    }

    /**
     * Take an ab1 filename and attempt to pull out the well name
     *
     * @param fileName
     * @param separator
     * @param partNumber
     * @return well or null if couldn't parse out well name
     */
    public static Well getWellFromFileName(String fileName, String separator, int partNumber) {
        String[] nameParts = fileName.split(separator);
        if(partNumber >= nameParts.length) {
            return null;
        }

        String wellStringBig = nameParts[partNumber];
        int count = 1;
        int wellNumber = -1;
        String wellNumberString = "";
        while(true) {
            if(count >= wellStringBig.length()) {
                break;
            }
            char numberChar = wellStringBig.charAt(count);
            try{
                wellNumber = Integer.parseInt(wellNumberString+numberChar);
            }
            catch(NumberFormatException ex) {
                break;
            }
            wellNumberString += numberChar;
            count++;
        }
        return new Well(wellStringBig.toUpperCase().charAt(0), wellNumber);
    }

    public static String getBarcodeFromFileName(String fileName, String separator, int partNumber) {
        String[] nameParts = fileName.split(separator);
        if(partNumber >= nameParts.length) {
            return null;
        }
        return nameParts[partNumber];
    }

    public static boolean isAlignmentOfContigs(AnnotatedPluginDocument alignmentDoc) throws DocumentOperationException {
        SequenceAlignmentDocument alignment = (SequenceAlignmentDocument)alignmentDoc.getDocument();
        for (int i = 0; i < alignment.getNumberOfSequences(); i ++) {
            if (i == alignment.getContigReferenceSequenceIndex()) continue;
            return !(alignment.getSequence(i) instanceof NucleotideGraphSequenceDocument);
        }
        return false;
    }
}
