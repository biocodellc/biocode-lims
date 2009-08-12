package com.biomatters.plugins.moorea.assembler.verify;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperation;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.PluginUtilities;
import com.biomatters.plugins.moorea.MooreaUtilities;
import jebl.util.ProgressListener;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class VerifyTaxonomyOptions extends Options {

    private StringOption keywordsOption;

    public VerifyTaxonomyOptions(AnnotatedPluginDocument[] documents) throws DocumentOperationException {
        addLabel("Check hit definition for keywords (comma separated):");
        beginAlignHorizontally(null, false);
        keywordsOption = addStringOption("definitionKeywords", "", "COI");
        endAlignHorizontally();
        boolean isAlignments = SequenceAlignmentDocument.class.isAssignableFrom(documents[0].getDocumentClass());
        if (isAlignments) {
            //todo check sequence type
            Options consensusOptions = MooreaUtilities.getConsensusOptions(documents);
            if (consensusOptions == null) {
                throw new DocumentOperationException("The consensus plugin must be installed to be able to verify");
            }
            addChildOptions("consensus", "Consensus", null, consensusOptions);
        }
    }

    public List<AnnotatedPluginDocument> getQueries(AnnotatedPluginDocument[] annotatedDocuments) throws DocumentOperationException {
        List<AnnotatedPluginDocument> queries;
        if (SequenceAlignmentDocument.class.isAssignableFrom(annotatedDocuments[0].getDocumentClass())) {
            Options consensusOptions = getChildOptions().get("consensus");
            DocumentOperation consensusOperation = PluginUtilities.getDocumentOperation("Generate_Consensus");
            queries = consensusOperation.performOperation(annotatedDocuments, ProgressListener.EMPTY, consensusOptions);
            for (int i = 0; i < queries.size(); i++) {
                //we don't want " consensus sequence" appended to every query doc
                queries.get(i).setName(annotatedDocuments[i].getName());
            }
        } else {
            queries = Arrays.asList(annotatedDocuments);
        }
        return queries;
    }

    public String getKeywords() {
        return keywordsOption.getValue();
    }

    @Override
    protected JPanel createAdvancedPanel() {
        return null;
    }
}
