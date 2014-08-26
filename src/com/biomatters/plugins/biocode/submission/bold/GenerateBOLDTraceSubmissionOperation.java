package com.biomatters.plugins.biocode.submission.bold;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideGraphSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import java.io.File;
import java.io.IOException;

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
        return "Select any number of reads documents to export them to a zip file ready for submission to BOLD.";
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
    public void performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options, SequenceSelection sequenceSelection, OperationCallback callback) throws DocumentOperationException {
        DocumentOperation traceExporter = PluginUtilities.getDocumentOperation("batchChromatogramsExport");
        if(traceExporter == null) {
            throw new DocumentOperationException("Could not find Batch Chromatogram Exporter.  Please try again.  " +
                    "If the problem persists contact " + BiocodePlugin.SUPPORT_EMAIL);
        }

        File tempDir;
        try {
            tempDir = FileUtilities.createTempDir(true);
        } catch (IOException e) {
            throw new DocumentOperationException("Failed to create temporary directory: " + e.getMessage(), e);
        }

        CompositeProgressListener compositeProgress = new CompositeProgressListener(progressListener, 0.1, 0.1, 0.5, 0.3);
        compositeProgress.beginSubtask("Examining input files");
        // Get list of primers used
        // Mapping options?  Or only allow one set of primers?  User defined or fetched from LIMS?


        // Check file names, if duplicates between forward and reverse then ask for suffix.  If duplicates throw exception
        // Also check for read direction being set


        compositeProgress.beginSubtask("Writing data.xls Spreadsheet");
        createTracesSpreadsheet(compositeProgress, tempDir);

        // Export all traces into a folder
        compositeProgress.beginSubtask("Exporting traces");

        compositeProgress.beginSubtask("Packing folder into zip");
        // Zip
//        FileUtilities.zip(tempDir, null, true, progressListener);
    }

    private static File createTracesSpreadsheet(ProgressListener progressListener, File tempDir) throws DocumentOperationException {
        try {
            // Create copy of xls
            File spreadsheet = new File(tempDir, "data.xls");
            File templateFile = FileUtilities.getResourceForClass(GenerateBOLDTraceSubmissionOperation.class, "data.xls");
            FileUtilities.copyFile(templateFile, spreadsheet, FileUtilities.TargetExistsAction.Fail, progressListener);
            // Edit copy
            return spreadsheet;
        } catch (IOException e) {
            throw new DocumentOperationException("Failed to export package: " + e.getMessage(), e);
        }
    }
}
