package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.TestGeneious;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.fims.ExcelFimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import jebl.util.ProgressListener;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 4/03/14 3:36 PM
 */
public class LimsSearchTest extends Assert {

    private static final String DATABASE_NAME = "testLimsForSearchTest";

    @Before
    public void createDatabaseAndInitializeConnections() throws IOException, SQLException {
        TestGeneious.initialize();

        File temp = FileUtilities.createTempDir(true);
        BiocodeService biocodeeService = BiocodeService.getInstance();
        biocodeeService.setDataDirectory(temp);
        LocalLIMSConnectionOptions.createDatabase(DATABASE_NAME);

        ConnectionManager.Connection connectionConfig = new ConnectionManager.Connection("forTests");
        PasswordOptions _fimsOptions = connectionConfig.getFimsOptions();
        assertTrue("First FIMS option has changed from Excel FIMS.  Test needs updating",
                _fimsOptions instanceof ExcelFimsConnectionOptions);
        ExcelFimsConnectionOptions fimsOptions = (ExcelFimsConnectionOptions) _fimsOptions;
        fimsOptions.setValue(ExcelFimsConnectionOptions.CONNECTION_OPTIONS_KEY + "." + ExcelFimsConnectionOptions.FILE_LOCATION, getPathToDemoFIMSExcel());
        fimsOptions.setValue(TableFimsConnectionOptions.TISSUE_ID, "tissue_id");
        fimsOptions.setValue(TableFimsConnectionOptions.SPECIMEN_ID, "Specimen No.");
        fimsOptions.setValue(TableFimsConnectionOptions.STORE_PLATES, Boolean.TRUE.toString());
        fimsOptions.setValue(TableFimsConnectionOptions.PLATE_NAME, "plate_name");
        fimsOptions.setValue(TableFimsConnectionOptions.PLATE_WELL, "well_number");
        fimsOptions.autodetectTaxonFields();

        LimsConnectionOptions parentLimsOptions = (LimsConnectionOptions)connectionConfig.getLimsOptions();
        parentLimsOptions.setValue(LimsConnectionOptions.CONNECTION_TYPE_CHOOSER, LIMSConnection.AvailableLimsTypes.local.name());
        PasswordOptions _limsOptions = parentLimsOptions.getSelectedLIMSOptions();
        assertTrue("Test needs updating.  Local LIMS not first option", _limsOptions instanceof LocalLIMSConnectionOptions);
        LocalLIMSConnectionOptions limsOptions = (LocalLIMSConnectionOptions) _limsOptions;
        limsOptions.setValue(LocalLIMSConnectionOptions.DATABASE, DATABASE_NAME);

        biocodeeService.connect(connectionConfig, false);
    }

    @After
    public void logoutAndDeleteLIMS() throws IOException {
        BiocodeService.getInstance().logOut();
        LocalLIMSConnectionOptions.deleteDatabase(DATABASE_NAME);
    }

    @Test
    public void basicExtractionSearch() throws IOException, BadDataException, SQLException {
        String plateName = "Plate_M037";
        String tissue = "MBIO24950.1";
        String extractionId = "MBIO24950.1.1";

        BiocodeService service = BiocodeService.getInstance();
        saveExtractionPlate(plateName, tissue, extractionId, service);

        List<AnnotatedPluginDocument> searchResults = service.retrieve(tissue);
        boolean foundTissue = false;
        boolean foundPlate = false;
        for (AnnotatedPluginDocument searchResult : searchResults) {
            if(getPlateFromDocument(searchResult, plateName, Reaction.Type.Extraction, extractionId) != null) {
                foundPlate = true;
            } else if(TissueDocument.class.isAssignableFrom(searchResult.getDocumentClass())) {
                foundTissue = true;
                assertEquals(tissue, searchResult.getName());
            }
        }
        assertTrue(foundTissue);
        assertTrue(foundPlate);
    }

    @Test
    public void pcrAndWorkflowSearch() throws BadDataException, SQLException {
        String tissue = "MBIO24950.1";
        String extractionId = "MBIO24950.1.1";

        BiocodeService service = BiocodeService.getInstance();
        saveExtractionPlate("Plate_M037", tissue, extractionId, service);

        String plateName = "PCR_M037";
        String locus = "COI";
        savePcrPlate(plateName, locus, service, extractionId);

        List<AnnotatedPluginDocument> searchResults = service.retrieve(extractionId);
        boolean foundWorkflow = false;
        boolean foundPcr = false;
        for (AnnotatedPluginDocument searchResult : searchResults) {
            if(WorkflowDocument.class.isAssignableFrom(searchResult.getDocumentClass())) {
                foundWorkflow = true;
                assertEquals(locus, ((WorkflowDocument)searchResult.getDocumentOrNull()).getWorkflow().getLocus());
            } else if(getPlateFromDocument(searchResult, plateName, Reaction.Type.PCR, extractionId) != null) {
                foundPcr = true;
            }
        }
        assertTrue(foundWorkflow);
        assertTrue(foundPcr);
    }

    @Test
    public void cyclesequencingSearch() throws BadDataException, SQLException, DocumentOperationException {
        String extractionId = "MBIO24950.1.1";
        String seqFName = "SeqF_M037";
        String seqRName = "SeqR_M037";

        BiocodeService service = BiocodeService.getInstance();
        saveExtractionPlate("Plate_M037", "MBIO24950.1", extractionId, service);
        Plate pcrPlate = savePcrPlate("PCR_M037", "COI", service, extractionId);

        saveCyclesequencingPlate(seqFName, "COI", CycleSequencingOptions.FORWARD_VALUE, service, pcrPlate, extractionId);
        saveCyclesequencingPlate(seqRName, "COI", CycleSequencingOptions.REVERSE_VALUE, service, pcrPlate, extractionId);

        List<AnnotatedPluginDocument> searchResults = service.retrieve(extractionId);
        boolean foundSeqF = false;
        boolean foundSeqR = false;
        for (AnnotatedPluginDocument searchResult : searchResults) {
            Plate seqR = getPlateFromDocument(searchResult, seqRName, Reaction.Type.CycleSequencing, extractionId);
            Plate seqF = getPlateFromDocument(searchResult, seqFName, Reaction.Type.CycleSequencing, extractionId);
            if(seqF != null) {
                foundSeqF = true;
                assertEquals(CycleSequencingOptions.FORWARD_VALUE,
                                        seqF.getReactions()[0].getOptions().getValueAsString(CycleSequencingOptions.DIRECTION));
            } else if(seqR != null) {
                foundSeqR = true;
                assertEquals(CycleSequencingOptions.REVERSE_VALUE,
                        seqR.getReactions()[0].getOptions().getValueAsString(CycleSequencingOptions.DIRECTION));
            }
        }
        assertTrue(foundSeqF);
        assertTrue(foundSeqR);
    }

    @Test
    public void searchByTissueDoesNotReturnExtra() throws IOException, BadDataException, SQLException, DatabaseServiceException {
        String plateName = "Plate_M037";
        String tissue = "MBIO24950.1";
        String extractionId = "MBIO24950.1.1";

        String plateName2 = "Plate_M037_2";
        String tissue2 = "MBIO24951.1";
        String extractionId2 = "MBIO24951.1.1";

        BiocodeService service = BiocodeService.getInstance();
        saveExtractionPlate(plateName, tissue, extractionId, service);
        saveExtractionPlate(plateName2, tissue2, extractionId2, service);

        Query query = Query.Factory.createFieldQuery(
                BiocodeService.getInstance().getActiveFIMSConnection().getTissueSampleDocumentField(), Condition.EQUAL, new Object[]{tissue});
        List<AnnotatedPluginDocument> searchResults = service.retrieve(query, ProgressListener.EMPTY);
        List<String> plates = new ArrayList<String>();
        for (AnnotatedPluginDocument searchResult : searchResults) {
            if(PlateDocument.class.isAssignableFrom(searchResult.getDocumentClass())) {
                plates.add(searchResult.getName());
            }
        }
        assertEquals(1, plates.size());
        assertEquals(plateName, plates.get(0));

        List<AnnotatedPluginDocument> searchResults2 = service.retrieve(tissue2);
        plates = new ArrayList<String>();
        for (AnnotatedPluginDocument searchResult : searchResults2) {
            if(PlateDocument.class.isAssignableFrom(searchResult.getDocumentClass())) {
                plates.add(searchResult.getName());
            }
        }
        assertEquals(1, plates.size());
        assertEquals(plateName2, plates.get(0));
    }

    @Test
    public void searchByWorkflowDoesNotReturnExtra() throws IOException, BadDataException, DatabaseServiceException, SQLException {
        String extPlate = "Plate_M037";
        String extractionId = "MBIO24950.1.1";

        BiocodeService service = BiocodeService.getInstance();
        saveExtractionPlate(extPlate, "MBIO24950.1", extractionId, service);

        String locus = "COI";
        String plateName = "PCR_M037_COI";
        savePcrPlate(plateName, locus, service, extractionId);

        String locus2 = "16s";
        String plateName2 = "PCR_M037_16s";
        savePcrPlate(plateName2, locus2, service, extractionId);

        List<AnnotatedPluginDocument> searchResults = service.retrieve(
                Query.Factory.createFieldQuery(LIMSConnection.WORKFLOW_LOCUS_FIELD, Condition.EQUAL, new Object[]{locus}),
                ProgressListener.EMPTY);
        List<String> plates = new ArrayList<String>();
        for (AnnotatedPluginDocument searchResult : searchResults) {
            if(PlateDocument.class.isAssignableFrom(searchResult.getDocumentClass())) {
                plates.add(searchResult.getName());
            }
        }
        assertEquals(Arrays.asList(extPlate, plateName), plates);

        List<AnnotatedPluginDocument> searchResults2 = service.retrieve(
                        Query.Factory.createFieldQuery(LIMSConnection.WORKFLOW_LOCUS_FIELD, Condition.EQUAL, new Object[]{locus2}),
                        ProgressListener.EMPTY);
        plates = new ArrayList<String>();
        for (AnnotatedPluginDocument searchResult : searchResults2) {
            if(PlateDocument.class.isAssignableFrom(searchResult.getDocumentClass())) {
                plates.add(searchResult.getName());
            }
        }
        assertEquals(Arrays.asList(extPlate, plateName2), plates);
    }

    @Test
    public void searchReturnsExtractionsWithoutWorkflow() throws BadDataException, DatabaseServiceException, SQLException {
        Map<String, String> values = new HashMap<String, String>();
        values.put("MBIO24950.1", "1");
        values.put("MBIO24951.1", "2");

        BiocodeService service = BiocodeService.getInstance();
        saveExtractionPlate("MyPlate", service, values);

        savePcrPlate("PCR", "COI", service, "1");

        int workflowCount = 0;
        List<AnnotatedPluginDocument> results = service.retrieve("");
        List<ExtractionReaction> extractions = new ArrayList<ExtractionReaction>();
        for (AnnotatedPluginDocument result : results) {
            if(WorkflowDocument.class.isAssignableFrom(result.getDocumentClass())) {
                workflowCount++;
            }
            Plate extractionPlate = getPlateFromDocument(result, "MyPlate", Reaction.Type.Extraction, "1", "2");
            if(extractionPlate != null) {
                for (Reaction reaction : extractionPlate.getReactions()) {
                    if(reaction instanceof ExtractionReaction && reaction.getExtractionId() != null && reaction.getExtractionId().length() > 0) {
                        extractions.add((ExtractionReaction)reaction);
                    }
                }
            }
        }
        assertEquals(1, workflowCount);
        assertEquals(2, extractions.size());
        int extractionsWithWorkflow = 0;
        for (ExtractionReaction extraction : extractions) {
            if(extraction.getWorkflow() != null) {
                extractionsWithWorkflow++;
            }
        }
        assertEquals(1, extractionsWithWorkflow);
    }

    @Test
    public void searchReturnsPcrAndSeqReactionsWithoutWorkflow() throws BadDataException, SQLException, DocumentOperationException {
        Map<String, String> values = new HashMap<String, String>();
        values.put("MBIO24950.1", "1");
        values.put("MBIO24951.1", "2");

        BiocodeService service = BiocodeService.getInstance();
        saveExtractionPlate("MyPlate", service, values);

        for(int i=0; i<2; i++) {
            String plateName;
            Reaction.Type type;
            if(i == 0) {
                plateName = "PCR";
                type = Reaction.Type.PCR;
                savePcrPlate(plateName, "COI", service, "1", "2");
            } else {
                plateName = "SeqF";
                type = Reaction.Type.CycleSequencing;
                saveCyclesequencingPlate(plateName, "COI", CycleSequencingOptions.FORWARD_VALUE, service, null, "1", "2");
            }

            List<AnnotatedPluginDocument> results = service.retrieve(plateName);
            Plate p = null;
            for (AnnotatedPluginDocument result : results) {
                Plate plate = getPlateFromDocument(result, plateName, type, "1", "2");
                if(plate != null) {
                    p = plate;
                }
            }
            assertNotNull(p);

            // Clear the workflow from the first reaction
            String tech = "Me";
            String TECH_FIELD = "technician";
            p.getReactions()[0].setWorkflow(null);
            p.getReactions()[0].getOptions().setValue(TECH_FIELD, tech);
            Reaction.saveReactions(p.getReactions(), type, service.getActiveLIMSConnection(), ProgressListener.EMPTY);

            // Check it is still part of the plate
            results = service.retrieve(plateName);
            p = null;
            for (AnnotatedPluginDocument result : results) {
                Plate plate = getPlateFromDocument(result, plateName, type, "", "2");  // No workflow means no extraction
                if(plate != null) {
                    p = plate;
                }
            }
            assertNotNull(p);

            Reaction reaction = p.getReactions()[0];
            assertEquals(null, reaction.getWorkflow());
            assertEquals(tech, reaction.getOptions().getValueAsString(TECH_FIELD));  // Confirm that this is our reaction and not an empty one
        }
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
    private Plate getPlateFromDocument(AnnotatedPluginDocument doc, String plateName, Reaction.Type expectedType, String... expectedExtractionIds) {
        if(PlateDocument.class.isAssignableFrom(doc.getDocumentClass())) {
            PlateDocument plateDoc = (PlateDocument) doc.getDocumentOrNull();
            if(plateDoc.getPlate().getReactionType() == expectedType && plateName.equals(doc.getName())) {
                Reaction[] reactions = plateDoc.getPlate().getReactions();
                assertEquals(Plate.Size.w96.numberOfReactions(), reactions.length);
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

    private void saveExtractionPlate(String plateName, String tissue, String extractionId, BiocodeService service) throws SQLException, BadDataException {
        Map<String,String> values = Collections.singletonMap(tissue, extractionId);
        saveExtractionPlate(plateName, service, values);
    }

    private void saveExtractionPlate(String plateName, BiocodeService service, Map<String, String> values) throws SQLException, BadDataException {
        Plate extractionPlate = new Plate(Plate.Size.w96, Reaction.Type.Extraction);
        extractionPlate.setName(plateName);

        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            ExtractionReaction reaction = (ExtractionReaction)extractionPlate.getReaction(0, index++);
            reaction.setTissueId(entry.getKey());
            reaction.setExtractionId(entry.getValue());
        }

        service.saveExtractions(ProgressListener.EMPTY, extractionPlate);
    }

    private Plate savePcrPlate(String plateName, String locus, BiocodeService service, String... extractionIds) throws SQLException, BadDataException {
        Plate pcrPlate = new Plate(Plate.Size.w96, Reaction.Type.PCR);
        pcrPlate.setName(plateName);
        List<Thermocycle> thermocycle = BiocodeService.getInstance().getPCRThermocycles();
        assertFalse("No default thermocycles in the system", thermocycle.isEmpty());
        pcrPlate.setThermocycle(thermocycle.get(0));

        int index = 0;
        for (String extractionId : extractionIds) {
            PCRReaction reaction = (PCRReaction)pcrPlate.getReaction(0, index++);
            reaction.setExtractionId(extractionId);
            reaction.getOptions().setValue(LIMSConnection.WORKFLOW_LOCUS_FIELD.getCode(), locus);
        }

        service.saveReactions(ProgressListener.EMPTY, pcrPlate);
        return pcrPlate;
    }

    private void saveCyclesequencingPlate(String plateName, String locus, String direction, BiocodeService service, Plate copyReactionsFrom, String... extractionIds) throws SQLException, BadDataException, DocumentOperationException {
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

        service.saveReactions(ProgressListener.EMPTY, plate);
    }

    private String getPathToDemoFIMSExcel() {
        final URL resource = getClass().getResource("demo video FIMS.xls");
        if (resource == null) {
            throw new IllegalArgumentException("Couldn't find spreadsheet");
        }
        return resource.getFile().replace("%20", " ");
    }

    @Test
    public void plateNameSearchReturnsCompleteWorkflows() throws BadDataException, SQLException, DatabaseServiceException, DocumentOperationException {
        String extractionId = "MBIO24950.1.1";
        String seqFName = "SeqF_M037";

        BiocodeService service = BiocodeService.getInstance();
        saveExtractionPlate("Plate_M037", "MBIO24950.1", extractionId, service);
        Plate pcrPlate = savePcrPlate("PCR_M037", "COI", service, extractionId);

        saveCyclesequencingPlate(seqFName, "COI", CycleSequencingOptions.FORWARD_VALUE, service, pcrPlate, extractionId);

        Query query = Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL, new Object[]{seqFName},
                BiocodeService.getSearchDownloadOptions(false, true, false, false));
        List<AnnotatedPluginDocument> searchResults = service.retrieve(query, ProgressListener.EMPTY);
        assertEquals(1, searchResults.size());
        for (AnnotatedPluginDocument result : searchResults) {
            if(WorkflowDocument.class.isAssignableFrom(result.getDocumentClass())) {
                boolean extractionFound = false;
                boolean pcrFound = false;
                boolean cycleSeqFound = false;
                for(Reaction r : ((WorkflowDocument)result.getDocumentOrNull()).getReactions()) {
                    if(r.getType() == Reaction.Type.Extraction) {
                        extractionFound = true;
                    } else if(r.getType() == Reaction.Type.PCR) {
                        pcrFound = true;
                    } else if(r.getType() == Reaction.Type.CycleSequencing) {
                        cycleSeqFound = true;
                    }
                }
                assertTrue("Workflow doc missing extraction reaction", extractionFound);
                assertTrue("Workflow doc missing pcr reaction", pcrFound);
                assertTrue("Workflow doc missing cycle sequencing reaction", cycleSeqFound);
            } else {
                fail("Search returned " + result.getDocumentClass() + ", when all we wanted was workflows.");
            }
        }
    }

    @Test
    public void searchForSingleReactionValueReturnsFullPlate() throws BadDataException, SQLException, DatabaseServiceException {
        Map<String, String> values = new HashMap<String, String>();
        String toSearchFor = "1";
        values.put("MBIO24950.1", toSearchFor);
        values.put("MBIO24951.1", "2");

        BiocodeService service = BiocodeService.getInstance();
        saveExtractionPlate("MyPlate", service, values);

        Query query = Query.Factory.createFieldQuery(LIMSConnection.EXTRACTION_ID_FIELD, Condition.EQUAL, new Object[]{toSearchFor},
                        BiocodeService.getSearchDownloadOptions(false, false, true, false));
        List<AnnotatedPluginDocument> searchResults = service.retrieve(query, ProgressListener.EMPTY);
        assertEquals(1, searchResults.size());

        Map<String, Boolean> found = new HashMap<String, Boolean>();
        for (AnnotatedPluginDocument result : searchResults) {
            if(PlateDocument.class.isAssignableFrom(result.getDocumentClass())) {
                for (Reaction reaction : ((PlateDocument) result.getDocumentOrNull()).getPlate().getReactions()) {
                    for (Map.Entry<String, String> entry : values.entrySet()) {
                        if(reaction.getExtractionId().equals(entry.getValue())) {
                            found.put(entry.getKey(), Boolean.TRUE);
                        }
                    }
                }
            } else {
                fail("Search returned " + result.getDocumentClass() + ", when all we wanted was plates.");
            }
        }
        for (String key : values.keySet()) {
            assertTrue("Did not find " + key + " on plate", found.get(key));
        }
    }

    @Test
    public void searchByPlateReturnsTissues() throws BadDataException, SQLException, DatabaseServiceException {
        String plateName = "Plate_M037";
        String tissue = "MBIO24950.1";
        String extractionId = "MBIO24950.1.1";

        BiocodeService service = BiocodeService.getInstance();
        saveExtractionPlate(plateName, tissue, extractionId, service);

        Query query = Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL, new Object[]{plateName},
                                BiocodeService.getSearchDownloadOptions(true, false, false, false));
        List<AnnotatedPluginDocument> searchResults = service.retrieve(query, ProgressListener.EMPTY);

        assertEquals(1, searchResults.size());
        for (AnnotatedPluginDocument searchResult : searchResults) {
            if(TissueDocument.class.isAssignableFrom(searchResult.getDocumentClass())) {
                assertEquals(tissue, searchResult.getName());
            }
        }
    }

    @Test
    public void orSearchWithWrongTissueStillReturnsPlate() throws DatabaseServiceException, BadDataException, SQLException {
        testMissingTissueSearch(false);
    }

    @Test
    public void andSearchWithWrongTissueDoesNotReturnsPlate() throws DatabaseServiceException, BadDataException, SQLException {
        testMissingTissueSearch(true);
    }

    private void testMissingTissueSearch(boolean and) throws DatabaseServiceException, BadDataException, SQLException {
        String plateName = "Plate_M037";
        String tissue = "MBIO24950.1";
        String extractionId = "MBIO24950.1.1";

        BiocodeService service = BiocodeService.getInstance();
        saveExtractionPlate(plateName, tissue, extractionId, service);

        Query[] subQs = new Query[2];
        subQs[0] = Query.Factory.createFieldQuery(
                BiocodeService.getInstance().getActiveFIMSConnection().getTissueSampleDocumentField(), Condition.EQUAL, new Object[]{"abcasfa"});
        subQs[1] = Query.Factory.createFieldQuery(
                        LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL, new Object[]{plateName});
        List<AnnotatedPluginDocument> searchResults = service.retrieve(
                and ? Query.Factory.createAndQuery(subQs, Collections.<String, Object>emptyMap()) :
                        Query.Factory.createOrQuery(subQs, Collections.<String, Object>emptyMap()), ProgressListener.EMPTY);
        List<String> plates = new ArrayList<String>();
        for (AnnotatedPluginDocument searchResult : searchResults) {
            if(PlateDocument.class.isAssignableFrom(searchResult.getDocumentClass())) {
                plates.add(searchResult.getName());
            }
        }
        if(and) {
            assertTrue(plates.isEmpty());
        } else {
            assertEquals(1, plates.size());
            assertEquals(plateName, plates.get(0));
        }
    }
}
