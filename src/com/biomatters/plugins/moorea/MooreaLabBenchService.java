package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.Icons;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.net.URL;
import java.net.URISyntaxException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.jdom.input.SAXBuilder;
import org.jdom.Element;
import org.jdom.JDOMException;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 23/02/2009 4:41:26 PM
 */
public class MooreaLabBenchService extends DatabaseService {
    public QueryField[] getSearchFields() {
        return new QueryField[]{
                new QueryField(new DocumentField("Specimen ID", "", "SpecimenId", String.class, true, false), new Condition[] {Condition.EQUAL, Condition.APPROXIMATELY_EQUAL}),
                new QueryField(new DocumentField("Sample ID", "", "SampleId", String.class, true, false), new Condition[] {Condition.EQUAL, Condition.APPROXIMATELY_EQUAL}),
                new QueryField(new DocumentField("Experiment ID", "", "ExperimentId", String.class, true, false), new Condition[] {Condition.EQUAL, Condition.APPROXIMATELY_EQUAL})
        };
    }

    public void retrieve(Query query, RetrieveCallback callback, URN[] urnsToNotRetrieve) throws DatabaseServiceException {
        ExperimentDocument document = new ExperimentDocument("PL005");

        //add notes
        DocumentNoteType specimen = DocumentNoteUtilities.getNoteType("moorea_specimem");
        if(specimen == null) {
            specimen = DocumentNoteUtilities.createNewNoteType("Specimen", "moorea_specimem", "", Arrays.asList(DocumentNoteField.createTextNoteField("Organism", "", "organism", Collections.EMPTY_LIST, false)), true);
            DocumentNoteUtilities.setNoteType(specimen);
        }
        DocumentNoteType sample = DocumentNoteUtilities.getNoteType("moorea_sample");
        if(sample == null) {
            sample = DocumentNoteUtilities.createNewNoteType("Tissue Sample", "moorea_sample", "", Arrays.asList(DocumentNoteField.createTextNoteField("Sample ID", "", "id", Collections.EMPTY_LIST, false), DocumentNoteField.createIntegerNoteField("Freezer", "", "freezer", Collections.EMPTY_LIST, false), DocumentNoteField.createIntegerNoteField("Shelf", "", "shelf", Collections.EMPTY_LIST, false)), true);
            DocumentNoteUtilities.setNoteType(sample);
        }
        DocumentNoteType extraction = DocumentNoteUtilities.getNoteType("moorea_extraction");
        if(extraction == null) {
            extraction = DocumentNoteUtilities.createNewNoteType("DNA Extraction", "moorea_extraction", "", Arrays.asList(
                    DocumentNoteField.createTextNoteField("Plate ID", "", "plate", Collections.EMPTY_LIST, false),
                    DocumentNoteField.createDateNoteField("Extraction Date", "", "date", Collections.EMPTY_LIST, false),
                    DocumentNoteField.createTextNoteField("Extracted By", "", "extractor", Collections.EMPTY_LIST, false),
                    DocumentNoteField.createEnumeratedNoteField(new String[] {"Method 1", "Method 2", "Method 3"}, "Extraction Method", "", "plate", false),
                    DocumentNoteField.createIntegerNoteField("Well Location", "", "location", Collections.EMPTY_LIST, false),
                    DocumentNoteField.createTextNoteField("Suspension Solution", "", "solution", Collections.EMPTY_LIST, false),
                    DocumentNoteField.createTextNoteField("Plate ID", "", "plate", Collections.EMPTY_LIST, false),
                    DocumentNoteField.createIntegerNoteField("Freezer", "", "freezer", Collections.EMPTY_LIST, false),
                    DocumentNoteField.createIntegerNoteField("Shelf", "", "shelf", Collections.EMPTY_LIST, false),
                    DocumentNoteField.createIntegerNoteField("Extra data", "", "extra_data", Collections.EMPTY_LIST, false)
            ), false);
            DocumentNoteUtilities.setNoteType(extraction);
        }
        DocumentNoteType pcr = DocumentNoteUtilities.getNoteType("moorea_pcr");
        if(pcr == null) {
            pcr = DocumentNoteUtilities.createNewNoteType("PCR Amplification", "moorea_pcr", "", Arrays.asList(DocumentNoteField.createEnumeratedNoteField(new String[] {"Passed", "Failed", "None"}, "Outcome", "", "outcome", false), DocumentNoteField.createIntegerNoteField("Freezer", "", "freezer", Collections.EMPTY_LIST, false), DocumentNoteField.createIntegerNoteField("Shelf", "", "shelf", Collections.EMPTY_LIST, false)), true);
            DocumentNoteUtilities.setNoteType(pcr);
        }

        List<DocumentNote> notes = new ArrayList<DocumentNote>();



        try {
            InputStream resource = getClass().getResourceAsStream("TextAbiDocuments.xml");
            List<NucleotideSequenceDocument> docs = new ArrayList<NucleotideSequenceDocument>();
            SAXBuilder builder = new SAXBuilder();
            Element root = builder.build(resource).detachRootElement();
            for(Element e : root.getChildren()) {
                docs.add(XMLSerializer.classFromXML(e, NucleotideSequenceDocument.class));
            }
            document.setNucleotideSequences(docs);
        } catch (JDOMException e) {
            throw new DatabaseServiceException("arse2", false);
        } catch (IOException e) {
            throw new DatabaseServiceException("arse3", false);
        } catch (XMLSerializationException e) {
            throw new DatabaseServiceException("arse4", false);
        }


        AnnotatedPluginDocument apd = DocumentUtilities.createAnnotatedPluginDocument(document);

        AnnotatedPluginDocument.DocumentNotes documentNotes = apd.getDocumentNotes(true);

        DocumentNote specimenNote = specimen.createDocumentNote();
        documentNotes.setNote(specimenNote);

        DocumentNote sampleNote = sample.createDocumentNote();
        documentNotes.setNote(sampleNote);

        DocumentNote extractionNote = extraction.createDocumentNote();
        documentNotes.setNote(extractionNote);

        DocumentNote pcrNote = pcr.createDocumentNote();
        documentNotes.setNote(pcrNote);

        documentNotes.saveNotes();

        callback.add(apd, Collections.EMPTY_MAP);
    }

    public String getUniqueID() {
        return "MooreaLabBenchService";
    }

    public String getName() {
        return "Moorea";
    }

    public String getDescription() {
        return "Search records form Moorea";
    }

    public String getHelp() {
        return null;
    }

    public Icons getIcons() {
        return IconUtilities.getIcons("databaseSearch16.png", "databaseSearch24.png");
    }

}
