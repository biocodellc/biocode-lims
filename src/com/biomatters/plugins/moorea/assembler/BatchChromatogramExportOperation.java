package com.biomatters.plugins.moorea.assembler;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideGraphSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Richard
 * @version $Id$
 */
public class BatchChromatogramExportOperation extends DocumentOperation {

    public GeneiousActionOptions getActionOptions() {
        return new GeneiousActionOptions("Original Chromatograms...").setMainMenuLocation(GeneiousActionOptions.MainMenu.Export);
    }

    public String getHelp() {
        return "Select one or more chromatogram documents to export the original ab1/scf source.";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(
                        new DocumentSelectionSignature.DocumentSelectionSignatureAtom[] {
                                new DocumentSelectionSignature.DocumentSelectionSignatureAtom(NucleotideGraphSequenceDocument.class, 1, Integer.MAX_VALUE),
                                new DocumentSelectionSignature.DocumentSelectionSignatureAtom(PluginDocument.class, 0, Integer.MAX_VALUE)
                        }
                )
        };
    }

    @Override
    public String getUniqueId() {
        return "batchChromatogramsExport";
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        Options options = new Options(BatchChromatogramExportOperation.class);
        Options.FileSelectionOption folderOption = options.addFileSelectionOption("exportTo", "Export to Folder:", "");
        folderOption.setSelectionType(JFileChooser.DIRECTORIES_ONLY);
        return options;
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options) throws DocumentOperationException {
        DocumentFileExporter abiExporter = PluginUtilities.getDocumentFileExporter("abi");
        DocumentFileExporter scfExporter = PluginUtilities.getDocumentFileExporter("scf");
        File directory = new File(options.getValueAsString("exportTo"));
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new DocumentOperationException("Export failed because the folder " + directory + " could not be created");
            }
        }
        CompositeProgressListener compositeProgress = new CompositeProgressListener(progressListener, annotatedDocuments.length);
        compositeProgress.setMessage("Exporting chromatograms");
        List<AnnotatedPluginDocument> failures = new ArrayList<AnnotatedPluginDocument>();
        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            if (compositeProgress.isCanceled()) {
                return null;
            }
            compositeProgress.beginSubtask();
            if (NucleotideGraphSequenceDocument.class.isAssignableFrom(annotatedDocument.getDocumentClass())) {
                try {
                    File file = getExportFile(directory, annotatedDocument, abiExporter);
                    abiExporter.export(file, new AnnotatedPluginDocument[] {annotatedDocument}, ProgressListener.EMPTY, null);
                    formats.put(annotatedDocument, "ABI");
                    names.put(annotatedDocument, file.getName());
                    continue;
                } catch (IOException e) {
                    //try scf below
                }
                try {
                    File file = getExportFile(directory, annotatedDocument, scfExporter);
                    scfExporter.export(file, new AnnotatedPluginDocument[] {annotatedDocument}, ProgressListener.EMPTY, null);
                    formats.put(annotatedDocument, "SCF");
                    names.put(annotatedDocument, file.getName());
                    continue;
                } catch (IOException e) {
                    //fail below
                }
                failures.add(annotatedDocument);
            }
        }
        if (!failures.isEmpty()) {
            throwExceptionForFailedDocuments(failures);
        }
        return null;
    }

    Map<AnnotatedPluginDocument, String> formats = new HashMap<AnnotatedPluginDocument, String>();
    Map<AnnotatedPluginDocument, String> names = new HashMap<AnnotatedPluginDocument, String>();

    public String getFormatUsedFor(AnnotatedPluginDocument document) {
        return formats.get(document);
    }

    public String getFileNameUsedFor(AnnotatedPluginDocument document) {
        return names.get(document);
    }

    private void throwExceptionForFailedDocuments(List<AnnotatedPluginDocument> failures) throws DocumentOperationException {
        List<String> names = new ArrayList<String>();
        for (AnnotatedPluginDocument failure : failures) {
            names.add(failure.getName());
        }
        String message = "The following " + failures.size() + " chromatograms couldn't be exported because they didn't come " +
                "from ab1 or SCF files or they were imported in an earlier version of " + Geneious.getName() + ":\n\n" +
                StringUtilities.humanJoin(names);
        throw new DocumentOperationException(message);
    }

    private File getExportFile(File directory, AnnotatedPluginDocument annotatedDocument, DocumentFileExporter exporter) {
        String extensionUpper = exporter.getDefaultExtension().toUpperCase();
        String extensionLower = exporter.getDefaultExtension().toLowerCase();
        String fileName = annotatedDocument.getName();
        int extensionIndex = fileName.indexOf(extensionUpper);
        if (extensionIndex != -1) { //the extensions can end up in the middle of the name if renaming has occurred
            fileName = fileName.substring(0, extensionIndex) + fileName.substring(extensionIndex + extensionUpper.length());
        }
        extensionIndex = fileName.indexOf(extensionLower);
        if (extensionIndex != -1) { //the extensions can end up in the middle of the name if renaming has occurred
            fileName = fileName.substring(0, extensionIndex) + fileName.substring(extensionIndex + extensionLower.length());
        }
        fileName += exporter.getDefaultExtension();
        fileName = fileName.replace(' ', '_');
        return new File(directory, fileName);
    }
}
