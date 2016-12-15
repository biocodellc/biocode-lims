package com.biomatters.plugins.biocode.assembler.verify;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideSequence;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Geneious;
import com.biomatters.geneious.publicapi.plugin.TestGeneious;
import jebl.util.ProgressListener;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class VerifyTaxonomyTest {

    @Before
    public void setUp() {
        TestGeneious.initialize();
        TestGeneious.initializeAllPlugins();
    }

    @Test
    public void verifyTaxonomy_runDefaultBlast_resultDocumentContainsTaxonomy() throws DocumentOperationException {
        // This test doesn't test exactly whether the default database is correct, but at least we know that it works at all
        if (Geneious.getMajorVersion().compareTo(Geneious.MajorVersion.forVersion("9.0")) < 0) {
            return; // blast only works in 8.1.9 or newer because of the https change, so don't bother testing it in old versions.
        }
        String sequence = "GAAGTCGTAACAAGGTAGCCGTATCGGAAGGTGCGGCTGGATCACCTCCTTTCTAAGGAAAAGGAAACCTGTGAGTTTTCGTTCTTCT" +
                "CTATTTGTTCAGTTTTGAGAGGTTAGTACTTCTCAGTATGTTTGTTCTTTGAAAACTAGATAAGAAAGTTAGTAAAGTTAGCATAGATAATTTATTAT" +
                "TTATGACACAAGTAACCGAGAATCATCTGAAAGTGAATCTTTCATCTGATTGGATGTATCATCGCTGATACGGAAAATCAGAAAAACAACCTTTACTT" +
                "CGTAGAAGTAAATTGGTTAAGTTAGAAAGGGCGCACGGTGGATGCCTTG";
        DefaultNucleotideSequence someFirmicutes = new DefaultNucleotideSequence("KC179818", sequence);
        String taxonomy = "Bacteria; Firmicutes; Bacilli; Bacillales; Listeriaceae; Listeria";
        someFirmicutes.setFieldValue(DocumentField.TAXONOMY_FIELD, taxonomy);
        AnnotatedPluginDocument query = DocumentUtilities.createAnnotatedPluginDocument(someFirmicutes);
        AnnotatedPluginDocument[] documents = {query};
        VerifyTaxonomyOptions options = new VerifyTaxonomyOptions(documents);
        List<AnnotatedPluginDocument> results = new VerifyTaxonomyOperation().performOperation(documents, ProgressListener.EMPTY, options);
        List<AnnotatedPluginDocument> hitDocuments = ((VerifyTaxonomyResultsDocument) results.get(0).getDocument()).getResults().get(0).hitDocuments;
        assertEquals(5, hitDocuments.size());
        assertEquals(taxonomy, hitDocuments.get(0).getFieldValue(DocumentField.TAXONOMY_FIELD));
    }
}
