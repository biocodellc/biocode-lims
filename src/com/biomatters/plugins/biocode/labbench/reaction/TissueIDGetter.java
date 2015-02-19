package com.biomatters.plugins.biocode.labbench.reaction;

/**
 * TODO: Write some javadoc
 *
 * @author Gen Li
 *         Created on 19/02/15 2:57 PM
 */
public class TissueIDGetter implements ReactionAttributeGetter<String> {
    @Override
    public String get(Reaction reaction) {
        return reaction.getOptions().getValueAsString(ExtractionOptions.TISSUE_ID);
    }

    @Override
    public String getAttributeName() {
        return "Tissue ID";
    }
}
