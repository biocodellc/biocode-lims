package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.plugin.DocumentFileImporter;
import com.biomatters.geneious.publicapi.plugin.DocumentImportException;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.DefaultSequenceListDocument;
import com.biomatters.geneious.publicapi.documents.sequence.AminoAcidSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.utilities.ProgressInputStream;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultAminoAcidSequence;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideSequence;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultSequenceDocument;

import java.io.*;
import java.util.List;
import java.util.ArrayList;

import jebl.util.ProgressListener;

/**
 * @author Steve
 */
public class BoldTsvImporter extends DocumentFileImporter{

    public String[] getPermissibleExtensions() {
        return new String[] {".tsv"};
    }

    public String getFileTypeDescription() {
        return "BOLD TSV file";
    }

    public AutoDetectStatus tentativeAutoDetect(File file, String fileContentsStart) {
        int firstLineIndex = fileContentsStart.indexOf('\n');
        if(firstLineIndex > 0) {
            if(fileContentsStart.substring(0, firstLineIndex).split("\t").length == 26) {
                return AutoDetectStatus.ACCEPT_FILE;
            }
        }
        return AutoDetectStatus.REJECT_FILE;
    }

    public void importDocuments(File file, ImportCallback callback, ProgressListener progressListener) throws IOException, DocumentImportException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new ProgressInputStream(progressListener, new FileInputStream(file), file.length())));
        try {
            String line;
            String[] headers = null;
            List<NucleotideSequenceDocument> sequences = new ArrayList<NucleotideSequenceDocument>();
            List<AminoAcidSequenceDocument> proteins = new ArrayList<AminoAcidSequenceDocument>();
            while((line = reader.readLine()) != null) {
                String[] data = line.split("\t");
                if(headers == null) {
                    headers = data;
                }
                else {
                    SequenceDocument sequenceDocument = getSequeceDocument(headers, data);
                    if(sequenceDocument != null) {
                        callback.addDocument(sequenceDocument);
                    }

                }
            }
            //callback.addDocument(proteins.size() > 0 ? DefaultSequenceListDocument.forBothSequenceTypes(sequences, proteins) : DefaultSequenceListDocument.forNucleotideSequences(sequences));
        } finally {
            reader.close();
        }
    }

    public static String getData(String key, String[] headers, String[] data) {
        for(int i=0; i < headers.length; i++) {
            if(headers[i].equals(key)) {
                return data[i];
            }
        }
        return null;
    }

    private static SequenceDocument getSequeceDocument(String[] headers, String[] data) {
        DefaultSequenceDocument sequenceDocument;
        String sequence = getData("nucraw", headers, data);
        if(sequence == null) {
            sequence = getData("nucleotides", headers, data);
        }
        if(sequence.contains("#")) {
            String aminoAcidSequence = getData("aminoraw", headers, data);
            if(aminoAcidSequence != null) {
                sequenceDocument = new DefaultAminoAcidSequence(getData("processid", headers, data), aminoAcidSequence.replace("-", ""));
            }
            else {
                return null;
            }
        }
        else {
            sequenceDocument = new DefaultNucleotideSequence(getData("processid", headers, data), sequence.replace("-", ""));
        }
        for (int i = 0; i < Math.min(data.length, headers.length); i++) {
            String key = headers[i];
            String value = data[i];
            sequenceDocument.setFieldValue(key, value);
            sequenceDocument.addDisplayableField(new DocumentField(key, key, key, String.class, false, false));
        }
        sequenceDocument.setFieldValue(DocumentField.TAXONOMY_FIELD.getCode(), getTaxonomy(data));
        sequenceDocument.addDisplayableField(DocumentField.TAXONOMY_FIELD);
        return sequenceDocument;
    }

    private static String getTaxonomy(String[] data) {
        String phylumName = data[8];
        String className = data[9];
        String orderName = data[10];

        phylumName = phylumName.substring(phylumName.indexOf(">")+1);
        className = className.substring(className.indexOf(">")+1);
        orderName = orderName.substring(orderName.indexOf(">")+1);
        return phylumName+";"+className+";"+orderName;
    }
}
