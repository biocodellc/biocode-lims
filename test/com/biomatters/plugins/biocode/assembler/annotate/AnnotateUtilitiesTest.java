package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.sequence.DefaultNucleotideGraph;
import com.biomatters.geneious.publicapi.implementations.DefaultAlignmentDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideGraphSequence;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideSequence;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * @author Matthew Cheung
 *         Created on 24/10/14 11:39 AM
 */
public class AnnotateUtilitiesTest extends Assert {

    @Test
    public void getDirectionForTraceWorks() throws DocumentOperationException {
        testGetDirection(getTestTrace(1, true), true);
    }

    @Test
    public void getDirectionReturnsNullIfForSeqThatIsNotAChromatogram() throws DocumentOperationException {
        testGetDirection(getTestTrace(1, false), false);
    }

    @Test
    public void getDirectionReturnsNullForAlignment() throws DocumentOperationException {
        DefaultAlignmentDocument alignment = new DefaultAlignmentDocument("MyAlignment",
                new DefaultNucleotideSequence("1", "A"), new DefaultNucleotideSequence("2", "A"));
        testGetDirection(DocumentUtilities.createAnnotatedPluginDocument(alignment), false);
    }

    private void testGetDirection(AnnotatedPluginDocument document, boolean shouldReturnDirection) throws DocumentOperationException {
        assertNull(AnnotateUtilities.getDirectionForTrace(document));

        for (boolean direction : new boolean[]{true, false}) {
            document.setFieldValue(BiocodeUtilities.IS_FORWARD_FIELD, direction);
            Boolean result = AnnotateUtilities.getDirectionForTrace(document);
            if(shouldReturnDirection) {
                assertEquals(direction, result);
            } else {
                assertNull(result);
            }
        }
    }

    private AnnotatedPluginDocument getTestTrace(int length, boolean hasChromatogram) {
        char[] chars = new char[length];
        Arrays.fill(chars, 'A');

        int[] arrayWithOneValueForEachPosition = new int[length];
        List<int []> chromats = new ArrayList<int[]>();
        for(int i=0; i<4; i++) {
            chromats.add(arrayWithOneValueForEachPosition);
        }
        DefaultNucleotideGraphSequence graphSeq = new DefaultNucleotideGraphSequence("Test", "", new String(chars), new Date(),
                new DefaultNucleotideGraph(
                        hasChromatogram ? chromats.toArray(new int[4][length]) : null,
                        hasChromatogram ? arrayWithOneValueForEachPosition : null,
                        arrayWithOneValueForEachPosition,
                        length,
                        hasChromatogram ? length : 0));
        return DocumentUtilities.createAnnotatedPluginDocument(graphSeq);
    }
}
