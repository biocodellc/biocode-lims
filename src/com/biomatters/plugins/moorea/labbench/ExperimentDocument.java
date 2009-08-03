package com.biomatters.plugins.moorea.labbench;

import com.biomatters.geneious.publicapi.documents.sequence.SequenceListDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.AminoAcidSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.documents.*;

import java.util.*;

import org.jdom.Element;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 23/02/2009 5:00:52 PM
 */
public class ExperimentDocument implements SequenceListDocument, PluginDocument{
    private String name;
    private List<NucleotideSequenceDocument> traces;

    public ExperimentDocument(){
        traces = new ArrayList<NucleotideSequenceDocument>();
    }

    public ExperimentDocument(String name){
        this();
        this.name = name;
    }

    public ExperimentDocument(Element e) throws XMLSerializationException {
        this();
        fromXML(e);
    }

    public List<DocumentField> getDisplayableFields() {
        return Collections.EMPTY_LIST;
    }

    public Object getFieldValue(String fieldCodeName) {
        return null;
    }

    public String getName() {
        return name;
    }

    public URN getURN() {
        return null;
    }

    public Date getCreationDate() {
        return new Date();
    }

    public String getDescription() {
        return "Experimental results of "+getName();
    }

    public String toHTML() {
        return null;
    }

    public Element toXML() {
        Element e = new Element("Experiment");
        e.addContent(new Element("Name").setText(name));
        for(SequenceDocument document : traces) {
            e.addContent(XMLSerializer.classToXML("trace", document));    
        }
        return e;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        name = element.getChildText("Name");
        for(Element e : element.getChildren("trace")) {
            traces.add(XMLSerializer.classFromXML(e, NucleotideSequenceDocument.class));
        }
    }

    public void setNucleotideSequences(List<NucleotideSequenceDocument> sequences) {
        traces.clear();
        traces.addAll(sequences);
    }

    public List<NucleotideSequenceDocument> getNucleotideSequences() {
        return traces;
    }

    public List<AminoAcidSequenceDocument> getAminoAcidSequences() {
        return Collections.EMPTY_LIST;
    }


}
