package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideSequence;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.AssembledSequence;
import com.biomatters.plugins.biocode.labbench.BadDataException;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LimsTestCase;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import jebl.util.ProgressListener;
import org.junit.Test;

import java.sql.SQLException;
import java.util.*;

/**
 * @author Gen Li
 *         Created on 26/02/15 4:54 PM
 */
public class ReverseAssemblySequencesOperationTest extends LimsTestCase {
    @Test
    public void testReverseSequence() throws DocumentOperationException, DatabaseServiceException, BadDataException, SQLException {
        String sequence = "ACTG";
        String sequenceReverseComplement = "CAGT";
        int assemblyID = 1;
        int workflowID = 1;
        String locus = "COI";
        String extractionID = "1";
        LIMSConnection activeLIMSConnection = BiocodeService.getInstance().getActiveLIMSConnection();

        saveExtractionPCRAndForwardCycleSequencingPlatesToDatabase(BiocodeService.getInstance(), extractionID, locus);
        activeLIMSConnection.addAssembly(true, "", "", null, "", false, createAssembledSequenceWithSuppliedFieldValuesAndZeroOrEmptyValuesForRestOfFields(assemblyID, workflowID, locus, sequence), Arrays.asList(assemblyID), ProgressListener.EMPTY);

        AnnotatedPluginDocument annotatedSequenceDocument = DocumentUtilities.createAnnotatedPluginDocument(new DefaultNucleotideSequence("", "", sequence, new Date()));
        annotatedSequenceDocument.setFieldValue(LIMSConnection.SEQUENCE_ID, 1);

        List<AnnotatedPluginDocument> assembledSequenceDocumentsReturnedByOperation = new ReverseAssemblySequencesOperation().performOperation(
                new AnnotatedPluginDocument[]{ annotatedSequenceDocument },
                ProgressListener.EMPTY,
                new Options(getClass())
        );

        assertEquals(1, assembledSequenceDocumentsReturnedByOperation.size());
        assertEquals(sequenceReverseComplement, ((SequenceDocument)assembledSequenceDocumentsReturnedByOperation.get(0).getDocument()).getCharSequence().toString());

        List<AssembledSequence> assembledSequencesRetrievedFromLIMS = activeLIMSConnection.getAssemblySequences(Collections.singletonList(1), ProgressListener.EMPTY, true);

        assertEquals(1, assembledSequencesRetrievedFromLIMS.size());
        assertEquals(sequenceReverseComplement, assembledSequencesRetrievedFromLIMS.get(0).consensus);
    }

    private static void saveExtractionPCRAndForwardCycleSequencingPlatesToDatabase(BiocodeService service, String extractionID, String locus) throws DatabaseServiceException, BadDataException, SQLException, DocumentOperationException {
        Thermocycle thermocycle = new Thermocycle();

        saveExtractionPlate("Extraction", "tissueId" + System.currentTimeMillis(), extractionID, service);
        Plate pcrPlate = savePcrPlate("PCR", locus, thermocycle, service, extractionID);
        saveCyclesequencingPlate("CSF", locus, "forward", null, service, pcrPlate, extractionID);
    }

    private static AssembledSequence createAssembledSequenceWithSuppliedFieldValuesAndZeroOrEmptyValuesForRestOfFields(int id, int workflowID, String locus, String consensus) {
        AssembledSequence assembledSequence = new AssembledSequence();

        assembledSequence.id = id;
        assembledSequence.consensus = consensus;
        assembledSequence.assemblyNotes = "";
        assembledSequence.assemblyParameters = "";
        assembledSequence.bin = "";
        assembledSequence.confidenceScore = "";
        assembledSequence.coverage = 0.0;
        assembledSequence.date = 0L;
        assembledSequence.editRecord = "";
        assembledSequence.extractionBarcode = "";
        assembledSequence.extractionId = "1";
        assembledSequence.forwardPlate = "";
        assembledSequence.forwardPrimerName = "";
        assembledSequence.forwardPrimerSequence = "";
        assembledSequence.forwardTrimParameters = "";
        assembledSequence.limsId = id;
        assembledSequence.numberOfAmbiguities = 0;
        assembledSequence.numberOfDisagreements = 0;
        assembledSequence.numOfEdits = 0;
        assembledSequence.progress = "";
        assembledSequence.reversePlate = "";
        assembledSequence.reversePrimerName = "";
        assembledSequence.reversePrimerSequence = "";
        assembledSequence.reverseTrimParameters = "";
        assembledSequence.sampleId = "";
        assembledSequence.submitted = true;
        assembledSequence.technician = "";
        assembledSequence.workflowId = workflowID;
        assembledSequence.workflowLocus = locus;
        assembledSequence.workflowName = "";

        return assembledSequence;
    }
}