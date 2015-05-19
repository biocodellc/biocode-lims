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
    @Test
    public void testCreateTwoStripPCRPlateFromTwoStripExtractionPlate() throws DocumentOperationException {
        NewPlateDocumentOperation newPlateDocumentOperation = new NewPlateDocumentOperation();

        NewPlateOptions newTwoStripExtractionPlateOptions = new NewPlateOptions();

        newTwoStripExtractionPlateOptions.getOption("reactionType").setValue(new Options.OptionValue("extraction", "Extraction"));
        newTwoStripExtractionPlateOptions.getOption("plateType").setValue(new Options.OptionValue("strips", ""));
        newTwoStripExtractionPlateOptions.getOption("stripNumber").setValue(2);

        Plate twoStripExtractionPlate = newPlateDocumentOperation._performOperation(new AnnotatedPluginDocument[]{}, ProgressListener.EMPTY, newTwoStripExtractionPlateOptions);
        AnnotatedPluginDocument twoStripExtractionPlateAnnotatedPluginDocument = DocumentUtilities.createAnnotatedPluginDocument(new PlateDocument(twoStripExtractionPlate));

        NewPlateOptions newTwoStripPCRPlateOptions = new NewPlateOptions(twoStripExtractionPlateAnnotatedPluginDocument);

        newTwoStripPCRPlateOptions.getOption("fromExisting").setValue(true);
        newTwoStripPCRPlateOptions.getOption("reactionType").setValue(new Options.OptionValue("pcr", "PCR"));
        newTwoStripPCRPlateOptions.getOption("plateType").setValue(new Options.OptionValue("strips", ""));
        newTwoStripPCRPlateOptions.getOption("stripNumber").setValue(2);

        Plate twoStripPCRPlate = newPlateDocumentOperation._performOperation(new AnnotatedPluginDocument[]{twoStripExtractionPlateAnnotatedPluginDocument}, ProgressListener.EMPTY, newTwoStripPCRPlateOptions);
    }

    @Test
    public void testCreateIndividualReactionsPlateFromIndividualReactionsPlate() throws DocumentOperationException {
        NewPlateDocumentOperation newPlateDocumentOperation = new NewPlateDocumentOperation();

        NewPlateOptions newTwentyFourIndividualReactionsExtractionPlateOptions = new NewPlateOptions();

        newTwentyFourIndividualReactionsExtractionPlateOptions.getOption("reactionType").setValue(new Options.OptionValue("extraction", "Extraction"));
        newTwentyFourIndividualReactionsExtractionPlateOptions.getOption("plateType").setValue(new Options.OptionValue("individualReactions", ""));
        newTwentyFourIndividualReactionsExtractionPlateOptions.getOption("reactionNumber").setValue(24);

        Plate twentyFourIndividualReactionsExtractionPlate = newPlateDocumentOperation._performOperation(new AnnotatedPluginDocument[]{}, ProgressListener.EMPTY, newTwentyFourIndividualReactionsExtractionPlateOptions);
        AnnotatedPluginDocument twentyFourIndividualReactionsExtractionPlateAnnotatedPluginDocument = DocumentUtilities.createAnnotatedPluginDocument(new PlateDocument(twentyFourIndividualReactionsExtractionPlate));

        NewPlateOptions twentyFourIndividualReactionsPCRPlateOptions = new NewPlateOptions(twentyFourIndividualReactionsExtractionPlateAnnotatedPluginDocument);

        twentyFourIndividualReactionsPCRPlateOptions.getOption("fromExisting").setValue(true);
        twentyFourIndividualReactionsPCRPlateOptions.getOption("reactionType").setValue(new Options.OptionValue("pcr", "PCR"));
        twentyFourIndividualReactionsPCRPlateOptions.getOption("plateType").setValue(new Options.OptionValue("individualReactions", ""));
        twentyFourIndividualReactionsPCRPlateOptions.getOption("reactionNumber").setValue(24);

        Plate twentyFourIndividualReactionsPCRPlate = newPlateDocumentOperation._performOperation(new AnnotatedPluginDocument[]{twentyFourIndividualReactionsExtractionPlateAnnotatedPluginDocument}, ProgressListener.EMPTY, twentyFourIndividualReactionsPCRPlateOptions);
    }
}