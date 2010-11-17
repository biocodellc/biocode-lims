package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideGraphSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.geneious.publicapi.implementations.sequence.OligoSequenceDocument;
import com.biomatters.plugins.biocode.assembler.SetReadDirectionOperation;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingOptions;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.reaction.ReactionUtilities;
import jebl.util.ProgressListener;
import jebl.util.Cancelable;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.text.NumberFormat;

/**
 * @author Richard
 * @version $Id$
 */
public class BiocodeUtilities {

    public static final String NOT_CONNECTED_ERROR_MESSAGE = "<html><b>You must connect to the lab bench service first.</b><br><br>" +
            "To connect, right-click on the Biocode service in the Sources panel to the left.";

    public static final DocumentField SEQUENCING_PLATE_FIELD = DocumentField.createStringField("Sequencing Plate", "Name of the cycle sequencing plate in the LIMS that this read was created from", "sequencingPlateName", false, false);
    public static final DocumentField SEQUENCING_WELL_FIELD = DocumentField.createStringField("Sequencing Well", "Well location on the cycle sequencing plate in the LIMS that this read was created from", "sequencingPlateWell", false, false);
    public static final DocumentField WORKFLOW_NAME_FIELD = DocumentField.createStringField("Workflow Name", "The name of the workflow that generated this trace", "workflowName", false, false);
    public static final DocumentField TRACE_ID_FIELD = DocumentField.createStringField("Trace ID", "A concatenation of the sequencing plate and well", "traceId", false, false);

    public static Options getConsensusOptions(AnnotatedPluginDocument[] selectedDocuments) throws DocumentOperationException {
        DocumentOperation consensusOperation = PluginUtilities.getDocumentOperation("Generate_Consensus");
        if (consensusOperation == null) {
            return null;
        }
        return consensusOperation.getOptions(selectedDocuments);
    }

    public static AnnotatedPluginDocument createPrimerDocument(String primerName, String primerSequence) {
        OligoSequenceDocument sequence = new OligoSequenceDocument(primerName, "", primerSequence, new Date());
        return DocumentUtilities.createAnnotatedPluginDocument(sequence);
    }

    /**
     *
     * @param doc
     * @param consensusOptions
     * @return generated consensus if doc is an alignment, doc if doc is a sequence, null if doc is a sequence with trace information (obviously isn't a consensus sequence)
     * @throws com.biomatters.geneious.publicapi.plugin.DocumentOperationException
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

    public static int getJavaVersion() {
        String version = System.getProperty("java.version");
        char minor = version.charAt(2);
        //char point = version.charAt(4);
        return Integer.parseInt(""+minor);
    }

    public static WorkflowDocument getMostRecentWorkflow(LIMSConnection limsConnection, FIMSConnection fimsConnection, Object tissueId) throws DocumentOperationException {
        WorkflowDocument mostRecent = null;
        try {
            List<FimsSample> tissues = fimsConnection.getMatchingSamples(Arrays.asList(tissueId.toString()));
            if (tissues.size() != 1) {
                return null;
            }

            List<WorkflowDocument> workflows = limsConnection.getMatchingWorkflowDocuments(null, tissues, null);
            for (WorkflowDocument workflow : workflows) {
                if (mostRecent == null || workflow.getNumberOfParts() > mostRecent.getNumberOfParts()) {
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

    public static String formatSize(long bytes, int decimalPlaces) {
        NumberFormat fmt = NumberFormat.getNumberInstance();
        if (decimalPlaces >= 0) {
            fmt.setMaximumFractionDigits(decimalPlaces);
        }
        double val = bytes / (1024 * 1024);
        if (val > 1) {
            return fmt.format(val).concat(" MB");
        }
        val = bytes / 1024;
        if (val > 10) {
            return fmt.format(val).concat(" KB");
        }
        return fmt.format(val).concat(" bytes");
    }


    public static void downloadTracesForReactions(List<CycleSequencingReaction> reactions, ProgressListener progressListener) throws SQLException, IOException, DocumentImportException{
        List<String> idQueries = new ArrayList<String>();
        for(Reaction r : reactions) {
            if(r.getId() >= 0) {
                idQueries.add("reaction="+r.getId());
            }
        }
        if(idQueries.size() == 0) {
            return;
        }

        Statement statement = BiocodeService.getInstance().getActiveLIMSConnection().getConnection().createStatement(java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_READ_ONLY);
        if(!BiocodeService.getInstance().getActiveLIMSConnection().isLocal()) {
            statement.setFetchSize(Integer.MIN_VALUE);
        }
        ResultSet countResultSet = statement.executeQuery("SELECT COUNT(*) FROM traces WHERE " + StringUtilities.join(" OR ", idQueries));
        countResultSet.next();
        int count = countResultSet.getInt(1);
        countResultSet.close();
        ResultSet resultSet = statement.executeQuery("SELECT * FROM traces WHERE " + StringUtilities.join(" OR ", idQueries));
        Map<Integer, List<ReactionUtilities.MemoryFile>> results = new HashMap<Integer, List<ReactionUtilities.MemoryFile>>();
        double pos = 0;
        long bytes = 0;
        while(resultSet.next()) {
            if (progressListener.isCanceled()) break;
            progressListener.setMessage("downloaded "+formatSize(bytes, 2));
            progressListener.setProgress(pos/count);
            pos++;
            ReactionUtilities.MemoryFile memoryFile = new ReactionUtilities.MemoryFile(resultSet.getString("name"), resultSet.getBytes("data"));
            bytes += memoryFile.getData().length;
            int id = resultSet.getInt("reaction");
            List<ReactionUtilities.MemoryFile> files = results.get(id);
            if(files == null) {
                files = new ArrayList<ReactionUtilities.MemoryFile>();
                results.put(id, files);
            }
            files.add(memoryFile);
        }
        resultSet.close();
        if(progressListener.isCanceled()) {
            return;
        }
        for (Map.Entry<Integer, List<ReactionUtilities.MemoryFile>> result : results.entrySet()) {
           if (progressListener.isCanceled()) return;
            //todo: there might be multiple instances of the same reaction in this list, so we loop through everything each time.  maybe we could sort the list if this is too slow?
            for(CycleSequencingReaction r : reactions) {
                if(r.getId() == result.getKey()) {
                    ((CycleSequencingOptions)r.getOptions()).addChromats(result.getValue());
                }
            }
        }
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

    public static List<WorkflowDocument> getWorkflowDocuments(AnnotatedPluginDocument[] docs) {
        List<WorkflowDocument> workflows = new ArrayList<WorkflowDocument>();
        for(AnnotatedPluginDocument doc : docs) {
            if(WorkflowDocument.class.isAssignableFrom(doc.getDocumentClass())) {
                workflows.add((WorkflowDocument)doc.getDocumentOrCrash());
            }
        }
        return workflows;
    }

    public static List<DocumentField> getFimsFields(List<WorkflowDocument> docs) {
        Set<DocumentField> fields = new LinkedHashSet<DocumentField>();
        for(WorkflowDocument doc : docs) {
            fields.addAll(doc.getFimsSample().getFimsAttributes());
        }
        return new ArrayList<DocumentField>(fields);
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
                throw new IllegalArgumentException("wellName must be in the form 'A1', or 'A01', but your well name was '"+wellName+"'");
            }
            wellName = wellName.toUpperCase();
            char letter = wellName.toCharArray()[0];
            if(letter < 65 || letter > 90) {
                throw new IllegalArgumentException("wellName must be in the form 'A1', or 'A01', but your well name was '"+wellName+"'");
            }
            this.letter = letter;

            int number;
            try {
                number = Integer.parseInt(wellName.substring(1));
            }
            catch(NumberFormatException ex) {
                throw new IllegalArgumentException("wellName must be in the form 'A1', or 'A01', but your well name was '"+wellName+"'");
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

    public static String getStringFromFileName(String fileName, String separator, int partNumber) {
        String[] parts = fileName.split(separator);
        if(parts.length < partNumber+1) {
            return null;
        }
        return parts[partNumber];
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
        String wellStringBig = getStringFromFileName(fileName, separator, partNumber);
        if(wellStringBig == null || wellStringBig.length() == 0) {
            return null;
        }
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

    public static class CancelListeningThread extends Thread{
        private Cancelable cancelable;
        private Statement statement;
        private boolean running = true;

        public CancelListeningThread(Cancelable cancelable, Statement statement){
            this.cancelable = cancelable;
            this.statement = statement;
            start();
        }

        @Override
        public void run() {
            while(running) {
                if(cancelable.isCanceled()) {
                    try {
                        if(statement.getResultSet() != null) {
                            statement.getResultSet().close();
                        }
                        statement.close();
                    } catch (SQLException e) {
                        //ignore...
                        System.out.println(e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                }
                ThreadUtilities.sleep(100);
            }
        }

        public void finish() {
            running = false;
        }
    }
}
