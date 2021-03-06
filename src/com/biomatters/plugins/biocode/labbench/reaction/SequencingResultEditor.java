package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import org.virion.jam.util.SimpleListener;

import java.util.List;

/**
 * @author Matthew Cheung
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
    void addSequences(SimpleListener finishedListener) {
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
