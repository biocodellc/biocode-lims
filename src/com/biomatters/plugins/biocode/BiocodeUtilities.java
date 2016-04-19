package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.OptionsPanel;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideGraphSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.OligoSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.assembler.SetReadDirectionOperation;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingReaction;
import com.biomatters.plugins.biocode.labbench.reaction.MemoryFile;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.utilities.ObjectAndColor;
import jebl.util.Cancelable;
import jebl.util.ProgressListener;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

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
    public static final DocumentField EXTRACTION_BARCODE_FIELD = DocumentField.createStringField("Extraction Barcode", "The code of the 2D-barcode on the well containing the extraction (if present)", "extractionBarcode", true, false);
    public static final DocumentField REACTION_STATUS_FIELD = DocumentField.createStringField("ReactionStatus", "The status of the reaction (e.g. passed, failed etc.)", "reactionStatus", true, false);
    public static final String[] taxonomyNames = new String [] {"kingdom", "phylum", "subphylum", "sub phylum",
            "sub-phylum", "superclass", "super class", "super-class", "class", "subclass", "sub class", "sub-class",
            "infraclass", "infra class", "infra-class", "superorder", "super order", "super-order", "ordr", "order",
            "suborder", "sub order", "sub-order", "infraorder", "infra order", "infra-order", "superfamily",
            "super family", "super-family", "family", "tribe", "subtribe", "sub tribe", "sub-tribe", "genus",
            "subgenus", "sub genus", "sub-genus", "specificepithet", "specific epithet", "subspecificepithet",
            "subspecific epithet", "scientificname", "scientific name"};

    public static final List<DocumentField> PLATE_FIELDS = Arrays.asList(
            BiocodeUtilities.SEQUENCING_PLATE_FIELD,
            BiocodeService.FWD_PLATE_FIELD,
            BiocodeService.REV_PLATE_FIELD
    );

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

    public static void displayExceptionDialog(Exception exception) {
        displayExceptionDialog("Error", exception.getMessage(), exception, null);
    }

    public static void displayExceptionDialog(String title, String message, Exception exception, Component owner) {
        StringWriter stacktrace = new StringWriter();
        exception.printStackTrace(new PrintWriter(stacktrace));
        Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(Dialogs.OK_ONLY, title, owner, Dialogs.DialogIcon.INFORMATION);
        dialogOptions.setMoreOptionsButtonText("Show Details", "Hide Details");
        Dialogs.showMoreOptionsDialog(dialogOptions, message, stacktrace.toString());
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


    public static void downloadTracesForReactions(Collection<CycleSequencingReaction> reactions_a, ProgressListener progressListener) throws DatabaseServiceException, IOException, DocumentImportException{
        List<CycleSequencingReaction> reactions = new ArrayList<CycleSequencingReaction>();
        for(CycleSequencingReaction reaction : reactions_a) { //try not to download if we've already downloaded!
            if(!reaction.hasDownloadedChromats()) {
                reactions.add(reaction);
            }
        }

        List<Integer> reactionIds = new ArrayList<Integer>();
        for(Reaction r : reactions) {
            if(r.getId() >= 0) {
                reactionIds.add(r.getId());
            }
        }
        if(reactionIds.size() == 0) {
            return;
        }

        Map<Integer, List<MemoryFile>> results = BiocodeService.getInstance().getActiveLIMSConnection().downloadTraces(reactionIds, progressListener);
        if(progressListener.isCanceled()) {
            return;
        }
        for (Map.Entry<Integer, List<MemoryFile>> result : results.entrySet()) {
           if (progressListener.isCanceled()) return;
            //todo: there might be multiple instances of the same reaction in this list, so we loop through everything each time.  maybe we could sort the list if this is too slow?
            for(CycleSequencingReaction r : reactions) {
                if(r.getId() == result.getKey()) {
                    r.setChromats(result.getValue());
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
            if (isAlignmentOfContigConsensusSequences(document)) {
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
            FimsSample fimsSample = doc.getFimsSample();
            if(fimsSample != null) {
                fields.addAll(fimsSample.getFimsAttributes());
                fields.addAll(fimsSample.getTaxonomyAttributes());
            }
        }
        return new ArrayList<DocumentField>(fields);
    }

    /**
     *
     * @param component a component that has been added to the dialog
     * @return
     */
    public static JButton getDialogOkButton(JComponent component) {
        JRootPane rootPane = component.getRootPane();
        if(rootPane != null) {
            return rootPane.getDefaultButton();
        }
        return null;
    }

    public static ProgressFrame getBlockingProgressFrame(String message, JComponent ownerComponent) {
        Window owner;
        if (ownerComponent.getTopLevelAncestor() instanceof Window) {
            owner = (Window) ownerComponent.getTopLevelAncestor();
        } else {
            owner = GuiUtilities.getMainFrame();
        }
        ProgressFrame progressFrame = new ProgressFrame(message, "", owner);
        progressFrame.setCancelable(false);
        progressFrame.setIndeterminateProgress();
        return progressFrame;
    }

    /**
     * The same as {@link com.biomatters.geneious.publicapi.plugin.Options.Option#getValueAsString()}.  Is required for versions before R7.1 where the method
     * is not publicly available.  The method was supposed to be introduced in R6, however a bug caused it to be
     * obfuscated away.
     */
    @SuppressWarnings("unchecked")
    public static String getValueAsString(Options.Option option) {
        return option.getValueAsString(option.getValue());
    }

    public static JPanel getRoundedBorderPanel(String message) {
        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setContentType("text/html");
        textPane.setText("<html>" + GuiUtilities.getHtmlHead() + "<body>" + "<center>" + message + "</center>" + "</body>" + "</html>");
        JPanel roundedBorderPanel = new JPanel(new BorderLayout());
        roundedBorderPanel.setBackground(Color.WHITE);
        OptionsPanel.RoundedLineBorder border = new OptionsPanel.RoundedLineBorder(null, false);
        border.setInsets(new Insets(30,30,30,30));
        roundedBorderPanel.setBorder(border);
        roundedBorderPanel.add(textPane, BorderLayout.CENTER);
        roundedBorderPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        return roundedBorderPanel;
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
                        String nameOfSequenceAnnotatationWithoutReferenceDocument = contig.getSequence(i).getName();
                        throw new DocumentOperationException(
                                "There is no reference document for sequence <strong>" + nameOfSequenceAnnotatationWithoutReferenceDocument + "</strong>" +
                                " in contig assembly <strong>" + contig.getName() + "</strong>." +
                                " Extract " + nameOfSequenceAnnotatationWithoutReferenceDocument + " to create a new reference document for it and try again." +
                                "<br><br>For more assistance, please contact Moorea Biocode support via " + BiocodePlugin.SUPPORT_EMAIL + "."
                        );
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

    public interface ValueGetter {
        Object get(String key);
    }

    public static List<String> getPlatesFromGetter(ValueGetter getter) {
        List<String> results = new ArrayList<String>();
        for (DocumentField plateField : PLATE_FIELDS) {
            Object value = getter.get(plateField.getCode());
            if(value != null) {
                for (String plate : value.toString().split(",")) {  // Forward and Reverse plate fields can contain a comma separated list
                    results.add(plate);
                }
            }
        }
        return results;
    }

    public static List<String> getPlatesAnnotatedOnDocument(final AnnotatedPluginDocument doc) {
        return getPlatesFromGetter(new ValueGetter() {
            @Override
            public Object get(String key) {
                return doc.getFieldValue(key);
            }
        });
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
        if(wellNumberString.length() == 0) {
            return null;
        }
        return new Well(wellStringBig.toUpperCase().charAt(0), wellNumber);
    }

    public static ObjectAndColor getObjactAndColorFromBinningHtml(String binningHtml) {
        String htmlStart = "<html><head><";
        String htmlStart2 = "></head><b><font color='#";
        if(binningHtml.length() > htmlStart.length() + htmlStart2.length() && binningHtml.startsWith(htmlStart) && binningHtml.substring(htmlStart.length()+1).startsWith(htmlStart2)) {
            try {
                int prefixLength = htmlStart.length() + 1 + htmlStart2.length();
                int colorEnd = binningHtml.indexOf('\'', prefixLength);
                String colorString = binningHtml.substring(prefixLength, colorEnd);
                String label = binningHtml.substring(colorEnd+2, binningHtml.indexOf("<", colorEnd+2));
                Color col = new Color(Integer.parseInt(colorString, 16));
                return new ObjectAndColor(label, col);
            } catch (NumberFormatException e) { //if the color definition isn't actually a color
                assert false : e.getMessage();
            } catch (IndexOutOfBoundsException e) { //if the parsing fails
                assert false : e.getMessage();
            }
        }
        return new ObjectAndColor(binningHtml, Color.black);
    }

    public static boolean isAlignmentOfContigConsensusSequences(AnnotatedPluginDocument alignmentDoc) throws DocumentOperationException {
        SequenceAlignmentDocument alignment = (SequenceAlignmentDocument)alignmentDoc.getDocument();
        for (int i = 0; i < alignment.getNumberOfSequences(); i ++) {
            if (i == alignment.getContigReferenceSequenceIndex()) continue;
           AnnotatedPluginDocument referenceDoc = alignment.getReferencedDocument(i);
            if(referenceDoc == null) {
                return false;
            }
            if(!SequenceAlignmentDocument.class.isAssignableFrom(referenceDoc.getDocumentClass())) {
                return false;
            }
            SequenceAlignmentDocument referenceAlignment = (SequenceAlignmentDocument)referenceDoc.getDocument();
            if(!referenceAlignment.isContig()) {
                return false;
            }
        }
        return true;
    }

    public static boolean isAlignmentOfChromatograms(AnnotatedPluginDocument alignmentDoc) throws DocumentOperationException {
        SequenceAlignmentDocument alignment = (SequenceAlignmentDocument)alignmentDoc.getDocument();
        for (int i = 0; i < alignment.getNumberOfSequences(); i ++) {
            if (i == alignment.getContigReferenceSequenceIndex()) continue;
            if(!(alignment.getSequence(i) instanceof NucleotideGraphSequenceDocument)) {
                return false;
            }
        }
        return true;
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

    /**
     * Compares version numbers.
     *
     * @param versionA version string of the form nnn[.nnn[.nnn][-nnn]]
     * @param versionB version string of the form nnn[.nnn[.nnn][-nnn]]
     * @return 0 if the two versions are equal, or a negative number if versionA < versionB
     * or a positive number if versionA > versionB.
     * The absolute value of the return value specifies the first field in which the versions differ.
     * Fields that are present in only one version string are interpreted as 0 in the other.
     * It is guaranteed that compare(a,b) == - compare(b,a).
     *
     * For example
     *   compare("2.0", "2.0.1") == -3   (the 3rd field is the first in which they differ)
     *   compare("2.0.1", "2.0") == 3  (3rd field again, but order is other way round)
     *   compare("0.9", "1.0") == -1
     *   compare("0.9", "0.9") == 0
     *   compare("2.5", "2.5.0") == 0
     */
    public static int compareVersions(String versionA, String versionB) {
        String[] fieldsA = versionA.split("[\\.-]");
        String[] fieldsB = versionB.split("[\\.-]");

        int maxLength = Math.max(fieldsA.length, fieldsB.length);

        for (int fieldNum = 0; fieldNum < maxLength; fieldNum++) {
            int valA = (fieldNum < fieldsA.length ? Integer.parseInt(fieldsA[fieldNum]) : 0);
            int valB = (fieldNum < fieldsB.length ? Integer.parseInt(fieldsB[fieldNum]) : 0);
            if (valA != valB) {
                return signum(valA - valB) * (fieldNum+1);
            }
        }
        // return signum(fieldsA.length - fieldsB.length) * (maxLength + 1); (if we want 2.5 < 2.5.0; then, also change max to min above)
        return 0;
    }

    private static int signum(int i) {
        return (i == 0) ? 0 : ((i > 0) ? 1 : -1);
    }
}
