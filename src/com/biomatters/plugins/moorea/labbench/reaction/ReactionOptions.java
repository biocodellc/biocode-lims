package com.biomatters.plugins.moorea.labbench.reaction;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import org.jdom.Element;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 14/07/2009 7:15:35 PM
 */
public abstract class ReactionOptions extends Options {
    public ReactionOptions() {
    }

    public ReactionOptions(Class cl) {
        super(cl);
    }

    public ReactionOptions(Class cl, String preferenceNameSuffix) {
        super(cl, preferenceNameSuffix);
    }

    public ReactionOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    public abstract boolean fieldIsFinal(String fieldCode);

    public abstract void refreshValuesFromCaches();

    public abstract Cocktail getCocktail();
}
