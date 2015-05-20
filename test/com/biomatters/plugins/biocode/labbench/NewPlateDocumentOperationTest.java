package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.lims.LimsTestCase;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import jebl.util.ProgressListener;
import org.junit.Test;

import java.util.Collections;

/**
 * @author Gen Li
 *         Created on 19/05/15 8:39 AM
 */
public class NewPlateDocumentOperationTest extends LimsTestCase {
    private static final String REACTION_TYPE_OPTION_NAME = "reactionType";
    private static final String PLATE_TYPE_OPTION_NAME = "plateType";
    private static final String STRIP_NUMBER_OPTION_NAME = "stripNumber";
    private static final String REACTION_NUMBER_OPTION_NAME = "reactionNumber";
    private static final String FROM_EXISTING_OPTION_NAME = "fromExisting";

    private static final String EXTRACTION_REACTION_TYPE_VALUE = "extraction";
    private static final String PCR_REACTION_TYPE_VALUE = "pcr";
    private static final String STRIPS_PLATE_TYPE_VALUE = "strips";
    private static final String INDIVIDUAL_REACTIONS_PLATE_TYPE_VALUE = "individualReactions";

    @Test
    public void testCreateTwoStripPCRPlateFromTwoStripExtractionPlate() throws DocumentOperationException {
        createPlate(PCR_REACTION_TYPE_VALUE, STRIPS_PLATE_TYPE_VALUE, 2, createPlate(EXTRACTION_REACTION_TYPE_VALUE, STRIPS_PLATE_TYPE_VALUE, 2, null));
    }

    @Test
    public void testCreateIndividualReactionsPlateFromIndividualReactionsPlate() throws DocumentOperationException {
        createPlate(PCR_REACTION_TYPE_VALUE, INDIVIDUAL_REACTIONS_PLATE_TYPE_VALUE, 24, createPlate(EXTRACTION_REACTION_TYPE_VALUE, INDIVIDUAL_REACTIONS_PLATE_TYPE_VALUE, 24, null));
    }

    private static Plate createPlate(String reactionType, String plateType, int plateValue, Plate plate) throws DocumentOperationException {
        AnnotatedPluginDocument annotatedExistingPlateDocument = null;
        AnnotatedPluginDocument[] annotatedExistingPlateDocuments = new AnnotatedPluginDocument[]{};

        if (plate != null) {
            annotatedExistingPlateDocument = DocumentUtilities.createAnnotatedPluginDocument(new PlateDocument(plate));
            annotatedExistingPlateDocuments = new AnnotatedPluginDocument[]{annotatedExistingPlateDocument};
        }

        return new NewPlateDocumentOperation()._performOperation(annotatedExistingPlateDocuments, ProgressListener.EMPTY, createNewPlateOptions(reactionType, plateType, plateValue, annotatedExistingPlateDocument));
    }

    private static NewPlateOptions createNewPlateOptions(String reactionType, String plateType, int plateValue, AnnotatedPluginDocument annotatedExistingPlateDocument) throws DocumentOperationException {
        NewPlateOptions newPlateOptions;

        if (annotatedExistingPlateDocument != null) {
            newPlateOptions = new NewPlateOptions(annotatedExistingPlateDocument);
            ((Options.BooleanOption)newPlateOptions.getOption(FROM_EXISTING_OPTION_NAME)).setValue(true);
        } else {
            newPlateOptions = new NewPlateOptions();
        }

        newPlateOptions.getOption(REACTION_TYPE_OPTION_NAME).setValueFromString(reactionType);
        newPlateOptions.getOption(PLATE_TYPE_OPTION_NAME).setValueFromString(plateType);
        if (plateType.equals(STRIPS_PLATE_TYPE_VALUE)) {
            ((Options.IntegerOption)newPlateOptions.getOption(STRIP_NUMBER_OPTION_NAME)).setValue(plateValue);
        } else if (plateType.equals(INDIVIDUAL_REACTIONS_PLATE_TYPE_VALUE)) {
            ((Options.IntegerOption)newPlateOptions.getOption(REACTION_NUMBER_OPTION_NAME)).setValue(plateValue);
        } else {
            throw new IllegalArgumentException("Unsupported plate type.");
        }

        return newPlateOptions;
    }
}