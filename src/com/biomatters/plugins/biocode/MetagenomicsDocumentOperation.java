package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseService;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.geneious.publicapi.databaseservice.SequenceSearchDatabaseService;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.implementations.EValue;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import jebl.util.ProgressListener;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Steve
 * Date: 5/04/13
 * Time: 7:55 PM
 * To change this template use File | Settings | File Templates.
 */
public class MetagenomicsDocumentOperation extends DocumentOperation {


    @Override
    public GeneiousActionOptions getActionOptions() {
        return new GeneiousActionOptions("Identify Taxonomies").setInMainToolbar(true);
    }

    @Override
    public String getHelp() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[]{new DocumentSelectionSignature(SequenceAlignmentDocument.class, 1, Integer.MAX_VALUE)};
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        DocumentOperation consensusOperation = PluginUtilities.getDocumentOperation("Generate_Consensus");
        return consensusOperation.getOptions(documents);
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(final AnnotatedPluginDocument[] annotatedDocuments, final ProgressListener progressListener, Options options) throws DocumentOperationException {
        DocumentOperation consensusOperation = PluginUtilities.getDocumentOperation("Generate_Consensus");

        List<AnnotatedPluginDocument> consensuses = new ArrayList<AnnotatedPluginDocument>();
        for (int i = 0, annotatedDocumentsLength = annotatedDocuments.length; i < annotatedDocumentsLength; i++) {
            progressListener.setMessage("Generating consensus "+(i+1)+" of "+annotatedDocuments.length);
            progressListener.setProgress(((double)i)/annotatedDocuments.length);
            AnnotatedPluginDocument alignment = annotatedDocuments[i];
            AnnotatedPluginDocument consensusDoc = consensusOperation.performOperation(new AnnotatedPluginDocument[]{alignment}, ProgressListener.EMPTY, options).get(0);
            consensusDoc.setName(""+i);
            consensuses.add(consensusDoc);
        }
        progressListener.setIndeterminateProgress();

        SequenceSearchDatabaseService blastService = (SequenceSearchDatabaseService)PluginUtilities.getGeneiousService("NCBI_nr");
        Options blastOptions = blastService.getSequenceSearchOptions("blastn");
        blastOptions.setValue("getHitAnnos", true);
        blastOptions.setValue("maxHits", 1);
        blastOptions.setValue("EXPECT", "10");
        final Map<String, MetagenomicsDocument.OTU> otuList = new HashMap<String, MetagenomicsDocument.OTU>();
        try {
            blastService.sequenceSearch(new SequenceSearchDatabaseService.SequenceSearchInput(new SequenceSelectionWithDocuments(consensuses), "blastn", 1, blastOptions, new RetrieveCallback() {
                @Override
                protected void _add(PluginDocument document, Map<String, Object> searchResultProperties) {
                    AnnotatedPluginDocument contig = annotatedDocuments[Integer.parseInt(""+document.getFieldValue("query"))];
                    if(contig == null) {
                        throw new RuntimeException("No contig!");
                    }
                    if(contig.getURN() == null) {
                        throw new RuntimeException("No URL!");
                    }
                    try {
                    otuList.put(contig.getURN().toString(), new MetagenomicsDocument.OTU(contig.getName(), contig.getURN(), "" + document.getFieldValue(DocumentField.TAXONOMY_FIELD.getCode()), document.getDescription(), (Integer) contig.getFieldValue(DocumentField.SEQUENCE_COUNT.getCode()), (EValue) document.getFieldValue("evalue")));
                    }
                    catch(Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                protected void _add(AnnotatedPluginDocument document, Map<String, Object> searchResultProperties) {
                    AnnotatedPluginDocument contig = getContig(annotatedDocuments, document.getFieldValue("query"));
                    otuList.put(contig.getURN().toString(), new MetagenomicsDocument.OTU(contig.getName(), contig.getURN(), "" + document.getFieldValue(DocumentField.TAXONOMY_FIELD.getCode()), "" + document.getFieldValue(DocumentField.DESCRIPTION_FIELD.getCode()), (Integer) contig.getFieldValue(DocumentField.SEQUENCE_COUNT.getCode()), (EValue) document.getFieldValue("evalue")));
                }

                @Override
                protected void _setMessage(String message) {
                    progressListener.setMessage(message);
                }

                @Override
                protected boolean _isCanceled() {
                    return progressListener.isCanceled();
                }
            }));
        } catch (DatabaseServiceException e) {
            throw new DocumentOperationException(e.getMessage(), e);
        }
        MetagenomicsDocument otuDoc = new MetagenomicsDocument();
        for(AnnotatedPluginDocument doc : annotatedDocuments) {
            MetagenomicsDocument.OTU otu = otuList.get(doc.getURN().toString());
            if(otu == null) {
                otu = new MetagenomicsDocument.OTU(doc.getName(), doc.getURN(), "NO HIT", "NO HIT",  (Integer) doc.getFieldValue(DocumentField.SEQUENCE_COUNT.getCode()), null);
            }
            otuDoc.addOTU(otu);
        }

        return Arrays.asList(DocumentUtilities.createAnnotatedPluginDocument(otuDoc));
    }

    private static AnnotatedPluginDocument getContig(AnnotatedPluginDocument[] contigs, Object queryName) {
        for(AnnotatedPluginDocument doc : contigs) {
            if(doc.getName().equals(queryName)) {
                return doc;
            }
        }
        return null;
    }
}
