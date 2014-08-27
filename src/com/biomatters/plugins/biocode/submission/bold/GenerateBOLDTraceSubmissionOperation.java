package com.biomatters.plugins.biocode.submission.bold;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.URN;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideGraphSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
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
            traceEntries = mapDocumentsToPrimers(annotatedDocuments, options.getForwardSuffix(), options.getReverseSuffix(), options.getProcessIdField());
            if(traceEntries.isEmpty()) {
                throw new DocumentOperationException("Could not find workflows in LIMS");  // todo Handle partial case
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

        // todo Ask about renaming primers to match BOLD
        compositeProgress.beginSubtask("Writing data.xls spreadsheet");
        createTracesSpreadsheet(traceEntries.values(), submissionDir, compositeProgress);

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

    private static Map<AnnotatedPluginDocument, TraceInfo> mapDocumentsToPrimers(AnnotatedPluginDocument[] inputDocs, String forwardSuffix, String reverseSuffix, DocumentField processIdField) throws DocumentOperationException, MissingFieldValueException {
        Map<AnnotatedPluginDocument, TraceInfo> result = new HashMap<AnnotatedPluginDocument, TraceInfo>();

        Map<URN, Boolean> directionForDocs = new HashMap<URN, Boolean>();
        Set<String> names = new HashSet<String>();
        for (AnnotatedPluginDocument annotatedDocument : inputDocs) {
            Object isForward = annotatedDocument.getFieldValue(BiocodeUtilities.IS_FORWARD_FIELD);
            String name;
            if(isForward == null) {
                throw new DocumentOperationException(annotatedDocument.getName() + " has no direction.  All chromatograms must have a direction set");
            } else {
                boolean fowardDirection = Boolean.TRUE.equals(isForward);
                directionForDocs.put(annotatedDocument.getURN(), fowardDirection);
                name = annotatedDocument.getName() + (fowardDirection ? forwardSuffix : reverseSuffix);
            }
            if(names.contains(name)) {
                throw new DocumentOperationException("Duplicate name detected: " + name + ".  " +
                        "If your forward and reverse traces use the same name, try adding a suffix.");
            }
            names.add(name);
        }

        Multimap<String, AnnotatedPluginDocument> workflowToDocument = getFieldValuesFromDocs(inputDocs, BiocodeUtilities.WORKFLOW_NAME_FIELD);
          // Also check for the existence of sequencing plate field and process ID field.  Even if we don't actually need the map.
        getFieldValuesFromDocs(inputDocs, BiocodeUtilities.SEQUENCING_PLATE_FIELD);
        getFieldValuesFromDocs(inputDocs, processIdField);

        try {
            List<WorkflowDocument> workflowDocs = BiocodeService.getInstance().getWorkflowDocumentsForNames(workflowToDocument.keySet());
            for (WorkflowDocument workflowDoc : workflowDocs) {
                for (AnnotatedPluginDocument document : workflowToDocument.get(workflowDoc.getName())) {
                    Reaction cyclesequencingReaction = getSequencingReactionForDoc(workflowDoc, document);
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
                    result.put(document,
                            new TraceInfo(document.getName() + forwardSuffix, fwdPcrPrimer, revPcrPrimer,
                            seqPrimer, directionForDocs.get(document.getURN()),
                            String.valueOf(document.getFieldValue(processIdField)), workflowDoc.getWorkflow().getLocus())
                    );
                }

            }
            return result;
        } catch (DatabaseServiceException e) {
            throw new DocumentOperationException("Failed to retrieve workflows from LIMS: " + e.getMessage(), e);
        }
    }

    static Reaction getMostLikelyPcrReactionForSeqReaction(WorkflowDocument workflowDoc, Reaction cyclesequencingReaction) {
        List<Reaction> pcrReactions = workflowDoc.getReactions(Reaction.Type.PCR);
        if(pcrReactions.isEmpty()) {
            return null;
        }
        Reaction pcrReaction = null;
        for (Reaction candidate : pcrReactions) {
            boolean isBeforeSeq = candidate.getDate().before(cyclesequencingReaction.getDate());
            if(isBeforeSeq && (pcrReaction == null || candidate.getDate().after(pcrReaction.getDate()))) {
                pcrReaction = candidate;
            }
        }
        return pcrReaction;
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
                File dest = new File(submssionDir, f.getName() + filenameSuffix);
                if (!FileUtilities.renameToWithRetry(f, dest)) {
                    throw new DocumentOperationException("Failed to move file " + f.getAbsolutePath() + " to " + dest.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            throw new DocumentOperationException("Failed to export chromatograms: " + e.getMessage(), e);
        }
    }

    static File createTracesSpreadsheet(Collection<TraceInfo> traceEntries, File submssionDir, ProgressListener progressListener) throws DocumentOperationException {
        try {
            File templateFile = getTemplateSpreadsheet();
            File spreadsheetFile = new File(submssionDir, "data.xls");

            Workbook template = Workbook.getWorkbook(templateFile);
            WritableWorkbook spreadsheet = Workbook.createWorkbook(spreadsheetFile, template);
            WritableSheet sheet = spreadsheet.getSheet(0);

            int rowIndex = 1;
            CompositeProgressListener compositeProgress = new CompositeProgressListener(progressListener, traceEntries.size());
            for (TraceInfo entry : traceEntries) {
                compositeProgress.beginSubtask();
                sheet.addCell(new Label(0, rowIndex, entry.filename));
                sheet.addCell(new Label(2, rowIndex, entry.forwardPcrPrimer));
                sheet.addCell(new Label(3, rowIndex, entry.reversePcrPrimer));
                sheet.addCell(new Label(4, rowIndex, entry.sequencingPrimer));
                sheet.addCell(new Label(5, rowIndex, entry.forwardNotReverse ? "F" : "R"));
                sheet.addCell(new Label(6, rowIndex, entry.processId));
                sheet.addCell(new Label(9, rowIndex, entry.locus));
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
