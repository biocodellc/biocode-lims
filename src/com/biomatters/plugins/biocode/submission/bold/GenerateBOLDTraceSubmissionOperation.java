package com.biomatters.plugins.biocode.submission.bold;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.URN;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideGraphSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.assembler.BatchChromatogramExportOperation;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingOptions;
import com.biomatters.plugins.biocode.labbench.reaction.PCROptions;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;
import jxl.Workbook;
import jxl.read.biff.BiffException;
import jxl.write.Label;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Exports traces to a zip file for submission to BOLD. In accordance with the
 * <a href="http://www.boldsystems.org/index.php/resources/handbook?chapter=3_submissions.html#trace_submissions"> specification</a>,
 * the zip file will contain a folder that contains:
 * <ul>
 *     <li>data.xls - Describing the traces</li>
 *     <li>ab1/scf files for each trace</li>
 * </ul>
 *
 *
 * @author Matthew Cheung
 *         Created on 26/08/14 3:09 PM
 */
public class GenerateBOLDTraceSubmissionOperation extends DocumentOperation {

    @Override
    public GeneiousActionOptions getActionOptions() {
        return new GeneiousActionOptions("BOLD Trace Submission...").setMainMenuLocation(GeneiousActionOptions.MainMenu.Export);
    }

    @Override
    public String getHelp() {
        return "Select any number of chromatograms to export them to a zip file ready for submission to BOLD.";
    }

    @Override
    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(NucleotideGraphSequenceDocument.class, 1, Integer.MAX_VALUE)
        };
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument[] documentsToExport) throws DocumentOperationException {
        return new BoldTraceSubmissionOptions(documentsToExport);
    }

    @Override
    public void performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options _options, SequenceSelection sequenceSelection, OperationCallback callback) throws DocumentOperationException {
        progressListener.setIndeterminateProgress();
        if (!(_options instanceof BoldTraceSubmissionOptions)) {
            throw new DocumentOperationException("Options must be obtained from calling getOptions(), expected " + BoldTraceSubmissionOptions.class.getSimpleName() + " but was " + _options.getClass().getSimpleName());
        }
        BoldTraceSubmissionOptions options = (BoldTraceSubmissionOptions) _options;

        DocumentOperation traceExporter = PluginUtilities.getDocumentOperation("batchChromatogramsExport");
        if (traceExporter == null) {
            throw new DocumentOperationException("Could not find Batch Chromatogram Exporter.  Please try again.  " +
                    "If the problem persists contact " + BiocodePlugin.SUPPORT_EMAIL);
        }

        File zipFile = options.getZipFile();
        if(zipFile.exists()) {
            if(!Dialogs.showYesNoDialog(zipFile.getName() + " already exists, do you want to replace it?", "Replace File?", null, Dialogs.DialogIcon.INFORMATION)) {
                return;
            }
        }

        File workingDirectory;
        File submissionDir;
        try {
            workingDirectory = FileUtilities.createTempDir(true);
            submissionDir = new File(workingDirectory, options.getSubmissionName());
            if(!submissionDir.mkdirs()) {
                throw new DocumentOperationException("Failed to create export directory: " + submissionDir.getAbsolutePath());
            }
        } catch (IOException e) {
            throw new DocumentOperationException("Failed to create temporary directory: " + e.getMessage(), e);
        }

        CompositeProgressListener compositeProgress = new CompositeProgressListener(progressListener, 0.2, 0.1, 0.25, 0.25, 0.2);

        compositeProgress.beginSubtask("Examining input files");
        Map<AnnotatedPluginDocument, TraceInfo> traceEntries;
        try {
            traceEntries = mapDocumentsToPrimers(annotatedDocuments, options);
            if(traceEntries.isEmpty()) {
                throw new DocumentOperationException("Could not find any primer information for your traces.  " +
                        "This could be because they are not annotated with LIMS information or that matching reactions could not be found in the current LIMS.");
            }
            Set<String> docsMissingPrimers = new HashSet<String>();
            for (AnnotatedPluginDocument document : annotatedDocuments) {
                if(traceEntries.get(document) == null) {
                    docsMissingPrimers.add(document.getName());
                }
            }
            if(!docsMissingPrimers.isEmpty()) {
                Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(Dialogs.CONTINUE_CANCEL, "Missing primer information");
                dialogOptions.setMoreOptionsButtonText("Show Document List", "Hide Document List");
                if(Dialogs.CANCEL == Dialogs.showMoreOptionsDialog(dialogOptions, "Cannot determine primer information for " +
                        BiocodeUtilities.getCountString("document", docsMissingPrimers.size()) + ".  This could be " +
                        "because they are not annotated with LIMS information or that matching reactions could not be found in the current LIMS.\n\n" +
                                "These will not be included in the submission package.", StringUtilities.join("\n", docsMissingPrimers))) {
                    return;
                }
            }
            if(compositeProgress.isCanceled()) {
                return;
            }
        } catch (MissingFieldValueException e) {
            if(Geneious.isHeadless()) {
                System.err.println(e.getMessage());
                e.printStackTrace();
            } else {
                e.showDialog();
            }
            return;
        }

        RenamingOptions.RenameMap renameMap = options.isUserManuallyEntering() ? null : askUserAboutRenamingPrimers(traceEntries.values());

        compositeProgress.beginSubtask("Writing data.xls spreadsheet");
        createTracesSpreadsheet(traceEntries.values(), renameMap, submissionDir, compositeProgress);

        List<AnnotatedPluginDocument> forwardDocs = new ArrayList<AnnotatedPluginDocument>();
        List<AnnotatedPluginDocument> reverseDocs = new ArrayList<AnnotatedPluginDocument>();
        for (Map.Entry<AnnotatedPluginDocument, TraceInfo> entry : traceEntries.entrySet()) {
            AnnotatedPluginDocument doc = entry.getKey();
            TraceInfo info = entry.getValue();
            if(info.forwardNotReverse) {
                forwardDocs.add(doc);
            } else {
                reverseDocs.add(doc);
            }
        }
        compositeProgress.beginSubtask("Exporting forward traces");
        exportTracesToFolder(forwardDocs, submissionDir, options.getForwardSuffix(), compositeProgress, traceExporter);
        if(compositeProgress.isCanceled()) {
            return;
        }

        compositeProgress.beginSubtask("Exporting reverse traces");
        exportTracesToFolder(reverseDocs, submissionDir, options.getReverseSuffix(), compositeProgress, traceExporter);
        if(compositeProgress.isCanceled()) {
            return;
        }

        try {
            compositeProgress.beginSubtask("Zipping submission folder");
            FileUtilities.zip(submissionDir, zipFile, true, compositeProgress);
        } catch (IOException e) {
            throw new DocumentOperationException("Failed to create submission package: " + e.getMessage(), e);
        }
    }

    private static RenamingOptions.RenameMap askUserAboutRenamingPrimers(Collection<TraceInfo> values) throws DocumentOperationException.Canceled {
        Set<String> primers = new HashSet<String>();
        Set<String> loci = new HashSet<String>();
        for (TraceInfo value : values) {
            primers.add(value.forwardPcrPrimer);
            primers.add(value.reversePcrPrimer);
            if(!value.sequencingPrimer.isEmpty()) {  // Might be empty since it's a non required field
                primers.add(value.sequencingPrimer);
            }
            loci.add(value.locus);
        }
        final RenamingOptions renamingOptions = new RenamingOptions(primers, loci);
        final AtomicBoolean userClickedOK = new AtomicBoolean();
        ThreadUtilities.invokeNowOrWait(new Runnable() {
            public void run() {
                userClickedOK.set(Dialogs.showOptionsDialog(renamingOptions, "Rename?", true));
            }
        });

        if(!userClickedOK.get()) {
            throw new DocumentOperationException.Canceled();
        }
        return renamingOptions.getRenameMap();
    }

    private static Map<AnnotatedPluginDocument, TraceInfo> mapDocumentsToPrimers(AnnotatedPluginDocument[] inputDocs, BoldTraceSubmissionOptions options) throws DocumentOperationException, MissingFieldValueException {
        Map<URN, Boolean> directionForDocs = new HashMap<URN, Boolean>();
        Multimap<String, AnnotatedPluginDocument> names = ArrayListMultimap.create();
        for (AnnotatedPluginDocument annotatedDocument : inputDocs) {
            Object isForward = annotatedDocument.getFieldValue(BiocodeUtilities.IS_FORWARD_FIELD);
            String name;
            if(isForward == null) {
                throw new DocumentOperationException(annotatedDocument.getName() + " has no direction.  All chromatograms must have a direction set");
            } else {
                boolean fowardDirection = Boolean.TRUE.equals(isForward);
                directionForDocs.put(annotatedDocument.getURN(), fowardDirection);
                name = getFilename(options, annotatedDocument, fowardDirection);
            }
            names.put(name, annotatedDocument);
        }

        for (Map.Entry<String, Collection<AnnotatedPluginDocument>> entry : names.asMap().entrySet()) {
            if(entry.getValue().size() > 1) {
                List<String> docNames = new ArrayList<String>();
                for (AnnotatedPluginDocument document : entry.getValue()) {
                    docNames.add(document.getName());
                }
                throw new DocumentOperationException("Duplicate name detected: <strong>" + entry.getKey() + "</strong>.\n\n" +
                                    "The following documents will be exported with this same name:\n" + StringUtilities.join("\n", docNames) +
                                    "\n\n<strong>Hint</strong>: If your forward and reverse traces use the same name, try adding a suffix."
                );
            }
        }

        getFieldValuesFromDocs(inputDocs, options.getProcessIdField());
        if(options.isUserManuallyEntering()) {
            return mapDocumentsToUserSpecifiedValues(inputDocs, directionForDocs, options);
        } else {
            return mapDocumentsToPrimersUsingLimsInfo(inputDocs, directionForDocs, options);
        }
    }

    private static Map<AnnotatedPluginDocument, TraceInfo> mapDocumentsToUserSpecifiedValues(AnnotatedPluginDocument[] inputDocs, Map<URN, Boolean> directionForDocs, BoldTraceSubmissionOptions options) {
        Map<AnnotatedPluginDocument, TraceInfo> result = new HashMap<AnnotatedPluginDocument, TraceInfo>();
        for (AnnotatedPluginDocument inputDoc : inputDocs) {
            Boolean isForward = directionForDocs.get(inputDoc.getURN());
            result.put(inputDoc, new TraceInfo(
                    getFilename(options, inputDoc, isForward), options.getUserEnteredForwardPcrPrimer(),
                    options.getUserEnteredReversePcrPrimer(), options.getUserEnteredSequencingPrimer(), isForward,
                    String.valueOf(inputDoc.getFieldValue(options.getProcessIdField())), options.getUserEnteredMarker()
            ));
        }
        return result;
    }

    private static Map<AnnotatedPluginDocument, TraceInfo> mapDocumentsToPrimersUsingLimsInfo(AnnotatedPluginDocument[] inputDocs, Map<URN, Boolean> directionForDocs, BoldTraceSubmissionOptions options) throws MissingFieldValueException, DocumentOperationException {
        Map<AnnotatedPluginDocument, TraceInfo> result = new HashMap<AnnotatedPluginDocument, TraceInfo>();
        Multimap<String, AnnotatedPluginDocument> workflowToDocument = getFieldValuesFromDocs(inputDocs, BiocodeUtilities.WORKFLOW_NAME_FIELD);
        // Also check for the existence of sequencing plate field and process ID field.  Even if we don't actually need the map.
        getFieldValuesFromDocs(inputDocs, BiocodeUtilities.SEQUENCING_PLATE_FIELD);

        try {
            List<WorkflowDocument> workflowDocs = BiocodeService.getInstance().getWorkflowDocumentsForNames(workflowToDocument.keySet());
            for (WorkflowDocument workflowDoc : workflowDocs) {
                for (AnnotatedPluginDocument document : workflowToDocument.get(workflowDoc.getName())) {
                    Reaction cyclesequencingReaction = getSequencingReactionForDoc(workflowDoc, document);
                    if(cyclesequencingReaction == null) {
                        continue;
                    }
                    String seqPrimer = getPrimerNameFromReaction(cyclesequencingReaction, CycleSequencingOptions.PRIMER_OPTION_ID);
                    if(seqPrimer == null) {
                        seqPrimer = "";  // This is a non required field.  So we'll set it to empty String if null.
                    }
                    Reaction pcrReaction = getMostLikelyPcrReactionForSeqReaction(workflowDoc, cyclesequencingReaction);
                    if(pcrReaction == null) {
                        continue;
                    }
                    String fwdPcrPrimer = getPrimerNameFromReaction(pcrReaction, PCROptions.PRIMER_OPTION_ID);
                    String revPcrPrimer = getPrimerNameFromReaction(pcrReaction, PCROptions.PRIMER_REVERSE_OPTION_ID);
                    Boolean isForward = directionForDocs.get(document.getURN());
                    result.put(document,
                            new TraceInfo(getFilename(options, document, isForward),
                                    fwdPcrPrimer, revPcrPrimer, seqPrimer, isForward,
                                    String.valueOf(document.getFieldValue(options.getProcessIdField())), workflowDoc.getWorkflow().getLocus())
                    );
                }

            }
            return result;
        } catch (DatabaseServiceException e) {
            throw new DocumentOperationException("Failed to retrieve workflows from LIMS: " + e.getMessage(), e);
        }
    }

    private static final String TRACE_FILE_EXTENSION = ".ab1";
    private static String getFilename(BoldTraceSubmissionOptions options, AnnotatedPluginDocument document, Boolean isForward) {
        String suffix = isForward ? options.getForwardSuffix() : options.getReverseSuffix();
        return BiocodeUtilities.getNiceExportedFilename(document.getName(), TRACE_FILE_EXTENSION, suffix);
    }

    static Reaction getMostLikelyPcrReactionForSeqReaction(WorkflowDocument workflowDoc, Reaction cyclesequencingReaction) {
        List<Reaction> pcrReactions = workflowDoc.getReactions(Reaction.Type.PCR);
        if(pcrReactions.isEmpty()) {
            return null;
        }
        Reaction pcrReaction = null;
        for (Reaction candidate : pcrReactions) {
            boolean isBeforeSeq = beforeOrSameDay(cyclesequencingReaction, candidate);
            if(isBeforeSeq && (pcrReaction == null || candidate.getDate().after(pcrReaction.getDate()))) {
                pcrReaction = candidate;
            }
        }
        return pcrReaction;
    }

    /**
     * The LIMS only stores year, month and day.  So this method checks those and ignores hours, seconds and milliseconds.
     *
     * @param checkAgainst The reaction to check against
     * @param candidate The reaction to check
     * @return true if the candidate is before or on the same day (ignoring time)
     */
    private static boolean beforeOrSameDay(Reaction checkAgainst, Reaction candidate) {
        GregorianCalendar checkAgainstCal = new GregorianCalendar();
        checkAgainstCal.setTime(checkAgainst.getDate());

        GregorianCalendar candidateCal = new GregorianCalendar();
        candidateCal.setTime(candidate.getDate());

        boolean sameYear = candidateCal.get(Calendar.YEAR) == checkAgainstCal.get(Calendar.YEAR);
        boolean sameMonth = candidateCal.get(Calendar.MONTH) == checkAgainstCal.get(Calendar.MONTH);
        return candidateCal.get(Calendar.YEAR) < checkAgainstCal.get(Calendar.YEAR) ||
                (sameYear && candidateCal.get(Calendar.MONTH) < checkAgainstCal.get(Calendar.MONTH)) ||
                (sameYear && sameMonth && candidateCal.get(Calendar.DAY_OF_MONTH) <= checkAgainstCal.get(Calendar.DAY_OF_MONTH));
    }

    private static String getPrimerNameFromReaction(Reaction reaction, String optionKey) {
        if(reaction != null) {
            Object value = reaction.getOptions().getValue(optionKey);
            if (value != null && value instanceof DocumentSelectionOption.FolderOrDocuments) {
                List<AnnotatedPluginDocument> docs = ((DocumentSelectionOption.FolderOrDocuments) value).getDocuments();
                return docs.isEmpty() ? null : docs.get(0).getName();
            }
        }
        return null;
    }

    private static Reaction getSequencingReactionForDoc(WorkflowDocument workflowDoc, AnnotatedPluginDocument document) {
        List<Reaction> seqReactions = workflowDoc.getReactions(Reaction.Type.CycleSequencing);
        Reaction cyclesequencingReaction = null;
        for (Reaction seqReaction : seqReactions) {
            if(seqReaction.getPlateName().equals(document.getFieldValue(BiocodeUtilities.SEQUENCING_PLATE_FIELD))) {
                cyclesequencingReaction = seqReaction;
            }
        }
        return cyclesequencingReaction;
    }

    /**
     *
     * @param inputDocs The documents to map to field values.
     * @param field The field to retrieve.
     * @return A {@link com.google.common.collect.Multimap} from the field value to the collection of documents that have that field value.
     * @throws MissingFieldValueException if any documents are missing a value for the specified field.
     */
    static Multimap<String, AnnotatedPluginDocument> getFieldValuesFromDocs(AnnotatedPluginDocument[] inputDocs, DocumentField field) throws MissingFieldValueException {
        Multimap<String, AnnotatedPluginDocument> map = ArrayListMultimap.create();
        Set<String> namesOfDocsMissingValue = new HashSet<String>();
        for (AnnotatedPluginDocument inputDoc : inputDocs) {
            Object object = inputDoc.getFieldValue(field);
            if(object == null) {
                namesOfDocsMissingValue.add(inputDoc.getName());
            } else {
                map.put(String.valueOf(object), inputDoc);
            }
        }
        if(!namesOfDocsMissingValue.isEmpty()) {
            throw new MissingFieldValueException(field, namesOfDocsMissingValue);
        }
        return map;
    }

    private static void exportTracesToFolder(List<AnnotatedPluginDocument> tracesToExport, File submssionDir, String filenameSuffix, ProgressListener progressListener, DocumentOperation traceExporter) throws DocumentOperationException {
        try {
            File tempTracesDir = FileUtilities.createTempDir(true);
            Options exportOptions = traceExporter.getOptions(tracesToExport);
            exportOptions.setValue(BatchChromatogramExportOperation.EXPORT_FOLDER, tempTracesDir.getAbsolutePath());
            traceExporter.performOperation(tracesToExport, progressListener, exportOptions);
            File[] filesInDir = tempTracesDir.listFiles();
            if (filesInDir == null) {
                throw new DocumentOperationException("Could not list files in " + tempTracesDir.getAbsolutePath());
            }
            for (File f : filesInDir) {
                File dest = new File(submssionDir, BiocodeUtilities.getNiceExportedFilename(f.getName(), TRACE_FILE_EXTENSION, filenameSuffix));
                if (!FileUtilities.renameToWithRetry(f, dest)) {
                    throw new DocumentOperationException("Failed to move file " + f.getAbsolutePath() + " to " + dest.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            throw new DocumentOperationException("Failed to export chromatograms: " + e.getMessage(), e);
        }
    }

    static File createTracesSpreadsheet(Collection<TraceInfo> traceEntries, RenamingOptions.RenameMap renameMap, File submssionDir, ProgressListener progressListener) throws DocumentOperationException {
        try {
            File templateFile = getTemplateSpreadsheet();
            File spreadsheetFile = new File(submssionDir, "data.xls");

            Workbook template = Workbook.getWorkbook(templateFile);
            WritableWorkbook spreadsheet = Workbook.createWorkbook(spreadsheetFile, template);
            WritableSheet sheet = spreadsheet.getSheet(0);

            List<TraceInfo> sortedTraceInfo = new ArrayList<TraceInfo>(traceEntries);
            Collections.sort(sortedTraceInfo, new Comparator<TraceInfo>() {
                @Override
                public int compare(TraceInfo o1, TraceInfo o2) {
                    return o1.filename.compareTo(o2.filename);
                }
            });

            int rowIndex = 1;
            CompositeProgressListener compositeProgress = new CompositeProgressListener(progressListener, traceEntries.size());
            for (TraceInfo entry : sortedTraceInfo) {
                compositeProgress.beginSubtask();
                String forwardPrimer = renameMap == null ? entry.forwardPcrPrimer : renameMap.getNameForPrimer(entry.forwardPcrPrimer);
                String reversePrimer = renameMap == null ? entry.reversePcrPrimer : renameMap.getNameForPrimer(entry.reversePcrPrimer);
                String sequencingPrimer = renameMap == null ? entry.sequencingPrimer : renameMap.getNameForPrimer(entry.sequencingPrimer);
                String marker = renameMap == null ? entry.locus : renameMap.getMarkerForLocus(entry.locus);
                sheet.addCell(new Label(0, rowIndex, entry.filename));
                sheet.addCell(new Label(2, rowIndex, forwardPrimer));
                sheet.addCell(new Label(3, rowIndex, reversePrimer));
                sheet.addCell(new Label(4, rowIndex, sequencingPrimer));
                sheet.addCell(new Label(5, rowIndex, entry.forwardNotReverse ? "F" : "R"));
                sheet.addCell(new Label(6, rowIndex, entry.processId));
                sheet.addCell(new Label(9, rowIndex, marker));
                rowIndex++;
            }
            spreadsheet.write();
            spreadsheet.close();

            return spreadsheetFile;
        } catch (IOException e) {
            throw new DocumentOperationException("Failed to export package: " + e.getMessage(), e);
        } catch (BiffException e) {
            throw new DocumentOperationException("Failed to read template spreadsheet: " + e.getMessage(), e);
        } catch (WriteException e) {
            throw new DocumentOperationException("Failed to write spreadsheet: " + e.getMessage(), e);
        }
    }

    static File getTemplateSpreadsheet() {
        return FileUtilities.getResourceForClass(GenerateBOLDTraceSubmissionOperation.class, "data.xls");
    }
}
