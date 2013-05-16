package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.implementations.EValue;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Steve
 * Date: 5/04/13
 * Time: 10:43 PM
 * To change this template use File | Settings | File Templates.
 */
public class MetagenomicsDocument implements PluginDocument {

    static class OTU implements XMLSerializable {
        private String contigName;
        private URN urn;
        private EValue eValue;
        private String taxonomy;
        private String description;
        private int sequenceCount;

        public OTU(String contigName, URN urn, String taxonomy, String description, int sequenceCount, EValue eValue) {
            this.contigName = contigName;
            this.urn = urn;
            this.taxonomy = taxonomy;
            this.description = description;
            this.sequenceCount = sequenceCount;
            this.eValue = eValue;
        }

        public OTU(Element e) throws XMLSerializationException {
            this.fromXML(e);
        }

        public URN getUrn() {
            return urn;
        }

        public String getTaxonomy() {
            return taxonomy;
        }

        public String getDescription() {
            return description;
        }

        public int getSequenceCount() {
            return sequenceCount;
        }

        public String getContigName() {
            return contigName;
        }

        public EValue geteValue() {
            return eValue;
        }

        public Element toXML() {
            Element e = new Element("OTU");
            if(urn != null) {
                e.addContent(new Element("urn").setText(urn.toString()));
            }
            if(taxonomy != null) {
                e.addContent(new Element("taxonomy").setText(taxonomy));
            }
            if(description != null) {
                e.addContent(new Element("description").setText(description));
            }
            if(contigName != null) {
                e.addContent(new Element("contigName").setText(contigName));
            }
            if(eValue != null) {
                e.addContent(XMLSerializer.classToXML("eValue", eValue));
            }
            e.addContent(new Element("sequenceCount").setText(""+sequenceCount));
            return e;
        }

        public void fromXML(Element element) throws XMLSerializationException {
            String urnString = element.getChildText("urn");
            if(urnString != null) {
                try {
                    urn = new URN(urnString);
                } catch (MalformedURNException e) {
                    throw new XMLSerializationException(e.getMessage(), e);
                }
            }
            contigName = element.getChildText("contigName");
            taxonomy = element.getChildText("taxonomy");
            description = element.getChildText("description");
            String countString = element.getChildText("sequenceCount");
            if(countString != null) {
                sequenceCount = Integer.parseInt(countString);
            }
            Element eValueElement = element.getChild("eValue");
            if(eValueElement != null) {
                eValue = XMLSerializer.classFromXML(eValueElement, EValue.class);
            }
        }
    }

    private List<OTU> otus;

    public MetagenomicsDocument() {
        otus = new ArrayList<OTU>();
    }

    public int getOTUCount() {
        return otus.size();
    }

    public OTU getOTU(int index) {
        return otus.get(index);
    }

    public void addOTU(OTU otu) {
        otus.add(otu);
    }

    public List<DocumentField> getDisplayableFields() {
        return Arrays.asList(new DocumentField("Number of OTU's", "Number of OTU's", "otuCount", Integer.class, true, false));
    }

    public Object getFieldValue(String fieldCodeName) {
        return "outCount".equals(fieldCodeName) ? getOTUCount() : null;
    }

    public String getName() {
        return "Taxonomy information for "+ getOTUCount()+" contigs";
    }

    public URN getURN() {
        return null;
    }

    public Date getCreationDate() {
        return null;
    }

    public String getDescription() {
        return "A list of NCBI taxonomies derived from BLAST searches of the consensus sequences of those contigs.";
    }

    public String toHTML() {
        return null;
    }

    public Element toXML() {
        Element e = new Element("otuList");
        for(OTU otu : otus) {
            e.addContent(otu.toXML());
        }
        return e;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        otus = new ArrayList<OTU>();
        for(Element e : element.getChildren("OTU")) {
            otus.add(new OTU(e));
        }
    }
}
