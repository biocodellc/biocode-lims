package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.TestGeneious;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.connection.Connection;
import com.biomatters.plugins.biocode.labbench.fims.ExcelFimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import jebl.util.ProgressListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author Gen Li
 * Created on 6/05/14.
 */
public abstract class LimsTestCase extends Assert {
    private static final String DATABASE_NAME = "testLimsForSearchTest";
    private static final long MAX_TIME_TO_WAIT_FOR_OPTIONS_UPDATE = 10 * 1000;
    private static final long WAIT_INCREMENT = 200;

    public static void waitForTissueColumnInitialization(String spreadsheetPath, ExcelFimsConnectionOptions options) {
        options.setValue(ExcelFimsConnectionOptions.CONNECTION_OPTIONS_KEY + "." +ExcelFimsConnectionOptions.FILE_LOCATION, spreadsheetPath);
        long timeWaited = 0;
        while ("None".equals(options.getTissueColumn())) {
            ThreadUtilities.sleep(WAIT_INCREMENT);
            timeWaited += WAIT_INCREMENT;
            assertTrue("Waited " + timeWaited + "ms for Options to update and gave up.", timeWaited < MAX_TIME_TO_WAIT_FOR_OPTIONS_UPDATE);
        }
        System.out.println("Slept " + timeWaited + " ms");
    }

    @Before
    public void createDatabaseAndInitializeConnections() throws IOException, SQLException, ConnectionException, DatabaseServiceException {
        TestGeneious.initialize();

        File temp = FileUtilities.createTempDir(true);
        BiocodeService biocodeeService = BiocodeService.getInstance();
        biocodeeService.setDataDirectory(temp);
        LocalLIMSConnectionOptions.createDatabase(DATABASE_NAME);

        Connection connectionConfig = new Connection("forTests");
        PasswordOptions _fimsOptions = connectionConfig.getFimsOptions();
        assertTrue("First FIMS option has changed from Excel FIMS.  Test needs updating",
                _fimsOptions instanceof ExcelFimsConnectionOptions);
        ExcelFimsConnectionOptions fimsOptions = (ExcelFimsConnectionOptions) _fimsOptions;
        waitForTissueColumnInitialization(TestUtilities.getPathToDemoFIMSExcel(LimsSearchTest.class, "demo video FIMS.xls"), fimsOptions);

        fimsOptions.setValue(TableFimsConnectionOptions.TISSUE_ID, "tissue_id");
        fimsOptions.setValue(TableFimsConnectionOptions.SPECIMEN_ID, "Specimen No.");
        fimsOptions.setValue(TableFimsConnectionOptions.STORE_PLATES, Boolean.TRUE.toString());
        fimsOptions.setValue(TableFimsConnectionOptions.PLATE_NAME, "plate_name");
        fimsOptions.setValue(TableFimsConnectionOptions.PLATE_WELL, "well_number");
        fimsOptions.autodetectTaxonFields();

        LimsConnectionOptions parentLimsOptions = (LimsConnectionOptions) connectionConfig.getLimsOptions();
        parentLimsOptions.setValue(LimsConnectionOptions.CONNECTION_TYPE_CHOOSER, LIMSConnection.AvailableLimsTypes.local.name());
        PasswordOptions _limsOptions = parentLimsOptions.getSelectedLIMSOptions();
        assertTrue("Test needs updating.  Local LIMS not first option", _limsOptions instanceof LocalLIMSConnectionOptions);
        LocalLIMSConnectionOptions limsOptions = (LocalLIMSConnectionOptions) _limsOptions;
        limsOptions.setValue(LocalLIMSConnectionOptions.DATABASE, DATABASE_NAME);

        biocodeeService.connect(connectionConfig, false);
    }

    @After
    public void cleanUpTestResources() throws IOException {
        BiocodeService.getInstance().logOut();
    }

    /**
     * Checks an {@link com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument} to see if it is the
     * expected plate.  If it is then run some assertions.
     *
     * @param doc The document
     * @param plateName The plate's name
     * @param expectedType The plate's expected type
     * @param expectedExtractionIds The expected extraction IDs
     *
     * @return The Plate found or null if the document did not match
     */
    protected Plate getPlateFromDocument(AnnotatedPluginDocument doc, String plateName, Reaction.Type expectedType, String... expectedExtractionIds) {
        if(PlateDocument.class.isAssignableFrom(doc.getDocumentClass())) {
            PlateDocument plateDoc = (PlateDocument) doc.getDocumentOrNull();
            if(plateDoc.getPlate().getReactionType() == expectedType && plateName.equals(doc.getName())) {
                Reaction[] reactions = plateDoc.getPlate().getReactions();
                assertEquals((int)Plate.Size.w96.numberOfReactions(), reactions.length);
                for(int i=0; i<expectedExtractionIds.length; i++) {
                    assertEquals(expectedExtractionIds[i], reactions[i].getExtractionId());
                }
                for(int i=expectedExtractionIds.length; i<reactions.length; i++) {
                    assertEquals("", reactions[i].getExtractionId());
                    assertNull("Other wells should have null sample", reactions[i].getFimsSample());
                }
                return plateDoc.getPlate();
            }
        }
        return null;
    }

    protected void saveExtractionPlate(String plateName, String tissue, String extractionId, BiocodeService service) throws DatabaseServiceException, BadDataException {
        Map<String,String> values = Collections.singletonMap(tissue, extractionId);
        saveExtractionPlate(plateName, service, values, new Date());
    }

    protected void saveExtractionPlate(String plateName, String tissue, String extractionId, BiocodeService service, Date lastModified) throws DatabaseServiceException, BadDataException {
        Map<String,String> values = Collections.singletonMap(tissue, extractionId);
        saveExtractionPlate(plateName, service, values, lastModified);
    }

    protected void saveExtractionPlate(String plateName, BiocodeService service, Map<String, String> values, Date lastModified) throws DatabaseServiceException, BadDataException {
        Plate extractionPlate = new Plate(Plate.Size.w96, Reaction.Type.Extraction, lastModified);
        extractionPlate.setName(plateName);

        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            ExtractionReaction reaction = (ExtractionReaction)extractionPlate.getReaction(0, index++);
            reaction.setTissueId(entry.getKey());
            reaction.setExtractionId(entry.getValue());
        }

        service.savePlate(extractionPlate, ProgressListener.EMPTY);
    }

    protected void saveExtractionPlate(String plateName, BiocodeService service, Map<String, String> values) throws DatabaseServiceException, BadDataException {
        Plate extractionPlate = new Plate(Plate.Size.w96, Reaction.Type.Extraction);
        extractionPlate.setName(plateName);

        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            ExtractionReaction reaction = (ExtractionReaction)extractionPlate.getReaction(0, index++);
            reaction.setTissueId(entry.getKey());
            reaction.setExtractionId(entry.getValue());
        }

        service.savePlate(extractionPlate, ProgressListener.EMPTY);
    }

    protected Plate savePcrPlate(String plateName, String locus, BiocodeService service, String... extractionIds) throws SQLException, BadDataException, DatabaseServiceException {
        Plate pcrPlate = new Plate(Plate.Size.w96, Reaction.Type.PCR);
        pcrPlate.setName(plateName);
        List<Thermocycle> thermocycle = BiocodeService.getInstance().getPCRThermocycles();
        assertFalse("No default thermocycles in the system", thermocycle.isEmpty());
        pcrPlate.setThermocycle(thermocycle.get(0));
        for (Reaction reaction : pcrPlate.getReactions()) {
            System.out.println(reaction.getLocus());
        }
        int index = 0;
        for (String extractionId : extractionIds) {
            PCRReaction reaction = (PCRReaction)pcrPlate.getReaction(0, index++);
            reaction.setExtractionId(extractionId);
            reaction.getOptions().setValue(LIMSConnection.WORKFLOW_LOCUS_FIELD.getCode(), locus);
        }

        service.savePlate(pcrPlate, ProgressListener.EMPTY);
        return pcrPlate;
    }

    protected Plate saveCyclesequencingPlate(String plateName, String locus, String direction, BiocodeService service, Plate copyReactionsFrom, String... extractionIds) throws DatabaseServiceException, BadDataException, DocumentOperationException {
        Plate plate = new Plate(Plate.Size.w96, Reaction.Type.CycleSequencing);
        if(copyReactionsFrom != null) {
            NewPlateDocumentOperation.copyPlateOfSameSize(copyReactionsFrom, plate, null);
        }
        plate.setName(plateName);
        List<Thermocycle> thermocycle = BiocodeService.getInstance().getCycleSequencingThermocycles();
        assertFalse("No default thermocycles in the system", thermocycle.isEmpty());
        plate.setThermocycle(thermocycle.get(0));

        int index = 0;
        for (String extractionId : extractionIds) {
            CycleSequencingReaction reaction = (CycleSequencingReaction)plate.getReaction(0, index++);
            reaction.setExtractionId(extractionId);
            reaction.getOptions().setValue(LIMSConnection.WORKFLOW_LOCUS_FIELD.getCode(), locus);
            reaction.getOptions().setValue(CycleSequencingOptions.DIRECTION, direction);
        }
        service.savePlate(plate, ProgressListener.EMPTY);
        return plate;
    }
}