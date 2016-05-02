package com.biomatters.plugins.biocode.assembler.verify;

import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceListDocument;
import com.biomatters.geneious.publicapi.documents.sequence.DefaultSequenceListDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultSequenceDocument;

import java.util.*;

import jebl.util.ProgressListener;
import jebl.util.CompositeProgressListener;

/**
 * @author Steve
 */
public class FillInTaxonomyOperation extends DocumentOperation {

    public GeneiousActionOptions getActionOptions() {
        return new GeneiousActionOptions("Fill in Taxonomy (from NCBI)").setInMainToolbar(true);
    }

    public String getHelp() {
        return null;
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[]{new DocumentSelectionSignature(DefaultSequenceListDocument.class, 1, Integer.MAX_VALUE)};
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options) throws DocumentOperationException {
        CompositeProgressListener composite = new CompositeProgressListener(progressListener, 0.25, 0.5, 0.25);

        Set<String> taxonomies = new LinkedHashSet<String>();
        CompositeProgressListener composite2 = new CompositeProgressListener(composite, annotatedDocuments.length);
        for(AnnotatedPluginDocument doc : annotatedDocuments) {
            composite2.beginSubtask("Processing documents");
            DefaultSequenceListDocument sequenceList = (DefaultSequenceListDocument)doc.getDocument();
            int totalSize = sequenceList.getNucleotideSequences().size() + sequenceList.getAminoAcidSequences().size();
            for (int i = 0; i < sequenceList.getNucleotideSequences().size(); i++) {
                SequenceDocument sequenceDoc = sequenceList.getNucleotideSequences().get(i);
                composite2.setProgress(((double)i)/ totalSize);
                Object taxonomyValue = sequenceDoc.getFieldValue(DocumentField.TAXONOMY_FIELD.getCode());
                if (taxonomyValue != null) {
                    taxonomies.add(taxonomyValue.toString());
                }
            }
            for (int i = 0; i < sequenceList.getAminoAcidSequences().size(); i++) {
                SequenceDocument sequenceDoc = sequenceList.getAminoAcidSequences().get(i);
                composite2.setProgress(((double) i+sequenceList.getNucleotideSequences().size()) / totalSize);
                Object taxonomyValue = sequenceDoc.getFieldValue(DocumentField.TAXONOMY_FIELD.getCode());
                if (taxonomyValue != null) {
                    taxonomies.add(taxonomyValue.toString());
                }
            }
        }

        composite.beginNextSubtask("Downloading taxonomies from NCBI");
        final Map<String,BiocodeTaxon> fullTaxonomies = VerifyTaxonomyOperation.fillInTaxonomyFromNcbi(taxonomies, composite);
        composite.beginNextSubtask("Processing documents");

        for(AnnotatedPluginDocument doc : annotatedDocuments) {
            DefaultSequenceListDocument sequenceList = (DefaultSequenceListDocument)doc.getDocument();
            DefaultSequenceListDocument newSequenceList = sequenceList.createNewDocumentByTransformingSequences(null, new SequenceDocument.Transformer() {
                public SequenceDocument transformSequence(SequenceDocument sequence) throws DocumentOperationException {
                    if (sequence instanceof DefaultSequenceDocument) {
                        BiocodeTaxon fullTaxonomy = fullTaxonomies.get(sequence.getFieldValue(DocumentField.TAXONOMY_FIELD.getCode()));
                        ((DefaultSequenceDocument) sequence).setFieldValue(DocumentField.TAXONOMY_FIELD.getCode(), fullTaxonomy != null ? fullTaxonomy.toString() : null);
                    }
                    return sequence;
                }
            }, composite);
            sequenceList.replaceContents(newSequenceList);
            doc.saveDocument();
        }
        return Collections.EMPTY_LIST;
    }
}
