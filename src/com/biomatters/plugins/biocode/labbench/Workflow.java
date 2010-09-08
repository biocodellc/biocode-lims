package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import org.jdom.Element;

import java.util.Date;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 16/06/2009 3:12:41 PM
 */
public class Workflow implements XMLSerializable {
    private int id;
    private String name;
    private String extraction;
    private String locus;
    private Date lastModified;
    private FimsSample fimsSample;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Workflow)) return false;

        Workflow workflow = (Workflow) o;

        if (id != workflow.id) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return id;
    }

    public Workflow(Element e) throws XMLSerializationException{
        fromXML(e);
    }

    public Workflow(int id, String name, String extractionId, String locus, Date lastModified) {
        this.id = id;
        this.name = name;
        this.extraction = extractionId;
        this.locus = locus;
        this.lastModified = lastModified;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExtractionId() {
        return extraction;
    }

    public void setExtractionId(String extraction) {
        this.extraction = extraction;
    }

    public String getLocus() {
        return locus;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public Element toXML() {
        return new Element("workflow").addContent(new Element("name").setText(getName())).addContent(new Element("id").setText(""+getId())).addContent(new Element("extraction").setText(extraction)).addContent(new Element("date").setText(""+lastModified.getTime())).addContent(new Element("locus").setText(locus));
    }

    public FimsSample getFimsSample() {
        return fimsSample;
    }

    public void setFimsSample(FimsSample fimsSample) {
        this.fimsSample = fimsSample;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        name = element.getChildText("name");
        extraction = element.getChildText("extraction");
        lastModified = new Date(Long.parseLong(element.getChildText("date")));
        locus = element.getChildText("locus");
        if(element.getChildText("id") != null) {
            try {
                id = Integer.parseInt(element.getChildText("id"));
            }
            catch(NumberFormatException ex){
                throw new XMLSerializationException("The id field was invalid");
            }
        }
    }
}
