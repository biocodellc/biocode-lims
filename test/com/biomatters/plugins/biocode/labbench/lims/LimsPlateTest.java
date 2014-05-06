package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import jebl.util.ProgressListener;
import org.junit.Test;

import java.sql.SQLException;

/**
 * Created Gen Li on 2/05/14.
 */
public class LimsPlateTest extends LimsTest {
    @Test
    public void locusSuccessfullySaved() throws DatabaseServiceException, BadDataException, SQLException, DocumentOperationException {
        String extractionID = "123";
        String locus = "abc";

        BiocodeService service = BiocodeService.getInstance();
        saveExtractionPlate("EP", "tissueID", extractionID, service);
        savePcrPlate("PP", locus, service, extractionID);

        assertEquals(locus, ((PlateDocument)service.retrieve(extractionID).get(2).getDocument()).getPlate().getReaction(0, 0).getLocus());
    }

    @Test
    public void locusSuccessfullyChanged() throws DatabaseServiceException, BadDataException, java.sql.SQLException, DocumentOperationException {
        String extractionID = "123";
        String initialLocus = "abc";
        String finalLocusOne = "def";
        String finalLocusTwo = "ghi";

        BiocodeService service = BiocodeService.getInstance();
        saveExtractionPlate("EP", "tissueID", extractionID, service);
        String[] extractionIDs = new String[96];
        for (int i = 0; i < extractionIDs.length; i++) {
            extractionIDs[i] = extractionID;
        }
        savePcrPlate("PP", initialLocus, service, extractionIDs);

        Query query = Query.Factory.createFieldQuery(LIMSConnection.EXTRACTION_ID_FIELD, Condition.EQUAL, new Object[] { extractionID },
                BiocodeService.getSearchDownloadOptions(false, false, true, false));

        Plate pcrPlate = ((PlateDocument)service.retrieve(query, ProgressListener.EMPTY).get(1).getDocument()).getPlate();

        for (Reaction reaction : pcrPlate.getReactions()) {
            assertEquals(initialLocus, reaction.getLocus());
        }

        int i = 0;
        for (Reaction reaction : pcrPlate.getReactions()) {
            if (i++ % 2 == 0) {
                reaction.getOptions().setValue(LIMSConnection.WORKFLOW_LOCUS_FIELD.getCode(), finalLocusOne);
            } else {
                reaction.getOptions().setValue(LIMSConnection.WORKFLOW_LOCUS_FIELD.getCode(), finalLocusTwo);
            }
        }

        service.savePlate(pcrPlate, ProgressListener.EMPTY);

        pcrPlate = ((PlateDocument)service.retrieve(query, ProgressListener.EMPTY).get(1).getDocument()).getPlate();

        i = 0;
        for (Reaction reaction : pcrPlate.getReactions()) {
            if (i++ % 2 == 0) {
                assertEquals(finalLocusOne, reaction.getLocus());
            } else {
                assertEquals(finalLocusTwo, reaction.getLocus());
            }
        }
    }
}