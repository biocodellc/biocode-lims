package com.biomatters.plugins.biocode.submission.bold;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideSequence;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.TestGeneious;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingReaction;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;
import com.biomatters.plugins.biocode.labbench.reaction.PCRReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.google.common.collect.Multimap;
import jebl.util.ProgressListener;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Matthew Cheung
 *         Created on 27/08/14 12:21 PM
 */
public class BOLDTraceSubmissionTest extends Assert {

    @Test
    public void canGetExtractFieldValues() throws MissingFieldValueException {
        TestGeneious.initialize();
        String sameValue = "same";
        String diffValue = "diff";

        AnnotatedPluginDocument sameValueDoc1 = DocumentUtilities.createAnnotatedPluginDocument(new DefaultNucleotideSequence("test", "ACTG"));
        sameValueDoc1.setFieldValue(DocumentField.ORGANISM_FIELD, sameValue);
        AnnotatedPluginDocument sameValueDoc2 = DocumentUtilities.createAnnotatedPluginDocument(new DefaultNucleotideSequence("test", "ACTG"));
        sameValueDoc2.setFieldValue(DocumentField.ORGANISM_FIELD, sameValue);
        AnnotatedPluginDocument diffValueDoc = DocumentUtilities.createAnnotatedPluginDocument(new DefaultNucleotideSequence("test", "ACTG"));
        diffValueDoc.setFieldValue(DocumentField.ORGANISM_FIELD, diffValue);

        Multimap<String, AnnotatedPluginDocument> result = GenerateBOLDTraceSubmissionOperation.getFieldValuesFromDocs(
                new AnnotatedPluginDocument[]{sameValueDoc1, sameValueDoc2, diffValueDoc}, DocumentField.ORGANISM_FIELD);
        Collection<AnnotatedPluginDocument> sameList = result.get(sameValue);
        assertEquals(2, sameList.size());
        assertTrue(sameList.contains(sameValueDoc1));
        assertTrue(sameList.contains(sameValueDoc2));
        Collection<AnnotatedPluginDocument> diffList = result.get(diffValue);
        assertEquals(1, diffList.size());
        assertTrue(diffList.contains(diffValueDoc));
    }

    @Test(expected = MissingFieldValueException.class)
    public void throwsExceptionIfFieldValueMissing() throws MissingFieldValueException {
        TestGeneious.initialize();
        AnnotatedPluginDocument testDoc = DocumentUtilities.createAnnotatedPluginDocument(new DefaultNucleotideSequence("test", "ACTG"));
        GenerateBOLDTraceSubmissionOperation.getFieldValuesFromDocs(new AnnotatedPluginDocument[] {testDoc}, DocumentField.ORGANISM_FIELD);
    }

    @Test
    public void canWriteOutTraceEntries() throws IOException, DocumentOperationException, BiffException {
        File tempDir = FileUtilities.createTempDir(true);
        List<TraceInfo> infoList = new ArrayList<TraceInfo>();
        infoList.add(new TraceInfo("myTrace.ab1", "LCO", "HCO", "", true, "abc", "COI"));
        infoList.add(new TraceInfo("myTrace1.ab1", "LCO", "HCO", "", false, "abc", "COI"));
        infoList.add(new TraceInfo("myTrace2.ab1", "fwd", "rev", "", true, "abc", "ITS"));

        GenerateBOLDTraceSubmissionOperation.createTracesSpreadsheet(infoList, null, tempDir, ProgressListener.EMPTY);
        File expectedFile = new File(tempDir, "data.xls");
        assertTrue("Spreadsheet does not exist or is named incorrectly. " +
                "Must be named data.xls according to BOLD specification", expectedFile.exists());

        Workbook workbook = Workbook.getWorkbook(expectedFile);
        Sheet writtenSheet = workbook.getSheet(0);
        assertRowIsAsExpected(Arrays.asList("Filename (.ab1)", "Score File (.phd.1)", "FORWARD  PCR PRIMER", "REVERSE PCR PRIMER",
                "SEQUENCING PRIMER", "Read Direction", "Process ID", "", "", "Marker"), writtenSheet, 0);
        assertRowIsAsExpected(Arrays.asList("myTrace.ab1", "", "LCO", "HCO", "", "F", "abc", "", "", "COI"), writtenSheet, 1);
        assertRowIsAsExpected(Arrays.asList("myTrace1.ab1", "", "LCO", "HCO", "", "R", "abc", "", "", "COI"), writtenSheet, 2);
        assertRowIsAsExpected(Arrays.asList("myTrace2.ab1", "", "fwd", "rev", "", "F", "abc", "", "", "ITS"), writtenSheet, 3);
    }

    void assertRowIsAsExpected(List<String> expectedRow, Sheet writtenSheet, int rowNum) {
        List<String> rowContents = new ArrayList<String>();
        for(int i=0; i<writtenSheet.getColumns(); i++) {
            rowContents.add(writtenSheet.getCell(i, rowNum).getContents());
        }
        assertEquals(expectedRow, rowContents);
    }

    @Test
    public void doesNotEditTemplateSpreadsheet() throws IOException, DocumentOperationException {
        File template = GenerateBOLDTraceSubmissionOperation.getTemplateSpreadsheet();
        String oldText = FileUtilities.getTextFromFile(template);

        File tempDir = FileUtilities.createTempDir(true);
        GenerateBOLDTraceSubmissionOperation.createTracesSpreadsheet(Collections.singletonList(
                new TraceInfo("myTrace.ab1", "LCO", "HCO", "", true, "abc", "COI")), null, tempDir, ProgressListener.EMPTY);

        assertEquals(oldText, FileUtilities.getTextFromFile(template));
    }

    @Test
    public void canGetPcrReactionFromWorkflow() {
        GregorianCalendar cal = new GregorianCalendar();
        Date start = cal.getTime();

        Workflow workflow = new Workflow(1, "test", "", "", start);
        Reaction extraction = getReactionWithCreationDate(start, Reaction.Type.Extraction);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        Reaction pcr = getReactionWithCreationDate(cal.getTime(), Reaction.Type.PCR);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        Reaction seq = getReactionWithCreationDate(cal.getTime(), Reaction.Type.CycleSequencing);

        assertEquals(pcr, GenerateBOLDTraceSubmissionOperation.getMostLikelyPcrReactionForSeqReaction(
                new WorkflowDocument(workflow, Arrays.asList(extraction, pcr, seq)), seq));
    }

    @Test
    public void doesNotGetReactionsOfWrongType() {
        GregorianCalendar cal = new GregorianCalendar();
        Date start = cal.getTime();

        Workflow workflow = new Workflow(1, "test", "", "", start);
        Reaction extraction = getReactionWithCreationDate(start, Reaction.Type.Extraction);

        cal.add(Calendar.DAY_OF_MONTH, 1);
        Reaction seq = getReactionWithCreationDate(cal.getTime(), Reaction.Type.CycleSequencing);

        assertEquals(null, GenerateBOLDTraceSubmissionOperation.getMostLikelyPcrReactionForSeqReaction(
                new WorkflowDocument(workflow, Arrays.asList(extraction, seq)), seq));
    }

    @Test
    public void doesNotGetPcrReactionCreatedInFuture() {
        GregorianCalendar cal = new GregorianCalendar();
        Date start = cal.getTime();

        Workflow workflow = new Workflow(1, "test", "", "", start);
        Reaction extraction = getReactionWithCreationDate(start, Reaction.Type.Extraction);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        Reaction pcr = getReactionWithCreationDate(cal.getTime(), Reaction.Type.PCR);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        Reaction seq = getReactionWithCreationDate(cal.getTime(), Reaction.Type.CycleSequencing);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        Reaction futurePcr = getReactionWithCreationDate(cal.getTime(), Reaction.Type.PCR);

        assertEquals(pcr, GenerateBOLDTraceSubmissionOperation.getMostLikelyPcrReactionForSeqReaction(
                new WorkflowDocument(workflow, Arrays.asList(extraction, pcr, seq, futurePcr)), seq));

        assertEquals(null, GenerateBOLDTraceSubmissionOperation.getMostLikelyPcrReactionForSeqReaction(
                        new WorkflowDocument(workflow, Arrays.asList(extraction, seq, futurePcr)), seq));
    }

    @Test
    public void getsMostRecentPcrReaction() {
        GregorianCalendar cal = new GregorianCalendar();
        Date start = cal.getTime();

        Workflow workflow = new Workflow(1, "test", "", "", start);
        Reaction extraction = getReactionWithCreationDate(start, Reaction.Type.Extraction);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        Reaction pcr = getReactionWithCreationDate(cal.getTime(), Reaction.Type.PCR);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        Reaction recentPcr = getReactionWithCreationDate(cal.getTime(), Reaction.Type.PCR);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        Reaction seq = getReactionWithCreationDate(cal.getTime(), Reaction.Type.CycleSequencing);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        assertEquals(recentPcr, GenerateBOLDTraceSubmissionOperation.getMostLikelyPcrReactionForSeqReaction(
                new WorkflowDocument(workflow, Arrays.asList(extraction, pcr, seq, recentPcr)), seq));
    }

    @Test
    public void reactionDateCheckIgnoresTime() {
        GregorianCalendar cal = new GregorianCalendar();
        Date start = cal.getTime();

        Workflow workflow = new Workflow(1, "test", "", "", start);
        cal.set(Calendar.HOUR, 11);
        Reaction seq = getReactionWithCreationDate(cal.getTime(), Reaction.Type.CycleSequencing);
        cal.set(Calendar.HOUR, 10);
        Reaction pcr = getReactionWithCreationDate(cal.getTime(), Reaction.Type.PCR);

        assertEquals(pcr, GenerateBOLDTraceSubmissionOperation.getMostLikelyPcrReactionForSeqReaction(
                new WorkflowDocument(workflow, Arrays.asList(pcr, seq)), seq));
    }

    private static Reaction getReactionWithCreationDate(final Date creationDate, Reaction.Type type) {
        if(type == Reaction.Type.PCR) {
            return new PCRReaction() {
                @Override
                public Date getDate() {
                    return creationDate;
                }
            };
        } else if(type == Reaction.Type.CycleSequencing) {
            return new CycleSequencingReaction() {
                @Override
                public Date getDate() {
                    return creationDate;
                }
            };
        } else {
            return new ExtractionReaction() {
                @Override
                public Date getDate() {
                    return creationDate;
                }
            };
        }
    }
}
