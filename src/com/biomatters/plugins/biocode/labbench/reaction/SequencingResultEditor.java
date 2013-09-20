package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import java.util.List;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 19/09/13 4:23 PM
 */
public class SequencingResultEditor extends SequencesEditor<NucleotideSequenceDocument> {
    public SequencingResultEditor(final List<NucleotideSequenceDocument> results, String reactionName) {
        super(results, reactionName);
    }

    @Override
    List<NucleotideSequenceDocument> createSequences(List<NucleotideSequenceDocument> results) {
        return results;
    }

    @Override
    boolean canEdit() {
        return false;
    }

    @Override
    void importSequences() {
    }

    @Override
    void addSequences() {
    }

    @Override
    List<NucleotideSequenceDocument> removeSequences() {
        return null;
    }

    @Override
    String getDialogName() {
        return "Sequencing Results";
    }
}
