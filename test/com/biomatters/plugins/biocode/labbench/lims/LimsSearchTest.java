package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import jebl.util.ProgressListener;
import org.junit.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 4/03/14 3:36 PM
 */
public class LimsSearchTest extends LimsTestCase {
    @Test
    public void basicExtractionSearch() throws IOException, BadDataException, DatabaseServiceException {
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
    public void extractionIdSearch() throws BadDataException, SQLException, DatabaseServiceException, DocumentOperationException {
        String extractionIdToFind = "2.1";

        BiocodeService service = BiocodeService.getInstance();
        saveExtractionPlate("Plate_01", "1", "1.1", service);
        saveExtractionPlate("Plate_02", "2", extractionIdToFind, service);

        List<AnnotatedPluginDocument> searchResults = service.retrieve(Query.Factory.createFieldQuery(
                LIMSConnection.EXTRACTION_ID_FIELD, Condition.EQUAL, new Object[]{extractionIdToFind},
                BiocodeService.getSearchDownloadOptions(false, false, true, false)
        ), ProgressListener.EMPTY);
        assertEquals(1, searchResults.size());
        AnnotatedPluginDocument doc = searchResults.get(0);
        assertEquals(extractionIdToFind, ((PlateDocument) doc.getDocument()).getPlate().getReactions()[0].getExtractionId());
    }

    @Test
    public void pcrAndWorkflowSearch() throws BadDataException, DatabaseServiceException, SQLException {
        String tissue = "MBIO24950.1";
        String extractionId = "MBIO24950.1.1";

        BiocodeService service = BiocodeService.getInstance();
        saveExtractionPlate("Plate_M037", tissue, extractionId, service);

        String plateName = "PCR_M037";
        String locus = "COI";
        savePcrPlate(plateName, locus, null, service, extractionId);

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
    public void cyclesequencingSearch() throws BadDataException, DatabaseServiceException, DocumentOperationException, SQLException {
        String extractionId = "MBIO24950.1.1";
        String seqFName = "SeqF_M037";
        String seqRName = "SeqR_M037";

        BiocodeService service = BiocodeService.getInstance();
        saveExtractionPlate("Plate_M037", "MBIO24950.1", extractionId, service);
        Plate pcrPlate = savePcrPlate("PCR_M037", "COI", null, service, extractionId);

        saveCyclesequencingPlate(seqFName, "COI", CycleSequencingOptions.FORWARD_VALUE, null, service, pcrPlate, extractionId);
        saveCyclesequencingPlate(seqRName, "COI", CycleSequencingOptions.REVERSE_VALUE, null, service, pcrPlate, extractionId);

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
        BiocodeService service = BiocodeService.getInstance();
        String extPlate = "Plate_M037";
        String extractionId = "MBIO24950.1.1";
        String tissueId = "MBIO24950.1";
        saveExtractionPlate(extPlate, tissueId, extractionId, service);

        saveExtractionPlate("Plate_M038", "1", "1.1", service);

        String locus = "COI";
        String plateName = "PCR_M037_COI";
        savePcrPlate(plateName, locus, null, service, extractionId);

        String locus2 = "16s";
        String plateName2 = "PCR_M037_16s";
        savePcrPlate(plateName2, locus2, null, service, extractionId);

        List<AnnotatedPluginDocument> searchResults = service.retrieve(
                Query.Factory.createFieldQuery(LIMSConnection.WORKFLOW_LOCUS_FIELD, Condition.EQUAL, new Object[]{locus}),
                ProgressListener.EMPTY);
        List<String> plates = new ArrayList<String>();
        List<String> tissues = new ArrayList<String>();
        for (AnnotatedPluginDocument searchResult : searchResults) {
            if(PlateDocument.class.isAssignableFrom(searchResult.getDocumentClass())) {
                plates.add(searchResult.getName());
            } else if(TissueDocument.class.isAssignableFrom(searchResult.getDocumentClass())) {
                tissues.add(searchResult.getName());
            }
        }
        assertEquals(Arrays.asList("MBIO24950.1"), tissues);
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

        savePcrPlate("PCR", "COI", null, service, "1");

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
    public void searchReturnsPcrAndSeqReactionsWithoutWorkflow() throws BadDataException, SQLException, DocumentOperationException, DatabaseServiceException {
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
                savePcrPlate(plateName, "COI", null, service, "1", "2");
            } else {
                plateName = "SeqF";
                type = Reaction.Type.CycleSequencing;
                saveCyclesequencingPlate(plateName, "COI", CycleSequencingOptions.FORWARD_VALUE, null, service, null, "1", "2");
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
            service.getActiveLIMSConnection().saveReactions(p.getReactions(), type, ProgressListener.EMPTY);

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

    @Test
    public void plateNameSearchReturnsCompleteWorkflows() throws BadDataException, SQLException, DatabaseServiceException, DocumentOperationException {
        String extractionId = "MBIO24950.1.1";
        String seqFName = "SeqF_M037";

        BiocodeService service = BiocodeService.getInstance();
        saveExtractionPlate("Plate_M037", "MBIO24950.1", extractionId, service);
        Plate pcrPlate = savePcrPlate("PCR_M037", "COI", null, service, extractionId);

        saveCyclesequencingPlate(seqFName, "COI", CycleSequencingOptions.FORWARD_VALUE, null, service, pcrPlate, extractionId);

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

    @Test
    public void searchPlatesLastModifiedBeforeExtractionDate() throws DatabaseServiceException, BadDataException, SQLException {
        BiocodeService service = BiocodeService.getInstance();

        String tissue = "MBIO24950.1";
        String extractionId = "MBIO24950.1.1";

        saveExtractionPlate("Plate_M037", tissue, extractionId, service);

        String plateName = "PCR_M037";
        String locus = "COI";
        savePcrPlate(plateName, locus, null, service, extractionId);

        Calendar cal = new GregorianCalendar();
        cal.add(GregorianCalendar.DAY_OF_MONTH, 1);
        Query query = Query.Factory.createFieldQuery(LIMSConnection.EXTRACTION_DATE_FIELD, Condition.DATE_BEFORE, new Object[] { cal.getTime() },
                BiocodeService.getSearchDownloadOptions(false, false, true, false));
        List<AnnotatedPluginDocument> searchResults = service.retrieve(query, ProgressListener.EMPTY);
        assertEquals(2, searchResults.size());
    }

    @Test
    public void searchPlatesLastModifiedBeforeOrOnExtractionDate() throws DatabaseServiceException, BadDataException, SQLException {
        BiocodeService service = BiocodeService.getInstance();

        String tissue = "MBIO24950.1";
        String extractionId = "MBIO24950.1.1";

        saveExtractionPlate("Plate_M037", tissue, extractionId, service);

        String tissue2 = "MBIO24950.2";
        String extractionId2 = "MBIO24950.2.2";

        Calendar cal = new GregorianCalendar();
        cal.add(GregorianCalendar.DAY_OF_MONTH, -1);

        saveExtractionPlate("Plate_M038", tissue2, extractionId2, service, cal.getTime());

        cal.add(GregorianCalendar.DAY_OF_MONTH, 1);
        Query query = Query.Factory.createFieldQuery(LIMSConnection.EXTRACTION_DATE_FIELD, Condition.DATE_BEFORE_OR_ON, new Object[] { cal.getTime() },
                BiocodeService.getSearchDownloadOptions(false, false, true, false));
        List<AnnotatedPluginDocument> searchResults = service.retrieve(query, ProgressListener.EMPTY);
        assertEquals(2, searchResults.size());
    }

    @Test
    public void searchPlatesLastModifiedAfterOrOnExtractionDate() throws DatabaseServiceException, BadDataException, SQLException {
        BiocodeService service = BiocodeService.getInstance();

        String tissue = "MBIO24950.1";
        String extractionId = "MBIO24950.1.1";

        saveExtractionPlate("Plate_M037", tissue, extractionId, service);

        String tissue2 = "MBIO24950.2";
        String extractionId2 = "MBIO24950.2.2";

        Calendar cal = new GregorianCalendar();
        cal.add(GregorianCalendar.DAY_OF_MONTH, -1);

        saveExtractionPlate("Plate_M038", tissue2, extractionId2, service, cal.getTime());

        Query query = Query.Factory.createFieldQuery(LIMSConnection.EXTRACTION_DATE_FIELD, Condition.DATE_AFTER_OR_ON, new Object[] { cal.getTime() },
                BiocodeService.getSearchDownloadOptions(false, false, true, false));
        List<AnnotatedPluginDocument> searchResults = service.retrieve(query, ProgressListener.EMPTY);
        assertEquals(2, searchResults.size());
    }

    @Test
    public void searchPlatesLastModifiedAfterExtractionDate() throws DatabaseServiceException, BadDataException, SQLException {
        BiocodeService service = BiocodeService.getInstance();

        String tissue = "MBIO24950.1";
        String extractionId = "MBIO24950.1.1";

        saveExtractionPlate("Plate_M037", tissue, extractionId, service);

        String plateName = "PCR_M037";
        String locus = "COI";
        savePcrPlate(plateName, locus, null, service, extractionId);

        Calendar cal = new GregorianCalendar();
        cal.add(GregorianCalendar.DAY_OF_MONTH, -1);
        Query query = Query.Factory.createFieldQuery(LIMSConnection.EXTRACTION_DATE_FIELD, Condition.DATE_AFTER, new Object[] { cal.getTime() },
                BiocodeService.getSearchDownloadOptions(false, false, true, false));
        List<AnnotatedPluginDocument> searchResults = service.retrieve(query, ProgressListener.EMPTY);
        assertEquals(2, searchResults.size());
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

    @Test
    public void searchPlatesLastModifiedBeforeDate() throws DatabaseServiceException, BadDataException, SQLException {
        BiocodeService service = BiocodeService.getInstance();

        String tissue = "MBIO24950.1";
        String extractionId = "MBIO24950.1.1";

        saveExtractionPlate("Plate_M037", tissue, extractionId, service);

        String plateName = "PCR_M037";
        String locus = "COI";
        savePcrPlate(plateName, locus, null, service, extractionId);

        Calendar cal = new GregorianCalendar();
        cal.add(GregorianCalendar.DAY_OF_MONTH, 1);
        Query query = Query.Factory.createFieldQuery(LIMSConnection.PLATE_DATE_FIELD, Condition.DATE_BEFORE, new Object[] { cal.getTime() },
                BiocodeService.getSearchDownloadOptions(false, false, true, false));
        List<AnnotatedPluginDocument> searchResults = service.retrieve(query, ProgressListener.EMPTY);
        assertEquals(2, searchResults.size());
    }

    @Test
    public void searchPlatesLastModifiedBeforeOrOnDate() throws DatabaseServiceException, BadDataException, SQLException {
        BiocodeService service = BiocodeService.getInstance();

        String tissue = "MBIO24950.1";
        String extractionId = "MBIO24950.1.1";

        saveExtractionPlate("Plate_M037", tissue, extractionId, service);

        String tissue2 = "MBIO24950.2";
        String extractionId2 = "MBIO24950.2.2";

        Calendar cal = new GregorianCalendar();
        cal.add(GregorianCalendar.DAY_OF_MONTH, -1);

        saveExtractionPlate("Plate_M038", tissue2, extractionId2, service, cal.getTime());

        cal.add(GregorianCalendar.DAY_OF_MONTH, 1);
        Query query = Query.Factory.createFieldQuery(LIMSConnection.PLATE_DATE_FIELD, Condition.DATE_BEFORE_OR_ON, new Object[] { cal.getTime() },
                BiocodeService.getSearchDownloadOptions(false, false, true, false));
        List<AnnotatedPluginDocument> searchResults = service.retrieve(query, ProgressListener.EMPTY);
        assertEquals(2, searchResults.size());
    }

    @Test
    public void searchPlatesLastModifiedAfterOrOnDate() throws DatabaseServiceException, BadDataException, SQLException {
        BiocodeService service = BiocodeService.getInstance();

        String tissue = "MBIO24950.1";
        String extractionId = "MBIO24950.1.1";

        saveExtractionPlate("Plate_M037", tissue, extractionId, service);

        String tissue2 = "MBIO24950.2";
        String extractionId2 = "MBIO24950.2.2";

        Calendar cal = new GregorianCalendar();
        cal.add(GregorianCalendar.DAY_OF_MONTH, -1);

        saveExtractionPlate("Plate_M038", tissue2, extractionId2, service, cal.getTime());

        Query query = Query.Factory.createFieldQuery(LIMSConnection.PLATE_DATE_FIELD, Condition.DATE_AFTER_OR_ON, new Object[] { cal.getTime() },
                BiocodeService.getSearchDownloadOptions(false, false, true, false));
        List<AnnotatedPluginDocument> searchResults = service.retrieve(query, ProgressListener.EMPTY);
        assertEquals(2, searchResults.size());
    }

    @Test
    public void searchPlatesLastModifiedAfterDate() throws DatabaseServiceException, BadDataException, SQLException {
        BiocodeService service = BiocodeService.getInstance();

        String tissue = "MBIO24950.1";
        String extractionId = "MBIO24950.1.1";

        saveExtractionPlate("Plate_M037", tissue, extractionId, service);

        String plateName = "PCR_M037";
        String locus = "COI";
        savePcrPlate(plateName, locus, null, service, extractionId);

        Calendar cal = new GregorianCalendar();
        cal.add(GregorianCalendar.DAY_OF_MONTH, -1);
        Query query = Query.Factory.createFieldQuery(LIMSConnection.PLATE_DATE_FIELD, Condition.DATE_AFTER, new Object[] { cal.getTime() },
                BiocodeService.getSearchDownloadOptions(false, false, true, false));
        List<AnnotatedPluginDocument> searchResults = service.retrieve(query, ProgressListener.EMPTY);
        assertEquals(2, searchResults.size());
    }

    @Test
    public void multipleSearchesNoExhaustSQLConnectionPool() throws DatabaseServiceException, BadDataException, SQLException {
        BiocodeService service = BiocodeService.getInstance();

        String tissue = "MBIO24950.1";
        String extractionId = "MBIO24950.1.1";

        saveExtractionPlate("Plate_M037", tissue, extractionId, service);

        String plateName = "PCR_M037";
        String locus = "COI";
        savePcrPlate(plateName, locus, null, service, extractionId);

        Calendar cal = new GregorianCalendar();
        cal.add(GregorianCalendar.DAY_OF_MONTH, -1);
        Query query = Query.Factory.createFieldQuery(LIMSConnection.PLATE_DATE_FIELD, Condition.DATE_AFTER, new Object[] { cal.getTime() },
                BiocodeService.getSearchDownloadOptions(false, false, true, false));

        for (int i = 0; i < 30; i++) {
            List<AnnotatedPluginDocument> searchResults = service.retrieve(query, ProgressListener.EMPTY);
            assertEquals(2, searchResults.size());
        }
    }

    @Test
    public void canFindPcrPlateWithNoWorkflows() throws DatabaseServiceException, BadDataException, SQLException {
        BiocodeService service = BiocodeService.getInstance();
        saveExtractionPlate("Plate_M037", "MBIO24950.1", "1", service);
        Plate pcrPlate = savePcrPlate("PCR_M037", "COI", null, service, "1");
        for (Reaction reaction : pcrPlate.getReactions()) {
            reaction.setExtractionId("");
            reaction.setWorkflow(null);
        }
        service.savePlate(pcrPlate, ProgressListener.EMPTY);

        List<AnnotatedPluginDocument> results = service.retrieve(
                Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL,
                        new Object[]{pcrPlate.getName()}, BiocodeService.getSearchDownloadOptions(false, false, true, false)),
                ProgressListener.EMPTY);
        assertEquals(1, results.size());
        Plate plateFromSearch = getPlateFromDocument(results.get(0), pcrPlate.getName(), Reaction.Type.PCR);
        assertNotNull(plateFromSearch);
    }

    @Test
    public void canFindSequencingPlateWithNoWorkflows() throws DatabaseServiceException, BadDataException, SQLException, DocumentOperationException {
        BiocodeService service = BiocodeService.getInstance();
        saveExtractionPlate("Plate_M037", "MBIO24950.1", "1", service);
        Plate pcrPlate = savePcrPlate("PCR_M037", "COI", null, service, "1");
        Plate seqPlate = saveCyclesequencingPlate("SeqF_M037", "COI", "forward", null, service, pcrPlate, "1");
        for (Reaction reaction : seqPlate.getReactions()) {
            reaction.setExtractionId("");
            reaction.setWorkflow(null);
        }
        service.savePlate(seqPlate, ProgressListener.EMPTY);

        List<AnnotatedPluginDocument> results = service.retrieve(
                Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL,
                        new Object[]{seqPlate.getName()}, BiocodeService.getSearchDownloadOptions(false, false, true, false)),
                ProgressListener.EMPTY);
        assertEquals(1, results.size());
        Plate plateFromSearch = getPlateFromDocument(results.get(0), seqPlate.getName(), Reaction.Type.CycleSequencing);
        assertNotNull(plateFromSearch);
    }

    @Test
    public void canSearchByPlateNameFims() throws DatabaseServiceException, BadDataException {
        BiocodeService service = BiocodeService.getInstance();
        String plateName = "M037_Extr";
        saveExtractionPlate(plateName, "MBIO24950.1", "1", service);

        List<AnnotatedPluginDocument> results = service.retrieve(
                Query.Factory.createFieldQuery(service.getActiveFIMSConnection().getPlateDocumentField(), Condition.CONTAINS,
                        new Object[]{"M037"}, BiocodeService.getSearchDownloadOptions(false, false, true, false)),
                ProgressListener.EMPTY);
        assertEquals(1, results.size());
        Plate plateFromSearch = getPlateFromDocument(results.get(0), plateName, Reaction.Type.Extraction, "1");
        assertNotNull(plateFromSearch);
    }

    @Test
    public void canSearchByPlateNameLims() throws DatabaseServiceException, BadDataException {
        BiocodeService service = BiocodeService.getInstance();
        String plateName = "M037_Extr";
        saveExtractionPlate(plateName, "MBIO24950.1", "1", service);

        List<AnnotatedPluginDocument> results = service.retrieve(
                Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.CONTAINS,
                        new Object[]{"M037"}, BiocodeService.getSearchDownloadOptions(false, false, true, false)),
                ProgressListener.EMPTY);
        assertEquals(1, results.size());
        Plate plateFromSearch = getPlateFromDocument(results.get(0), plateName, Reaction.Type.Extraction, "1");
        assertNotNull(plateFromSearch);
    }

    @Test
    public void canUseCompoundSearchesAcrossFimsAndLims() throws DatabaseServiceException, BadDataException {
        BiocodeService service = BiocodeService.getInstance();
        String m037Plate = "M037_Extr";
        saveExtractionPlate(m037Plate, "MBIO24950.1", "1", service);

        String m038Plate = "M038_Extr";
        saveExtractionPlate(m038Plate, "MBIO819375.1", "2", service);


        Map<String, Object> searchOptions = BiocodeService.getSearchDownloadOptions(false, false, true, false);
        Query containsFimsPlate = Query.Factory.createFieldQuery(service.getActiveFIMSConnection().getPlateDocumentField(), Condition.CONTAINS,
                new Object[]{"M037"}, searchOptions);
        Query containsLimsPlate = Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.CONTAINS,
                        new Object[]{"M038"}, searchOptions);

        List<AnnotatedPluginDocument> results = service.retrieve(
                Query.Factory.createOrQuery(new Query[]{containsFimsPlate, containsLimsPlate}, searchOptions),
                ProgressListener.EMPTY);
        assertEquals(2, results.size());
        assertPlateExistsInResults(results, m037Plate, Reaction.Type.Extraction, "1");
        assertPlateExistsInResults(results, m038Plate, Reaction.Type.Extraction, "2");

        results = service.retrieve(
                        Query.Factory.createAndQuery(new Query[]{containsFimsPlate, containsLimsPlate}, searchOptions),
                        ProgressListener.EMPTY);
        assertEquals(0, results.size());
    }

    private void assertPlateExistsInResults(List<AnnotatedPluginDocument> results, String plateName, Reaction.Type type, String... extractionIds) {
        int count = 0;
        for (AnnotatedPluginDocument result : results) {
            if(getPlateFromDocument(result, plateName, type, extractionIds) != null) {
                count++;
            }
        }
        assertEquals(1, count);
    }
}