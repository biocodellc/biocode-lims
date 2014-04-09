package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PlateDocument;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.server.XMLSerializableList;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 4/04/14 11:56 AM
 */
public class LimsSearchResult implements XMLSerializable {
    XMLSerializableList<FimsSample> tissueSamples = new XMLSerializableList<FimsSample>(
            FimsSample.class, new ArrayList<FimsSample>());
    XMLSerializableList<WorkflowDocument> workflows = new XMLSerializableList<WorkflowDocument>(
            WorkflowDocument.class, new ArrayList<WorkflowDocument>());
    XMLSerializableList<PlateDocument> plates = new XMLSerializableList<PlateDocument>(
            PlateDocument.class, new ArrayList<PlateDocument>());
    List<Integer> sequenceIds = new ArrayList<Integer>();

    public List<FimsSample> getTissueSamples() {
        return tissueSamples.getList();
    }

    public List<WorkflowDocument> getWorkflows() {
        return workflows.getList();
    }

    public List<PlateDocument> getPlates() {
        return plates.getList();
    }

    public List<Integer> getSequenceIds() {
        return Collections.unmodifiableList(sequenceIds);
    }

    private static final String TISSUES = "tissues";
    private static final String WORKFLOWS = "workflows";
    private static final String PLATES = "plates";
    private static final String SEQUENCES = "sequenceIds";

    @Override
    public Element toXML() {
        Element root = new Element(XMLSerializable.ROOT_ELEMENT_NAME);
        root.addContent(XMLSerializer.classToXML(TISSUES, tissueSamples));
        root.addContent(XMLSerializer.classToXML(WORKFLOWS, workflows));
        root.addContent(XMLSerializer.classToXML(PLATES, plates));
        Element seqElement = new Element(SEQUENCES);
        for (Integer sequenceId : sequenceIds) {
            seqElement.addContent(new Element("id").setText(""+sequenceId));
        }
        root.addContent(seqElement);
        return root;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void fromXML(Element element) throws XMLSerializationException {
        tissueSamples = getListFromElement(element, TISSUES);
        workflows = getListFromElement(element, WORKFLOWS);
        plates = getListFromElement(element, PLATES);

        Element sequencesElement = element.getChild(SEQUENCES);
        if(sequencesElement != null) {
            sequenceIds = new ArrayList<Integer>();
            for (Element child : sequencesElement.getChildren()) {
                String childText = child.getText();
                try {
                    int value = Integer.parseInt(childText);
                    sequenceIds.add(value);
                } catch (NumberFormatException e) {
                    throw new XMLSerializationException("Bad sequence ID: " + childText, e);
                }
            }
        }
    }

    public static XMLSerializableList getListFromElement(Element element, String key) throws XMLSerializationException {
        Element workflowsElement = element.getChild(key);
        if(workflowsElement != null) {
            return XMLSerializer.classFromXML(workflowsElement, XMLSerializableList.class);
        } else {
            throw new XMLSerializationException("Missing " + key + " element");
        }
    }
}
