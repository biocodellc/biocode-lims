package com.biomatters.plugins.biocode.labbench.reaction;

/**
 *
 * @author Gen Li
 *         Created on 10/02/15 3:40 PM
 */
public class ExtractionIDGetter implements ReactionAttributeGetter<String> {
    public String get(Reaction reaction ) {
        return reaction.getExtractionId();
    }

    public String getAttributeName() {
        return "Extraction ID";
    }
}
