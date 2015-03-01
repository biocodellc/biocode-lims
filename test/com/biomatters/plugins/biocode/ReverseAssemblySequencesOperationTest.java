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

import java.util.*;

/**
 * @author Gen Li
 *         Created on 26/02/15 4:54 PM
 */
public class ReverseAssemblySequencesOperationTest extends LimsTestCase {
    @Test
    public void testReverseSequence() throws DocumentOperationException, DatabaseServiceException, BadDataException {
        String sequence = "ACTG";
        String sequenceReverseComplement = "CAGT";
        int assemblyID = 1;
        int workflowID = 1;
        String locus = "COI";
        String extractionID = "1";
        LIMSConnection activeLIMSConnection = BiocodeService.getInstance().getActiveLIMSConnection();

        saveExtractionPCRAndForwardCycleSequencingPlatesToDatabase(activeLIMSConnection, extractionID, locus);
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

    private static void saveExtractionPCRAndForwardCycleSequencingPlatesToDatabase(LIMSConnection activeLIMSConnection, String extractionID, String locus) throws DatabaseServiceException, BadDataException {
        Thermocycle thermocycle = new Thermocycle();

        Plate extractionPlate = new Plate(Plate.Size.w96, Reaction.Type.Extraction);
        extractionPlate.setName("Extraction");
        ExtractionReaction extractionReaction = new ExtractionReaction();
        extractionReaction.setExtractionId(extractionID);
        Reaction[] extractionReactions = extractionPlate.getReactions();
        extractionReactions[0] = extractionReaction;
        extractionPlate.setReactions(extractionReactions);

        Plate pcrPlate = new Plate(Plate.Size.w96, Reaction.Type.PCR);
        pcrPlate.setName("PCR");
        PCRReaction pcrReaction = new PCRReaction();
        pcrReaction.setExtractionId(extractionID);
        pcrReaction.getOptions().setValue(LIMSConnection.WORKFLOW_LOCUS_FIELD.getCode(), locus);
        pcrPlate.setThermocycle(thermocycle);
        Reaction[] pcrReactions = pcrPlate.getReactions();
        pcrReactions[0] = pcrReaction;
        pcrPlate.setReactions(pcrReactions);

        Plate forwardCycleSequencingPlate = new Plate(Plate.Size.w96, Reaction.Type.CycleSequencing);
        forwardCycleSequencingPlate.setName("CSF");
        CycleSequencingReaction cycleSequencingReaction = new CycleSequencingReaction();
        cycleSequencingReaction.setExtractionId(extractionID);
        cycleSequencingReaction.getOptions().setValue(LIMSConnection.WORKFLOW_LOCUS_FIELD.getCode(), locus);
        forwardCycleSequencingPlate.setThermocycle(thermocycle);
        Reaction[] forwardCycleSequencingReactions = forwardCycleSequencingPlate.getReactions();
        forwardCycleSequencingReactions[0] = cycleSequencingReaction;
        forwardCycleSequencingPlate.setReactions(forwardCycleSequencingReactions);

        activeLIMSConnection.savePlates(Arrays.asList(extractionPlate, pcrPlate, forwardCycleSequencingPlate), ProgressListener.EMPTY);
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