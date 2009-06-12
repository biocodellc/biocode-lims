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
 *          Created on 12/06/2009 5:27:29 PM
 */
public class ExtractionReaction extends Reaction{

    public Options getOptions() {
        Options op = new Options(this.getClass());
        return op;
    }


    public List<DocumentField> getDefaultDisplayedFields() {
        return Collections.EMPTY_LIST;
    }


    public Color getBackgroundColor() {
        return Color.white;
    }
}
