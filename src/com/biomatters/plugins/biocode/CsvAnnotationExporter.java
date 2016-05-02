package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.*;
import com.biomatters.geneious.publicapi.plugin.DocumentFileExporter;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.Options;
import jebl.util.ProgressListener;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author Steve
 *          <p/>
 *          Created on 13/03/12 11:10 AM
 */


public class CsvAnnotationExporter extends DocumentFileExporter {

    List<String> allowedAnnotationTypes = Arrays.asList(
            SequenceAnnotation.TYPE_CDS
            ,SequenceAnnotation.TYPE_GENE
            //,SequenceAnnotation.TYPE_MRNA
            //,SequenceAnnotation.TYPE_EXON
            //,SequenceAnnotation.TYPE_TRNA
            //,SequenceAnnotation.TYPE_MISC_RNA
    );

    @Override
    public Options getOptions(AnnotatedPluginDocument[] documentsToExport) {
        Options o = new Options(this.getClass());
        o.addBooleanOption("justGeneAndCds", "Just Gene and CDS annotations", true);
        return o;
    }

    @Override
    public String getFileTypeDescription() {
        return "Annotations (as CSV)";
    }

    @Override
    public String getDefaultExtension() {
        return "csv";
    }

    @Override
    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                DocumentSelectionSignature.forNucleotideAndProteinSequences(0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, true)
        };
    }

    @Override
    public void export(final File file, AnnotatedPluginDocument[] documents, ProgressListener progressListener, Options options) throws IOException {
        final boolean filterAnnotations = (Boolean)options.getValue("justGeneAndCds");
        final Set<String> headers = new LinkedHashSet<String>();
        iterateThroughAllSequences(documents, new SequenceRunnable() {
            @Override
            public void run(SequenceDocument document) {
                for (SequenceAnnotation annotation : document.getSequenceAnnotations()) {
                    if(filterAnnotations && !allowedAnnotationTypes.contains(annotation.getType())) {
                        continue;
                    }
                    for (SequenceAnnotationQualifier qualifier : annotation.getQualifiers()) {
                        headers.add(qualifier.getName());
                    }
                }
            }
        });
        final List<String> headerList = new ArrayList<String>(headers);
        headerList.add(0, "Name");
        headerList.add(1, "Type");
        headerList.add(2, "Direction");
        headerList.add(3, "Minimum");
        headerList.add(4, "Maximum");
        final PrintWriter out = new PrintWriter(new FileWriter(file, true)); 
        out.println(getCsvLine(headerList));
        iterateThroughAllSequences(documents, new SequenceRunnable() {
            @Override
            public void run(SequenceDocument document) throws IOException {
                exportAnnotations(out, headerList, document, filterAnnotations);
            }
        });
        out.close();
    }

    private void iterateThroughAllSequences(AnnotatedPluginDocument[] documents, SequenceRunnable runnable) throws IOException {
        for(AnnotatedPluginDocument doc : documents) {
            PluginDocument pluginDocument = doc.getDocumentOrThrow(IOException.class);
            if(pluginDocument instanceof SequenceDocument) {
                runnable.run((SequenceDocument)pluginDocument);
            }
            else if(pluginDocument instanceof SequenceListDocument) {
                SequenceListDocument sequenceListDocument = (SequenceListDocument)pluginDocument;
                for(SequenceDocument document : sequenceListDocument.getNucleotideSequences()) {
                    runnable.run(document);
                }
                for(SequenceDocument document : sequenceListDocument.getNucleotideSequences()) {
                    runnable.run(document);
                }
            }
            else if(pluginDocument instanceof SequenceAlignmentDocument) {
                for(SequenceDocument sequence : ((SequenceAlignmentDocument)pluginDocument).getSequences()) {
                    runnable.run(sequence);
                }
            }
        }
    }

    private void exportAnnotations(PrintWriter out, List<String> headers, SequenceDocument document, boolean filterAnnotations) throws IOException{
        for(SequenceAnnotation annotation : document.getSequenceAnnotations()) {
            if(filterAnnotations && !allowedAnnotationTypes.contains(annotation.getType())) {
                continue;
            }
            for(SequenceAnnotationInterval interval : annotation.getIntervals()) {
                exportAnnotationInterval(out, annotation, interval, headers);
            }
        }
    }

    private void exportAnnotationInterval(PrintWriter out, SequenceAnnotation annotation, SequenceAnnotationInterval interval, List<String> headers) throws IOException{
        List<String> elements = new ArrayList<String>();
        for(String s : headers) {
            if(s.equals("Name")) {
                elements.add(annotation.getName());        
            }
            else if(s.equals("Type")) {
                elements.add(annotation.getType());    
            }
            else if(s.equals("Direction")) {
                elements.add(interval.getDirection().toString());    
            }
            else if(s.equals("Minimum")) {
                elements.add(""+interval.getMinimumIndex());    
            }
            else if(s.equals("Maximum")) {
                elements.add(""+interval.getMaximumIndex());    
            }
            else {
                String qualifierValue = annotation.getQualifierValue(s);
                elements.add(qualifierValue == null ? "" : qualifierValue);
            }
        }
        out.println(getCsvLine(elements));
    }
    
    private String getCsvLine(List<String> elements) {
        StringBuilder builder = new StringBuilder();
        boolean firstTime = true;
        for(String s : elements) {
            if(firstTime) {
                firstTime = false;
            }
            else {
                builder.append(", ");
            }
            if(s.matches("^(.*\\s+.*)+$")) {
                builder.append("\""+s+"\"");    
            }
            else {
                builder.append(s);
            }
        }
        return builder.toString();
    }


    private static abstract class SequenceRunnable {
        public abstract void run(SequenceDocument document) throws IOException; 
    }
}
