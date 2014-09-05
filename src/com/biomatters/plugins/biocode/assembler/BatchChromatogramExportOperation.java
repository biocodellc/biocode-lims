package com.biomatters.plugins.biocode.assembler;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideGraphSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.assembler.lims.InputType;
import com.biomatters.plugins.biocode.assembler.lims.MarkInLimsUtilities;
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

    public static final String EXPORT_FOLDER = "exportTo";

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
                ),
                DocumentSelectionSignature.forNucleotideAlignments(1, Integer.MAX_VALUE)
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

        String labelText = null;
        InputType type = InputType.determineInputType(documents);
        if(type == InputType.CONSENSUS_SEQS || type == InputType.ALIGNMENT_OF_CONSENSUS) {
            labelText = "Source chromatograms will be exported instead of the selected consensus sequences.";
        } else if(type == InputType.CONTIGS) {
            labelText = "Source chromatograms will be exported instead of the selected contig assemblies.";
        } else if(type == InputType.MIXED) {
            labelText = "Source chromatograms will be exported instead of the selected documents.";
        }

        if(labelText != null) {
            options.addLabel("<html><b>Note</b>:<i>" + labelText + "</i></html>");
            options.addLabel("");
        }

        Options.FileSelectionOption folderOption = options.addFileSelectionOption(EXPORT_FOLDER, "Export to Folder:", "");
        folderOption.setSelectionType(JFileChooser.DIRECTORIES_ONLY);
        return options;
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options) throws DocumentOperationException {
        DocumentFileExporter abiExporter = PluginUtilities.getDocumentFileExporter("abi");
        DocumentFileExporter scfExporter = PluginUtilities.getDocumentFileExporter("scf");
        File directory = new File(options.getValueAsString(EXPORT_FOLDER));
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new DocumentOperationException("Export failed because the folder " + directory + " could not be created");
            }
        }
        CompositeProgressListener compositeProgress = new CompositeProgressListener(progressListener, annotatedDocuments.length);
        compositeProgress.setMessage("Exporting chromatograms");
        List<AnnotatedPluginDocument> consensusWithoutLinks = new ArrayList<AnnotatedPluginDocument>();
        List<String> namesOfFailedSeqs = new ArrayList<String>();
        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            if (compositeProgress.isCanceled()) {
                return null;
            }
            compositeProgress.beginSubtask();
            InputType inputType = InputType.determineInputType(new AnnotatedPluginDocument[]{annotatedDocument});

            List<AnnotatedPluginDocument> assemblies = new ArrayList<AnnotatedPluginDocument>();
            if(inputType == InputType.ALIGNMENT_OF_CONSENSUS) {
                PluginDocument pluginDoc = annotatedDocument.getDocument();
                for (AnnotatedPluginDocument consensus : ((SequenceAlignmentDocument) pluginDoc).getReferencedDocuments()) {
                    if(consensus != null) {
                        AnnotatedPluginDocument assembly = MarkInLimsUtilities.getAssemblyFromConsensus(consensus);
                        if(assembly != null) {
                            assemblies.add(assembly);
                        } else {
                            consensusWithoutLinks.add(consensus);
                        }
                    }
                }
            } else if(inputType == InputType.CONSENSUS_SEQS) {
                AnnotatedPluginDocument assembly = MarkInLimsUtilities.getAssemblyFromConsensus(annotatedDocument);
                if(assembly != null) {
                    assemblies.add(assembly);
                } else {
                    consensusWithoutLinks.add(annotatedDocument);
                }
            } else if(inputType == InputType.CONTIGS) {
                assemblies.add(annotatedDocument);
            } else if (inputType == InputType.TRACES) {
                if(!exportChromatSeq(abiExporter, scfExporter, directory, annotatedDocument)) {
                    namesOfFailedSeqs.add(annotatedDocument.getName());
                }
            } else {
                namesOfFailedSeqs.add(annotatedDocument.getName());
            }

            for (AnnotatedPluginDocument assembly : assemblies) {
                PluginDocument pluginDoc = assembly.getDocument();
                SequenceAlignmentDocument alignment = (SequenceAlignmentDocument) pluginDoc;
                for (int i=0; i< alignment.getNumberOfSequences(); i++) {
                    AnnotatedPluginDocument refDoc = alignment.getReferencedDocument(i);
                    if(refDoc != null && NucleotideGraphSequenceDocument.class.isAssignableFrom(refDoc.getDocumentClass())) {
                        if(!exportChromatSeq(abiExporter, scfExporter, directory, refDoc)) {
                            namesOfFailedSeqs.add(refDoc.getName());
                        }
                    } else {
                        namesOfFailedSeqs.add(alignment.getSequence(i).getName());
                    }
                }
            }
        }
        if (!namesOfFailedSeqs.isEmpty() || !consensusWithoutLinks.isEmpty()) {
            throwExceptionForFailedDocuments(namesOfFailedSeqs, consensusWithoutLinks);
        }
        return null;
    }

    /**
     * Exports a chromatogram
     *
     * @param abiExporter Used to export ab1 format
     * @param scfExporter Used to export scf format
     * @param directory Directory to export to
     * @param annotatedDocument The document to export
     * @return true if exported, false if failure
     */
    private boolean exportChromatSeq(DocumentFileExporter abiExporter, DocumentFileExporter scfExporter, File directory, AnnotatedPluginDocument annotatedDocument) throws DocumentOperationException.Canceled {
        try {
            File file = getExportFile(directory, annotatedDocument, abiExporter);
            AnnotatedPluginDocument[] documentArray = {annotatedDocument};
            Options exporterOptions = abiExporter.getOptions(documentArray);
            if(exporterOptions != null) {
                exporterOptions.setValue("exportMethod", "originalSource");
            }
            abiExporter.export(file, documentArray, ProgressListener.EMPTY, exporterOptions);
            formats.put(annotatedDocument, "ABI");
            names.put(annotatedDocument, file.getName());
            return true;
        } catch (IOException e) {
            //try scf below
        }
        try {
            File file = getExportFile(directory, annotatedDocument, scfExporter);
            scfExporter.export(file, new AnnotatedPluginDocument[] {annotatedDocument}, ProgressListener.EMPTY, null);
            formats.put(annotatedDocument, "SCF");
            names.put(annotatedDocument, file.getName());
            return true;
        } catch (IOException e) {
            //fail below
        }
        return false;
    }

    Map<AnnotatedPluginDocument, String> formats = new HashMap<AnnotatedPluginDocument, String>();
    Map<AnnotatedPluginDocument, String> names = new HashMap<AnnotatedPluginDocument, String>();

    public String getFormatUsedFor(AnnotatedPluginDocument document) {
        return formats.get(document);
    }

    public String getFileNameUsedFor(AnnotatedPluginDocument document) {
        return names.get(document);
    }

    private void throwExceptionForFailedDocuments(List<String> names, List<AnnotatedPluginDocument> consensusWithoutLinks) throws DocumentOperationException {
        List<String> consensusNames = new ArrayList<String>();
        for (AnnotatedPluginDocument consensusWithoutLink : consensusWithoutLinks) {
            consensusNames.add(consensusWithoutLink.getName());
        }

        StringBuilder message = new StringBuilder();
        if(!consensusNames.isEmpty()) {
            message.append("Source chromatograms could not be exported for the following ").
                    append(consensusNames.size()).append(" consensus sequences because they do not have a link back to an assembly:\n");
            message.append(StringUtilities.join("\n", consensusNames));
            if(!names.isEmpty()) {
                message.append("\n\n");
            }
        }

        if(!names.isEmpty()) {
            message.append("The following ").append(names.size()).append(
                    " chromatograms couldn't be exported because they didn't come from ab1 or SCF files or they were " +
                            "imported in an earlier version of ").append(
                    Geneious.getName()).append(":\n").append(StringUtilities.join("\n", names));
        }
        throw new DocumentOperationException(message.toString());
    }

    private File getExportFile(File directory, AnnotatedPluginDocument annotatedDocument, DocumentFileExporter exporter) throws DocumentOperationException.Canceled {
        String extension = exporter.getDefaultExtension();
        String fileName = BiocodeUtilities.getNiceExportedFilename(annotatedDocument.getName(), extension, null);

        File exportFile = new File(directory, fileName);
        if (exportFile.exists()) {
            if (!Dialogs.showContinueCancelDialog("The folder " + directory.getName() + " already contains a file named " +
                    exportFile.getName() + ". This file will be overwritten.", "Overwrite?", null, Dialogs.DialogIcon.WARNING)) {
                throw new DocumentOperationException.Canceled();
            }
        }
        return exportFile;
    }
}
