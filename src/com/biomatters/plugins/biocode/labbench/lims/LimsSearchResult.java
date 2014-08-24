package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.plugins.biocode.labbench.PlateDocument;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.server.XMLSerializableList;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 4/04/14 11:56 AM
 */
public class LimsSearchResult implements XMLSerializable {
    List<String> tissueIds = new ArrayList<String>();
    XMLSerializableList<WorkflowDocument> workflows = new XMLSerializableList<WorkflowDocument>(
            WorkflowDocument.class, new ArrayList<WorkflowDocument>());
    XMLSerializableList<PlateDocument> plates = new XMLSerializableList<PlateDocument>(
            PlateDocument.class, new ArrayList<PlateDocument>());
    List<Integer> sequenceIds = new ArrayList<Integer>();

    public List<String> getTissueIds() {
        return tissueIds;
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

    public void addTissueSample(String tissueId) {
        tissueIds.add(tissueId);
    }

    public void addAllTissueSamples(Collection<? extends String> tissueIds) {
        this.tissueIds.addAll(tissueIds);
    }

    public void addWorkflow(WorkflowDocument workflow) {
        workflows.add(workflow);
    }

    public void addAllWorkflows(Collection<? extends WorkflowDocument> workflows) {
        this.workflows.addAll(workflows);
    }

    public void addPlate(PlateDocument plate) {
        plates.add(plate);
    }

    public void addAllPlates(Collection<? extends PlateDocument> plates) {
        this.plates.addAll(plates);
    }

    public void addSequenceID(Integer sequenceId) {
        sequenceIds.add(sequenceId);
    }

    public void addAllSequenceIDs(Collection<? extends Integer> sequenceIds) {
        this.sequenceIds.addAll(sequenceIds);
    }

    private static final String TISSUES = "tissues";
    private static final String WORKFLOWS = "workflows";
    private static final String PLATES = "plates";
    private static final String SEQUENCES = "sequenceIds";

    @Override
    public Element toXML() {
        Element root = new Element(XMLSerializable.ROOT_ELEMENT_NAME);
        addIdListAsElement(root, TISSUES, tissueIds);
        root.addContent(XMLSerializer.classToXML(WORKFLOWS, workflows));
        root.addContent(XMLSerializer.classToXML(PLATES, plates));
        addIdListAsElement(root, SEQUENCES, sequenceIds);
        return root;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void fromXML(Element element) throws XMLSerializationException {
        tissueIds = getIdListFromElement(element, TISSUES, TEXT_PARSER);
        workflows = getListFromElement(element, WORKFLOWS);
        plates = getListFromElement(element, PLATES);
        sequenceIds = getIdListFromElement(element, SEQUENCES, INTEGER_PARSER);
    }

    private static void addIdListAsElement(Element root, String elementName, List<?> ids) {
        Element seqElement = new Element(elementName);
        for (Object sequenceId : ids) {
            seqElement.addContent(new Element("id").setText(String.valueOf(sequenceId)));
        }
        root.addContent(seqElement);
    }

    private static <T> List<T> getIdListFromElement(Element element, String elementName, Parser<T> parser) throws XMLSerializationException {
        Element sequencesElement = element.getChild(elementName);
        List<T> tempList = new ArrayList<T>();
        if(sequencesElement != null) {
            for (Element child : sequencesElement.getChildren()) {
                String childText = child.getText();
                tempList.add(parser.parseText(childText));
            }
        }
        return tempList;
    }

    private static abstract class Parser<T> {
        abstract T parseText(String text) throws XMLSerializationException;
    }

    private static final Parser TEXT_PARSER = new Parser<String>() {
        @Override
        String parseText(String text) throws XMLSerializationException {
            return text;
        }
    };

    private static final Parser INTEGER_PARSER = new Parser<Integer>() {
        @Override
        Integer parseText(String text) throws XMLSerializationException {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                throw new XMLSerializationException("Bad sequence ID: " + text, e);
            }
        }
    };

    public static XMLSerializableList getListFromElement(Element element, String key) throws XMLSerializationException {
        Element workflowsElement = element.getChild(key);
        if(workflowsElement != null) {
            return XMLSerializer.classFromXML(workflowsElement, XMLSerializableList.class);
        } else {
            throw new XMLSerializationException("Missing " + key + " element");
        }
    }
}