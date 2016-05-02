package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import org.jdom.Element;

import java.util.*;

/**
 * @author Matthew Cheung
 *          <p/>
 *          Created on 4/04/14 11:56 AM
 */
public class LimsSearchResult implements XMLSerializable {
    Set<String> tissueIds = new HashSet<String>();
    Set<Integer> workflows = new HashSet<Integer>();
    Set<Integer> plates = new HashSet<Integer>();
    Set<Integer> sequenceIds = new HashSet<Integer>();

    public List<String> getTissueIds() {
        return new ArrayList<String>(tissueIds);
    }

    public List<Integer> getWorkflowIds() {
        return new ArrayList<Integer>(workflows);
    }

    public List<Integer> getPlateIds() {
        return new ArrayList<Integer>(plates);
    }

    public List<Integer> getSequenceIds() {
        return new ArrayList<Integer>(sequenceIds);
    }

    public void addTissueSample(String tissueId) {
        tissueIds.add(tissueId);
    }

    public void addAllTissueSamples(Collection<? extends String> tissueIds) {
        this.tissueIds.addAll(tissueIds);
    }

    public void addWorkflow(Integer workflow) {
        workflows.add(workflow);
    }

    public void addAllWorkflows(Collection<? extends Integer> workflows) {
        this.workflows.addAll(workflows);
    }

    public void addPlate(Integer plate) {
        plates.add(plate);
    }

    public void addAllPlates(Collection<? extends Integer> plates) {
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
        addIdListAsElement(root, WORKFLOWS, workflows);
        addIdListAsElement(root, PLATES, plates);
        addIdListAsElement(root, SEQUENCES, sequenceIds);
        return root;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void fromXML(Element element) throws XMLSerializationException {
        tissueIds = getIdListFromElement(element, TISSUES, TEXT_PARSER);
        workflows = getIdListFromElement(element, WORKFLOWS, INTEGER_PARSER);
        plates = getIdListFromElement(element, PLATES, INTEGER_PARSER);
        sequenceIds = getIdListFromElement(element, SEQUENCES, INTEGER_PARSER);
    }

    private static void addIdListAsElement(Element root, String elementName, Collection<?> ids) {
        Element seqElement = new Element(elementName);
        for (Object sequenceId : ids) {
            seqElement.addContent(new Element("id").setText(String.valueOf(sequenceId)));
        }
        root.addContent(seqElement);
    }

    private static <T> Set<T> getIdListFromElement(Element element, String elementName, Parser<T> parser) throws XMLSerializationException {
        Element sequencesElement = element.getChild(elementName);
        Set<T> tempSet = new HashSet<T>();
        if(sequencesElement != null) {
            for (Element child : sequencesElement.getChildren()) {
                String childText = child.getText();
                tempSet.add(parser.parseText(childText));
            }
        }
        return tempSet;
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
}