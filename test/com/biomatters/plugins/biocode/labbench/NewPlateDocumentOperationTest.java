package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.lims.LimsTestCase;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionOptions;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import jebl.util.ProgressListener;
import org.jdom.Element;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    private static final String STRIPS_PLATE_TYPE_VALUE = "strips";
    private static final String INDIVIDUAL_REACTIONS_PLATE_TYPE_VALUE = "individualReactions";
    private static final String FOURTY_EIGHT_WELL_PLATE_TYPE_VALUE = "48Plate";
    private static final String NINETY_SIX_WELL_PLATE_TYPE_VALUE = "96Plate";
    private static final String THREE_HUNDRED_EIGHTY_FOUR_WELL_PLATE_TYPE_VALUE = "384Plate";

    //TODO: Fix these tests which are failing due to Caused by: java.lang.IllegalArgumentException: Key too long: buttonVisibleView/Add Thermocycles:Create new Thermocycles, or view existing ones
    @Test
    public void testCreateStripExtractionPlateFromStripExtractionPlate() throws DocumentOperationException, DatabaseServiceException, BadDataException {
        for (int numberOfStrips = 1; numberOfStrips <= 6; numberOfStrips++) {
            Plate extractionPlate = createExtractionNonEmptyExtractionPlate(STRIPS_PLATE_TYPE_VALUE, numberOfStrips);
            for (Reaction.Type type : Reaction.Type.values()) {
                createPlate(type.name, STRIPS_PLATE_TYPE_VALUE, numberOfStrips, extractionPlate);
            }
        }
    }

    @Test
    public void testCreateIndividualReactionsPlateFromIndividualReactionsExtractionPlate() throws DocumentOperationException, DatabaseServiceException, BadDataException {
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
    public void testCreate48PCRPlateFrom48ExtractionPlate() throws DocumentOperationException, DatabaseServiceException, BadDataException {
        Plate extractionPlate = createExtractionNonEmptyExtractionPlate(FOURTY_EIGHT_WELL_PLATE_TYPE_VALUE, 0);
        for (Reaction.Type type : Reaction.Type.values()) {
            createPlate(type.name, FOURTY_EIGHT_WELL_PLATE_TYPE_VALUE, 0, extractionPlate);
        }
    }

    @Test
    public void testCreate96PCRPlateFrom96ExtractionPlate() throws DocumentOperationException, DatabaseServiceException, BadDataException {
        Plate extractionPlate = createExtractionNonEmptyExtractionPlate(NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0);
        for (Reaction.Type type : Reaction.Type.values()) {
            createPlate(type.name, NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0, extractionPlate);
        }
    }

    @Test
    public void testCreate384PCRPlateFrom384ExtractionPlate() throws DocumentOperationException, DatabaseServiceException, BadDataException {
        Plate extractionPlate = createExtractionNonEmptyExtractionPlate(THREE_HUNDRED_EIGHTY_FOUR_WELL_PLATE_TYPE_VALUE, 0);
        for (Reaction.Type type : Reaction.Type.values()) {
            createPlate(type.name, THREE_HUNDRED_EIGHTY_FOUR_WELL_PLATE_TYPE_VALUE, 0, extractionPlate);
        }
    }

    @Test
    public void testCreate384PCRPlateFromFour96ExtractionPlates() throws DocumentOperationException, DatabaseServiceException, BadDataException {
        Plate ninetySixWellExtractionPlateOne = createExtractionNonEmptyExtractionPlate(NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0);
        Plate ninetySixWellExtractionPlateTwo = createExtractionNonEmptyExtractionPlate(NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0);
        Plate ninetySixWellExtractionPlateThree = createExtractionNonEmptyExtractionPlate(NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0);
        Plate ninetySixWellExtractionPlateFour = createExtractionNonEmptyExtractionPlate(NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0);
        for (Reaction.Type type : Reaction.Type.values()) {
            createPlate(type.name, THREE_HUNDRED_EIGHTY_FOUR_WELL_PLATE_TYPE_VALUE, 0, ninetySixWellExtractionPlateOne, ninetySixWellExtractionPlateTwo, ninetySixWellExtractionPlateThree, ninetySixWellExtractionPlateFour);
        }
    }

    @Test
    public void testAlternatingCustomCopy() throws DocumentOperationException, DatabaseServiceException, BadDataException {
        Plate extractionPlate = createExtractionNonEmptyExtractionPlate(STRIPS_PLATE_TYPE_VALUE, 5);
        Options.OptionValue method = NewPlateOptions.ALTERNATING;
        testCustomCopy(extractionPlate, method, 1, "1,2,3,4,5".split(","));
        testCustomCopy(extractionPlate, method, 2, "1,6,2,7,3,8,4,9,5,10".split(","));
        testCustomCopy(extractionPlate, method, 3, ("1,6,11,2,7,12,3,8,13,4,9,14,5,10,15").split(","));
    }

    @Test
    public void testSequentialCustomCopy() throws DocumentOperationException, DatabaseServiceException, BadDataException {
        Plate extractionPlate = createExtractionNonEmptyExtractionPlate(STRIPS_PLATE_TYPE_VALUE, 5);
        Options.OptionValue method = NewPlateOptions.SEQUENTIALLY;
        testCustomCopy(extractionPlate, method, 1, "1,2,3,4,5".split(","));
        testCustomCopy(extractionPlate, method, 2, "1,2,3,4,5,6,7,8,9,10".split(","));
        testCustomCopy(extractionPlate, method, 3, ("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15").split(","));
    }

    private void testCustomCopy(Plate extractionPlate, Options.OptionValue method, int numRowsToCopy, String... expectedIds) throws DocumentOperationException {
        List<Integer> rows = new ArrayList<Integer>();
        for (int i = 0; i < numRowsToCopy; i++) {
            rows.add(i);
        }
        testCustomCopy(extractionPlate, Arrays.asList(Reaction.Type.values()), method, 1, rows, expectedIds);
    }

    private List<Plate> testCustomCopy(Plate extractionPlate, List<Reaction.Type> destReactionTypes, Options.OptionValue method, int start,  List<Integer> rowsToCopy, String... expectedIds) throws DocumentOperationException {
        List<Plate> results = new ArrayList<Plate>();
        AnnotatedPluginDocument annotatedPlate = DocumentUtilities.createAnnotatedPluginDocument(new PlateDocument(extractionPlate));
        for (Reaction.Type type : destReactionTypes) {
            NewPlateOptions options = createNewPlateOptions(type.name, INDIVIDUAL_REACTIONS_PLATE_TYPE_VALUE, extractionPlate.getCols() * rowsToCopy.size(), annotatedPlate);
            options.setValue(NewPlateOptions.USE_CUSTOM_COPY, true);
            options.setValue("customCopy.insertStart", start);
            options.setValue("customCopy.insertMethod", method);
            Options customCopyOptions = options.getChildOptions().get("customCopy");
            for (int i = 0; i < rowsToCopy.size(); i++) {
                Integer rowIndex = rowsToCopy.get(i);
                customCopyOptions.setStringValue("rowOptions." + i + ".row", "" + rowIndex);
            }

            Plate plate = createPlateUsingOptions(false, options, annotatedPlate);
            results.add(plate);
            Reaction[] reactions = plate.getReactions();
            List<String> actualIds = new ArrayList<String>();
            for (int reactionIndex = 0; reactionIndex < reactions.length; reactionIndex++) {
                Reaction reaction = reactions[reactionIndex];
                int zeroBasedStart = start - 1;
                boolean beforeStart = reactionIndex < zeroBasedStart;
                boolean afterEnd = reactionIndex >= zeroBasedStart + rowsToCopy.size() * extractionPlate.getCols();
                assertEquals(beforeStart || afterEnd, reaction.isEmpty());
                if(!beforeStart && !afterEnd) {
                    actualIds.add(reaction.getExtractionId());
                }
            }
            if(expectedIds != null) {
                if (type == Reaction.Type.Extraction) {
                    // When copying to a new extraction plate, new IDs need to be generated
                    String[] newExtractionIds = new String[expectedIds.length];
                    for (int i = 0; i < expectedIds.length; i++) {
                        newExtractionIds[i] = expectedIds[i] + ".1";
                    }
                    assertEquals(Arrays.asList(newExtractionIds), actualIds);
                } else {
                    assertEquals(Arrays.asList(expectedIds), actualIds);
                }
            }
        }
        return results;
    }

    @Test
    public void testCustomCopyInSmithsonianGelQuantificationFormat() throws DocumentOperationException, DatabaseServiceException, BadDataException {
        Plate extractionPlate = createExtractionNonEmptyExtractionPlate(NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0);
        AnnotatedPluginDocument annotatedPlate = DocumentUtilities.createAnnotatedPluginDocument(new PlateDocument(extractionPlate));
        List<Plate> gelPlates = new ArrayList<Plate>();
        for(int rowIndex=0; rowIndex<extractionPlate.getRows();) {
            List<Integer> rows = new ArrayList<Integer>();
            rows.add(rowIndex++);
            rows.add(rowIndex++);

            gelPlates.addAll(testCustomCopy(extractionPlate, Collections.singletonList(Reaction.Type.GelQuantification), NewPlateOptions.ALTERNATING, 4, rows, null));
        }
        assertEquals(4, gelPlates.size());  // There should be 4x 30-well gel quantification plates from a single 96-well plate.
    }

    @Test
    public void testCustomCopyWhenSourceHasMoreWellsThanDestination() throws DocumentOperationException, DatabaseServiceException, BadDataException {
        Plate extractionPlate = createExtractionNonEmptyExtractionPlate(NINETY_SIX_WELL_PLATE_TYPE_VALUE, 0);
        AnnotatedPluginDocument annotatedPlate = DocumentUtilities.createAnnotatedPluginDocument(new PlateDocument(extractionPlate));
        NewPlateOptions options = createNewPlateOptions(Reaction.Type.GelQuantification.name, INDIVIDUAL_REACTIONS_PLATE_TYPE_VALUE, 20, annotatedPlate);
        options.setValue(NewPlateOptions.USE_CUSTOM_COPY, true);

        for (int start : Arrays.asList(19, 20, 21)) {
            for (Options.OptionValue insertMethod : Arrays.asList(NewPlateOptions.ALTERNATING, NewPlateOptions.SEQUENTIALLY)) {
                testCustomCopy(extractionPlate, Arrays.asList(Reaction.Type.values()), insertMethod, start, Arrays.asList(1,2,3), null);
            }
        }
    }

    private static int extractionPlateCount = 1;
    private static Plate createExtractionNonEmptyExtractionPlate(String plateType, int plateValue) throws DocumentOperationException, DatabaseServiceException, BadDataException {
        Plate plate = createPlate(false, EXTRACTION_REACTION_TYPE_VALUE, plateType, plateValue);
        plate.setName("ExtractionPlate"+ extractionPlateCount++);
        Reaction[] reactions = plate.getReactions();
        for (int i = 0; i < reactions.length; i++) {
            Reaction reaction = reactions[i];
            final String id = "" + (i + 1);
            reaction.setExtractionId(id);
            reaction.getOptions().setValue(ExtractionOptions.TISSUE_ID, id);
        }
        // Save the plate so that the extraction IDs cannot be re-used
        BiocodeService.getInstance().savePlate(plate, ProgressListener.EMPTY);
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
        NewPlateOptions options = createNewPlateOptions(reactionType, plateType, plateValue, annotatedExistingPlateDocuments);

        return createPlateUsingOptions(checkForNotEmpty, options, annotatedExistingPlateDocuments);
    }

    private static Plate createPlateUsingOptions(boolean checkForNotEmpty, NewPlateOptions options, AnnotatedPluginDocument... annotatedExistingPlateDocuments) throws DocumentOperationException {
        Plate plate = new NewPlateDocumentOperation()._performOperation(annotatedExistingPlateDocuments, ProgressListener.EMPTY, options);
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