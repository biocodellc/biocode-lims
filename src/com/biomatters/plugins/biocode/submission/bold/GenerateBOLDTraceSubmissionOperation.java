package com.biomatters.plugins.biocode.submission.bold;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideGraphSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.assembler.BatchChromatogramExportOperation;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        File submissionDir;
        try {
            File workingDirectory = FileUtilities.createTempDir(true);
            submissionDir = new File(workingDirectory, options.getSubmissionName());
        } catch (IOException e) {
            throw new DocumentOperationException("Failed to create temporary directory: " + e.getMessage(), e);
        }

        CompositeProgressListener compositeProgress = new CompositeProgressListener(progressListener, 0.1, 0.1, 0.25, 0.25, 0.3);

        compositeProgress.beginSubtask("Examining input files");
        // todo
        // Get list of primers used
        // Mapping options?  Or only allow one set of primers?  User defined or fetched from LIMS?
        Map<AnnotatedPluginDocument, String> forwardDocsAndPrimerInfo = new HashMap<AnnotatedPluginDocument, String>();
        Map<AnnotatedPluginDocument, String> reverseDocsAndPrimerInfo = new HashMap<AnnotatedPluginDocument, String>();
        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            Object isForward = annotatedDocument.getFieldValue(BiocodeUtilities.IS_FORWARD_FIELD);
            if(isForward == null) {
                throw new DocumentOperationException(annotatedDocument.getName() + " has no direction.  All chromatograms must have a direction set");
            } else if(Boolean.TRUE.equals(isForward)) {
                forwardDocsAndPrimerInfo.put(annotatedDocument, null);
            } else {
                reverseDocsAndPrimerInfo.put(annotatedDocument, null);
            }
        }

        // Check file names, if duplicates between forward and reverse then ask for suffix.  If duplicates throw exception
        // Also check for read direction being set

        compositeProgress.beginSubtask("Writing data.xls spreadsheet");
        createTracesSpreadsheet(compositeProgress, submissionDir);

        compositeProgress.beginSubtask("Exporting forward traces");
        exportTracesToFolder(new ArrayList<AnnotatedPluginDocument>(forwardDocsAndPrimerInfo.keySet()), options.getForwardSuffix(), submissionDir, compositeProgress, traceExporter);
        compositeProgress.beginSubtask("Exporting reverse traces");
        exportTracesToFolder(new ArrayList<AnnotatedPluginDocument>(reverseDocsAndPrimerInfo.keySet()), options.getReverseSuffix(), submissionDir, compositeProgress, traceExporter);

        try {
            compositeProgress.beginSubtask("Zipping submission folder");
            FileUtilities.zip(submissionDir, options.getZipFile(), true, compositeProgress);
        } catch (IOException e) {
            throw new DocumentOperationException("Failed to create submission package: " + e.getMessage(), e);
        }
    }

    private static void exportTracesToFolder(List<AnnotatedPluginDocument> tracesToExport, String filenameSuffix, File tempDir, CompositeProgressListener compositeProgress, DocumentOperation traceExporter) throws DocumentOperationException {
        if(filenameSuffix == null) {
            filenameSuffix = "";
        } else {
            filenameSuffix = filenameSuffix.trim();
        }
        try {
            File tempTracesDir = FileUtilities.createTempDir(true);
            Options exportOptions = traceExporter.getOptions(tracesToExport);
            exportOptions.setValue(BatchChromatogramExportOperation.EXPORT_FOLDER, tempTracesDir.getAbsolutePath());
            traceExporter.performOperation(tracesToExport, compositeProgress, exportOptions);
            File[] filesInDir = tempTracesDir.listFiles();
            if(filesInDir == null) {
                throw new DocumentOperationException("Could not list files in " + tempTracesDir.getAbsolutePath());
            }
            for (File f : filesInDir) {
                File dest = new File(tempDir, f.getName() + filenameSuffix);
                if(!f.renameTo(dest)) {
                    throw new DocumentOperationException("Failed to move file " + f.getAbsolutePath() + " to " + dest.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            throw new DocumentOperationException("Failed to export chromatograms: " + e.getMessage(), e);
        }
    }

    private static File createTracesSpreadsheet(ProgressListener progressListener, File tempDir) throws DocumentOperationException {
        try {
            // Create copy of xls
            File spreadsheet = new File(tempDir, "data.xls");
            File templateFile = FileUtilities.getResourceForClass(GenerateBOLDTraceSubmissionOperation.class, "data.xls");
            FileUtilities.copyFile(templateFile, spreadsheet, FileUtilities.TargetExistsAction.Fail, progressListener);

            // Edit copy
            // todo

            return spreadsheet;
        } catch (IOException e) {
            throw new DocumentOperationException("Failed to export package: " + e.getMessage(), e);
        }
    }
}
