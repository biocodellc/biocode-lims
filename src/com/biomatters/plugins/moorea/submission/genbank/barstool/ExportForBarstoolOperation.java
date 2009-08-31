package com.biomatters.plugins.moorea.submission.genbank.barstool;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideGraphSequence;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideSequence;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.geneious.publicapi.utilities.GeneralUtilities;
import com.biomatters.plugins.moorea.MooreaPlugin;
import com.biomatters.plugins.moorea.MooreaUtilities;
import com.biomatters.plugins.moorea.assembler.BatchChromatogramExportOperation;
import com.biomatters.plugins.moorea.assembler.SetReadDirectionOperation;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Richard
 * @version $Id$
 */
public class ExportForBarstoolOperation extends DocumentOperation {

    public GeneiousActionOptions getActionOptions() {
        GeneiousActionOptions geneiousActionOptions = new GeneiousActionOptions("Export for BarSTool Submission...")
                .setInPopupMenu(true, 0.7);
        return GeneiousActionOptions.createSubmenuActionOptions(MooreaPlugin.getSuperBiocodeAction(), geneiousActionOptions);
    }

    public String getHelp() {
        return ""; //todo
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(SequenceAlignmentDocument.class, 1, Integer.MAX_VALUE)
        };
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        return new ExportForBarstoolOptions(documents);
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] docs, ProgressListener progressListener, Options o) throws DocumentOperationException {
        ExportForBarstoolOptions options = (ExportForBarstoolOptions) o;
        CompositeProgressListener progress = new CompositeProgressListener(progressListener, 5);

        Map<AnnotatedPluginDocument, String> contigDocumentsMap = getContigDocuments(docs);
        List<AnnotatedPluginDocument> contigDocuments = new ArrayList<AnnotatedPluginDocument>(contigDocumentsMap.keySet());
        String noReadDirectionValue = getNoReadDirectionValue(contigDocumentsMap);

        File tracesFolder = new File(options.getFolder(), options.getTracesFolderName());
        if (!tracesFolder.exists()) {
            if (!tracesFolder.mkdirs()) {
                throw new DocumentOperationException("The folder " + tracesFolder + " doesn't exist and it couldn't be created. Make sure you have sufficient permissions to access the folder.");
            }
        }
        if (!tracesFolder.isDirectory()) {
            throw new DocumentOperationException("The selected file is not a folder. Please select a folder to export to.");
        }
        if (!tracesFolder.canWrite()) {
            throw new DocumentOperationException("The folder " + tracesFolder + " cannot be written to. Make sure you have sufficient permissions to access the folder.");
        }
        if (tracesFolder.list().length != 0) {
            //todo prompt to replace
            for (File file : tracesFolder.listFiles()) {
                if (!file.delete()) {
                    throw new DocumentOperationException("The file " + file + " already exists and could not be deleted from the traces directory");
                }
            }
        }
        

        progress.beginSubtask("Exporting traces");
        Map<AnnotatedPluginDocument, AnnotatedPluginDocument> traceDocsMap = new HashMap<AnnotatedPluginDocument, AnnotatedPluginDocument>();
        for (AnnotatedPluginDocument contigDoc : contigDocuments) {
            SequenceAlignmentDocument contig = (SequenceAlignmentDocument)contigDoc.getDocument();
            for (int i = 0; i < contig.getNumberOfSequences(); i ++) {
                if (i == contig.getContigReferenceSequenceIndex()) continue;
                AnnotatedPluginDocument referencedDoc = contig.getReferencedDocument(i);
                if (referencedDoc == null) {
                    throw new DocumentOperationException("The contig " + contigDoc.getName() + " is missing a reference for sequence " + contig.getSequence(i).getName() + ".");
                }
                if (!DefaultNucleotideGraphSequence.class.isAssignableFrom(referencedDoc.getDocumentClass())) {
                    throw new DocumentOperationException("The contig " + contigDoc.getName() + " references a sequence which is not a chromatogram: " + contig.getSequence(i).getName() + ".");
                }
                traceDocsMap.put(referencedDoc, contigDoc);
            }
        }
        List<AnnotatedPluginDocument> traceDocs = new ArrayList<AnnotatedPluginDocument>(traceDocsMap.keySet());
        BatchChromatogramExportOperation chromatogramExportOperation = new BatchChromatogramExportOperation();
        Options chromatogramExportOptions = chromatogramExportOperation.getOptions(traceDocs);
        chromatogramExportOptions.setValue("exportTo", tracesFolder.toString());
        chromatogramExportOperation.performOperation(traceDocs, progress, chromatogramExportOptions);

        ZipOutputStream out = null;
        try {
            File zipFile = new File(options.getFolder(), options.getSubmissionName() + "_traces.zip");
            out = new ZipOutputStream(new FileOutputStream(zipFile));
            zipDirectory(tracesFolder.getParentFile(), tracesFolder, out);
        } catch (IOException e) {
            throw new DocumentOperationException("Failed to zip traces: " + e.getMessage(), e);
        } finally {
            if (out != null) {
                GeneralUtilities.attemptClose(out);
            }
        }
        FileUtilities.deleteDirectory(tracesFolder, progress);

        //todo check for existing files

        progress.beginSubtask("Exporting consensus sequences");
        if (exportConsensusSequences(contigDocumentsMap, options, progress)) return null;

        //todo export protein fasta?

        try {
            progress.beginSubtask("Generating primer table");
            TabDelimitedExport.export(new File(options.getFolder(), options.getSubmissionName() + "_primers.txt"),
                    new PrimerExportTableModel(contigDocuments, options));

            progress.beginSubtask("Generating source modifiers table");
            TabDelimitedExport.export(new File(options.getFolder(), options.getSubmissionName() + "_source.txt"),
                    new SourceExportTableModel(contigDocuments, options));

            progress.beginSubtask("Generating trace information table");
            TabDelimitedExport.export(new File(options.getFolder(), options.getSubmissionName() + "_traces.txt"),
                    new TraceExportTableModel(traceDocsMap, options, chromatogramExportOperation, noReadDirectionValue));
        } catch (IOException e) {
            throw new DocumentOperationException("Tab delimited export failed: " + e.getMessage(), e);
        }
        return null;
    }

    private static String getNoReadDirectionValue(Map<AnnotatedPluginDocument, String> contigDocumentsMap) throws DocumentOperationException {
        String noReadDirectionValue = "N";
        for (AnnotatedPluginDocument contigDoc : contigDocumentsMap.keySet()) {
            SequenceAlignmentDocument contig = (SequenceAlignmentDocument) contigDoc.getDocument();
            for (int i = 0; i < contig.getNumberOfSequences(); i ++) {
                if (i == contig.getContigReferenceSequenceIndex()) continue;
                AnnotatedPluginDocument traceDoc = contig.getReferencedDocument(i);
                if (traceDoc == null) {
                    throw new DocumentOperationException("The contig " + contigDoc.getName() + " is missing a reference for sequence " + contig.getSequence(i).getName() + ".");
                }
                Object isForwardValue = traceDoc.getFieldValue(SetReadDirectionOperation.IS_FORWARD_FIELD);
                if (isForwardValue == null) {
                    continue;
                }
                if ((Boolean)isForwardValue) {
                    if (noReadDirectionValue.equals("F")) {
                        noReadDirectionValue = "N"; //both forward and reverse marked
                        break;
                    } else {
                        noReadDirectionValue = "R";
                    }
                } else {
                    if (noReadDirectionValue.equals("R")) {
                        noReadDirectionValue = "N"; //both forward and reverse marked
                        break;
                    } else {
                        noReadDirectionValue = "F";
                    }
                }
            }
        }
        return noReadDirectionValue;
    }

    private static Map<AnnotatedPluginDocument, String> getContigDocuments(AnnotatedPluginDocument[] docs) throws DocumentOperationException {
        Map<AnnotatedPluginDocument, String> contigDocumentsMap = new HashMap<AnnotatedPluginDocument, String>();
        for (AnnotatedPluginDocument document : docs) {
            if (MooreaUtilities.isAlignmentOfContigs(document)) {
                SequenceAlignmentDocument alignment = (SequenceAlignmentDocument)document.getDocument();
                for (int i = 0; i < alignment.getNumberOfSequences(); i ++) {
                    if (i == alignment.getContigReferenceSequenceIndex()) continue;
                    contigDocumentsMap.put(alignment.getReferencedDocument(i), alignment.getSequence(i).getSequenceString());
                }
            } else {
                contigDocumentsMap.put(document, null);
            }
        }
        return contigDocumentsMap;
    }

    /**
     * export fasta consensus sequences in format ">sequence_ID [organism=Genus species]"
     *
     * @return true if canceled, false otherwise
     */
    private static boolean exportConsensusSequences(Map<AnnotatedPluginDocument, String> contigDocumentsMap, ExportForBarstoolOptions options, CompositeProgressListener progress) throws DocumentOperationException {
        List<AnnotatedPluginDocument> consensusDocs = new ArrayList<AnnotatedPluginDocument>();
        for (Map.Entry<AnnotatedPluginDocument, String> contigDocEntry : contigDocumentsMap.entrySet()) {
            AnnotatedPluginDocument contigDoc = contigDocEntry.getKey();
            AnnotatedPluginDocument consensusDoc;
            if (contigDocEntry.getValue() != null) {
                SequenceDocument seq = new DefaultNucleotideSequence("seq", contigDocEntry.getValue());
                consensusDoc = DocumentUtilities.createAnnotatedPluginDocument(seq);
            } else {
                consensusDoc = MooreaUtilities.getConsensusSequence(contigDoc, options.getConsensusOptions());
                if (consensusDoc == null) {
                    throw new RuntimeException("consensus shouldn't be null here");
                } else if (SequenceDocument.class.isAssignableFrom(contigDoc.getDocumentClass())) {
                    consensusDoc = DocumentUtilities.duplicateDocument(consensusDoc, true);
                }
            }

            consensusDoc.setName(options.getSequenceId(contigDoc));
            consensusDoc.setFieldValue(DocumentField.DESCRIPTION_FIELD, "[organism=" + options.getOrganismName(contigDoc) + "]");
            consensusDoc.save();
            consensusDocs.add(consensusDoc);
        }
        if (progress.isCanceled()) return true;
        DocumentFileExporter fastaExporter = PluginUtilities.getDocumentFileExporter("fasta");
        if (fastaExporter == null) {
            throw new DocumentOperationException("FASTA exporter couldn't be loaded. Make sure the FASTA plugin is enabled");
        }
        AnnotatedPluginDocument[] consensusDocsArray = consensusDocs.toArray(new AnnotatedPluginDocument[consensusDocs.size()]);
        Options fastaOptions = fastaExporter.getOptions(consensusDocsArray);
        try {
            fastaExporter.export(new File(options.getFolder(), options.getSubmissionName() + ".fasta"), consensusDocsArray, progress, fastaOptions);
        } catch (IOException e) {
            throw new DocumentOperationException("Failed to export consensus sequences: " + e.getMessage(), e);
        }
        return progress.isCanceled();
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }

    private static void zipDirectory(File rootDirectory, File directory, ZipOutputStream out) throws IOException {
        byte[] buf = new byte[1024];
        FileInputStream in = null;
        String relativeDirectory = rootDirectory.equals(directory)? "":
                directory.getPath().substring(rootDirectory.getPath().length() + 1) + File.separator;
        try {
            for (File file: directory.listFiles()) {
                if (file.getName().equals(".svn")) {
                    continue;
                }
                String path = relativeDirectory.replace('\\', '/') + file.getName();
                if(file.isDirectory())  {
                    String dirname = path + "/";
                    out.putNextEntry(new ZipEntry(dirname));
                    out.closeEntry();
                    zipDirectory(rootDirectory, file, out);
                } else {
                    in = new FileInputStream(file);
                    out.putNextEntry(new ZipEntry(path));

                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }

                    out.closeEntry();
                    in.close();
                }
            }
        } finally {
            GeneralUtilities.attemptClose(in);
        }
    }
}
