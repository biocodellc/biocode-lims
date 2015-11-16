package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.lims.LimsTestCase;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
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
    private static final String FROM_EXISTING_OPTION_NAME = "fromExisting";

    private static final String EXTRACTION_REACTION_TYPE_VALUE = "extraction";

    private static final String STRIPS_PLATE_TYPE_VALUE = "strips";
    private static final String INDIVIDUAL_REACTIONS_PLATE_TYPE_VALUE = "individualReactions";
    private static final String FOURTY_EIGHT_WELL_PLATE_TYPE_VALUE = "48Plate";
    private static final String NINETY_SIX_WELL_PLATE_TYPE_VALUE = "96Plate";
    private static final String THREE_HUNDRED_EIGHTY_FOUR_WELL_PLATE_TYPE_VALUE = "384Plate";

    @Test
    public void testCreateStripExtractionPlateFromStripExtractionPlate() throws DocumentOperationException {
        for (int numberOfStrips = 1; numberOfStrips <= 6; numberOfStrips++) {
            Plate extractionPlate = createExtractionNonEmptyExtractionPlate(STRIPS_PLATE_TYPE_VALUE, numberOfStrips);
            for (Reaction.Type type : Reaction.Type.values()) {
                createPlate(type.name, STRIPS_PLATE_TYPE_VALUE, numberOfStrips, extractionPlate);
            }
        }
    }

    @Test
    public void testCreateIndividualReactionsPlateFromIndividualReactionsExtractionPlate() throws DocumentOperationException {
        for (int numberOfIndividualReactions = 1; numberOfIndividualReactions <= Plate.MAX_INDIVIDUAL_REACTIONS; numberOfIndividualReactions++) {
            if(numberOfIndividualReactions % 8 == 0) {
                continue;  // These sizes are reserved for strips
            }
            Plate extractionPlate = createExtractionNonEmptyExtractionPlate(INDIVIDUAL_REACTIONS_PLATE_TYPE_VALUE, numberOfIndividualReactions);
            for (Reaction.Type type : Reaction.Type.values()) {
                Plate plate = createPlate(type.name, INDIVIDUAL_REACTIONS_PLATE_TYPE_VALUE, numberOfIndividualReactions, extractionPlate);
                assertEquals(type, plate.getReactionType());
                assertEquals(numberOfIndividualReactions, plate.getReactions().length);
                assertEquals(1, plate.getRows());
                int count = 0;
                for (Reaction reaction : plate.getReactions()) {
                    assertFalse(reaction.isEmpty());
                }
            }
        }
    }

    @Test
    public void testCreate48PCRPlateFrom48ExtractionPlate() throws DocumentOperationException {
        Plate extractionPlate = createExtractionNonEmptyExtractionPlate(FOURTY_EIGHT_WELL_PLATE_TYPE_VALUE, 0);
        for (Reaction.Type type : Reaction.Type.values()) {
            createPlate(type.name, FOURTY_EIGHT_WELL_PLATE_TYPE_VALUE, 0, extractionPlate);
        }
    }

    @Test
    public void testCreate96PCRPlateFrom96ExtractionPlate() throws DocumentOperationException {
        Plate extractionPlate = createExtractionNonEmptyExtractionPlate(NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0);
        for (Reaction.Type type : Reaction.Type.values()) {
            createPlate(type.name, NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0, extractionPlate);
        }
    }

    @Test
    public void testCreate384PCRPlateFrom384ExtractionPlate() throws DocumentOperationException {
        Plate extractionPlate = createExtractionNonEmptyExtractionPlate(THREE_HUNDRED_EIGHTY_FOUR_WELL_PLATE_TYPE_VALUE, 0);
        for (Reaction.Type type : Reaction.Type.values()) {
            createPlate(type.name, THREE_HUNDRED_EIGHTY_FOUR_WELL_PLATE_TYPE_VALUE, 0, extractionPlate);
        }
    }

    @Test
    public void testCreate384PCRPlateFromFour96ExtractionPlates() throws DocumentOperationException {
        Plate ninetySixWellExtractionPlateOne = createExtractionNonEmptyExtractionPlate(NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0);
        Plate ninetySixWellExtractionPlateTwo = createExtractionNonEmptyExtractionPlate(NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0);
        Plate ninetySixWellExtractionPlateThree = createExtractionNonEmptyExtractionPlate(NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0);
        Plate ninetySixWellExtractionPlateFour = createExtractionNonEmptyExtractionPlate(NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0);
        for (Reaction.Type type : Reaction.Type.values()) {
            createPlate(type.name, THREE_HUNDRED_EIGHTY_FOUR_WELL_PLATE_TYPE_VALUE, 0, ninetySixWellExtractionPlateOne);
            createPlate(type.name, THREE_HUNDRED_EIGHTY_FOUR_WELL_PLATE_TYPE_VALUE, 0, ninetySixWellExtractionPlateOne, ninetySixWellExtractionPlateTwo, ninetySixWellExtractionPlateThree, ninetySixWellExtractionPlateFour);
        }
    }

    private static Plate createExtractionNonEmptyExtractionPlate(String plateType, int plateValue) throws DocumentOperationException {
        Plate plate = createPlate(false, EXTRACTION_REACTION_TYPE_VALUE, plateType, plateValue);
        Reaction[] reactions = plate.getReactions();
        for (int i = 0; i < reactions.length; i++) {
            Reaction reaction = reactions[i];
            reaction.setExtractionId("" + i + ".1");
        }
        return plate;
    }

    private static Plate createPlate(String reactionType, String plateType, int plateValue, Plate... plates) throws DocumentOperationException {
        return createPlate(true, reactionType, plateType, plateValue, plates);
    }

    private static Plate createPlate(boolean checkForNotEmpty, String reactionType, String plateType, int plateValue, Plate... plates) throws DocumentOperationException {
        AnnotatedPluginDocument[] annotatedExistingPlateDocuments = new AnnotatedPluginDocument[plates.length];

        for (int i = 0; i < plates.length; i++) {
            annotatedExistingPlateDocuments[i] = DocumentUtilities.createAnnotatedPluginDocument(new PlateDocument(plates[i]));
        }

        Plate plate = new NewPlateDocumentOperation()._performOperation(annotatedExistingPlateDocuments, ProgressListener.EMPTY, createNewPlateOptions(reactionType, plateType, plateValue, annotatedExistingPlateDocuments));
        if(checkForNotEmpty) {
            for (Reaction reaction : plate.getReactions()) {
                assertFalse(reaction.isEmpty());
            }
        }
        return plate;
    }

    private static NewPlateOptions createNewPlateOptions(String reactionType, String plateType, int plateValue, AnnotatedPluginDocument... annotatedExistingPlateDocument) throws DocumentOperationException {
        NewPlateOptions newPlateOptions;

        if (annotatedExistingPlateDocument.length > 0) {
            newPlateOptions = new NewPlateOptions(annotatedExistingPlateDocument);
            Options.BooleanOption fromExistingOption = (Options.BooleanOption)newPlateOptions.getOption(FROM_EXISTING_OPTION_NAME);
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
            for(int i=0; i<4; i++) {
                if(annotatedExistingPlateDocument.length > i) {
                    newPlateOptions.setValue("fromQuadrant" + (".q") + (i+1), annotatedExistingPlateDocument[i].getURN().toString());
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported plate type: " + plateType + ".");
        }

        return newPlateOptions;
    }
}