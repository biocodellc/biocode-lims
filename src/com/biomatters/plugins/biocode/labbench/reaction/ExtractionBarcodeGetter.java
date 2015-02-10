package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.plugins.biocode.BiocodeUtilities;

/**
 *
 * @author Gen Li
 *         Created on 10/02/15 3:40 PM
 */
public class ExtractionBarcodeGetter implements ReactionAttributeGetter {
    public String get(Reaction reaction) {
        return reaction.getOptions().getValueAsString(BiocodeUtilities.EXTRACTION_BARCODE_FIELD.getCode());
    }

    public String getAttributeName() {
        return "Extraction barcode";
    }
}
