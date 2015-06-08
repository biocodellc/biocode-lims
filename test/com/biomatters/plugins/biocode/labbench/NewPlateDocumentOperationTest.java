package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.lims.LimsTestCase;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import jebl.util.ProgressListener;
import org.junit.Test;

/**
 * @author Gen Li
 *         Created on 19/05/15 8:39 AM
 */
public class NewPlateDocumentOperationTest extends LimsTestCase {
    private static final String REACTION_TYPE_OPTION_NAME = "reactionType";
    private static final String PLATE_TYPE_OPTION_NAME = "plateType";
    private static final String STRIP_NUMBER_OPTION_NAME = "stripNumber";
    private static final String REACTION_NUMBER_OPTION_NAME = "reactionNumber";

    private static final String EXTRACTION_REACTION_TYPE_VALUE = "extraction";
    private static final String PCR_REACTION_TYPE_VALUE = "pcr";

    private static final String STRIPS_PLATE_TYPE_VALUE = "strips";
    private static final String INDIVIDUAL_REACTIONS_PLATE_TYPE_VALUE = "individualReactions";
    private static final String FOURTY_EIGHT_WELL_PLATE_TYPE_VALUE = "48Plate";
    private static final String NINETY_SIX_WELL_PLATE_TYPE_VALUE = "96Plate";
    private static final String THREE_HUNDRED_EIGHTY_FOUR_WELL_PLATE_TYPE_VALUE = "384Plate";

    @Test
    public void testCreateStripExtractionPlateFromStripExtractionPlate() throws DocumentOperationException {
        for (int numberOfStrips = 1; numberOfStrips <= 6; numberOfStrips++) {
            createPlate(PCR_REACTION_TYPE_VALUE, STRIPS_PLATE_TYPE_VALUE, numberOfStrips, createPlate(EXTRACTION_REACTION_TYPE_VALUE, STRIPS_PLATE_TYPE_VALUE, numberOfStrips));
        }
    }

    @Test
    public void testCreateIndividualReactionsPCRPlateFromIndividualReactionsExtractionPlate() throws DocumentOperationException {
        for (int numberOfIndividualReactions = 1; numberOfIndividualReactions <= 26; numberOfIndividualReactions++) {
            createPlate(PCR_REACTION_TYPE_VALUE, INDIVIDUAL_REACTIONS_PLATE_TYPE_VALUE, numberOfIndividualReactions, createPlate(EXTRACTION_REACTION_TYPE_VALUE, INDIVIDUAL_REACTIONS_PLATE_TYPE_VALUE, numberOfIndividualReactions));
        }
    }

    @Test
    public void testCreate48PCRPlateFrom48ExtractionPlate() throws DocumentOperationException {
        createPlate(PCR_REACTION_TYPE_VALUE, FOURTY_EIGHT_WELL_PLATE_TYPE_VALUE, 0, createPlate(EXTRACTION_REACTION_TYPE_VALUE, FOURTY_EIGHT_WELL_PLATE_TYPE_VALUE, 0));
    }

    @Test
    public void testCreate96PCRPlateFrom96ExtractionPlate() throws DocumentOperationException {
        createPlate(PCR_REACTION_TYPE_VALUE, NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0, createPlate(EXTRACTION_REACTION_TYPE_VALUE, NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0));
    }

    @Test
    public void testCreate384PCRPlateFrom384ExtractionPlate() throws DocumentOperationException {
        createPlate(PCR_REACTION_TYPE_VALUE, THREE_HUNDRED_EIGHTY_FOUR_WELL_PLATE_TYPE_VALUE, 0, createPlate(EXTRACTION_REACTION_TYPE_VALUE, THREE_HUNDRED_EIGHTY_FOUR_WELL_PLATE_TYPE_VALUE, 0));
    }

    @Test
    public void testCreate384PCRPlateFromFour96ExtractionPlates() throws DocumentOperationException {
        Plate ninetySixWellExtractionPlateOne = createPlate(EXTRACTION_REACTION_TYPE_VALUE, NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0);
        Plate ninetySixWellExtractionPlateTwo = createPlate(EXTRACTION_REACTION_TYPE_VALUE, NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0);
        Plate ninetySixWellExtractionPlateThree = createPlate(EXTRACTION_REACTION_TYPE_VALUE, NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0);
        Plate ninetySixWellExtractionPlateFour = createPlate(EXTRACTION_REACTION_TYPE_VALUE, NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0);
        createPlate(PCR_REACTION_TYPE_VALUE, THREE_HUNDRED_EIGHTY_FOUR_WELL_PLATE_TYPE_VALUE, 0, ninetySixWellExtractionPlateOne, ninetySixWellExtractionPlateTwo, ninetySixWellExtractionPlateThree, ninetySixWellExtractionPlateFour);
    }

    private static Plate createPlate(String reactionType, String plateType, int plateValue, Plate... plates) throws DocumentOperationException {
        AnnotatedPluginDocument[] annotatedExistingPlateDocuments = new AnnotatedPluginDocument[plates.length];

        for (int i = 0; i < plates.length; i++) {
            annotatedExistingPlateDocuments[i] = DocumentUtilities.createAnnotatedPluginDocument(new PlateDocument(plates[i]));
        }

        return new NewPlateDocumentOperation()._performOperation(annotatedExistingPlateDocuments, ProgressListener.EMPTY, createNewPlateOptions(reactionType, plateType, plateValue, annotatedExistingPlateDocuments));
    }

    private static NewPlateOptions createNewPlateOptions(String reactionType, String plateType, int plateValue, AnnotatedPluginDocument... annotatedExistingPlateDocument) throws DocumentOperationException {
        NewPlateOptions newPlateOptions;

        if (annotatedExistingPlateDocument.length > 0) {
            newPlateOptions = new NewPlateOptions(annotatedExistingPlateDocument);
            Options.BooleanOption fromExistingOption = (Options.BooleanOption)newPlateOptions.getOption(NewPlateOptions.FROM_EXISTING_OPTION_NAME);
            fromExistingOption.setValue(true);
        } else {
            newPlateOptions = new NewPlateOptions();
        }

        newPlateOptions.getOption(REACTION_TYPE_OPTION_NAME).setValueFromString(reactionType);
        newPlateOptions.getOption(PLATE_TYPE_OPTION_NAME).setValueFromString(plateType);
        if (plateType.equals(STRIPS_PLATE_TYPE_VALUE)) {
            Options.IntegerOption stripNumberOptions = (Options.IntegerOption)newPlateOptions.getOption(STRIP_NUMBER_OPTION_NAME);
            stripNumberOptions.setValue(plateValue);
        } else if (plateType.equals(INDIVIDUAL_REACTIONS_PLATE_TYPE_VALUE)) {
            Options.IntegerOption reactionNumberOptions = (Options.IntegerOption)newPlateOptions.getOption(REACTION_NUMBER_OPTION_NAME);
            reactionNumberOptions.setValue(plateValue);
        } else if (plateType.equals(FOURTY_EIGHT_WELL_PLATE_TYPE_VALUE)) {
        } else if (plateType.equals(NINETY_SIX_WELL_PLATE_TYPE_VALUE)) {
        } else if (plateType.equals(THREE_HUNDRED_EIGHTY_FOUR_WELL_PLATE_TYPE_VALUE)) {
        } else {
            throw new IllegalArgumentException("Unsupported plate type: " + plateType + ".");
        }

        return newPlateOptions;
    }
}