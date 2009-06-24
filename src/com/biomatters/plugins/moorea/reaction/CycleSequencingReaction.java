package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;

import java.util.List;
import java.util.Collections;
import java.awt.*;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 24/06/2009 6:02:38 PM
 */
public class CycleSequencingReaction extends Reaction{
    private Options options;

    public Type getType() {
        return Type.CycleSequencing;
    }

    public Options getOptions() {
        if(options == null) {
            options = new CycleSequencingOptions(this.getClass());
        }
        return options;
    }

    public List<DocumentField> getDefaultDisplayedFields() {
        return Collections.EMPTY_LIST;
    }

    public Color _getBackgroundColor() {
        return Color.white;
    }

    public String areReactionsValid(List<Reaction> reactions) {
        return null;
    }

    
}
