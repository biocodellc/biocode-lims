package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.plugin.ExtendedPrintable;
import com.biomatters.geneious.publicapi.components.OptionsPanel;
import com.biomatters.plugins.moorea.reaction.Reaction;
import com.biomatters.plugins.moorea.reaction.ExtractionReaction;
import com.biomatters.plugins.moorea.reaction.PCRReaction;
import com.biomatters.plugins.moorea.reaction.ThermocycleEditor;

import java.util.*;
import java.util.List;
import java.awt.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.jdom.Element;

import javax.swing.*;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 18/06/2009 4:06:40 PM
 */
public class WorkflowDocument extends MuitiPartDocument {
    private Workflow workflow;
    private List<Reaction> reactions;
    private static final DateFormat dateFormat = SimpleDateFormat.getDateInstance(SimpleDateFormat.MEDIUM);

    public WorkflowDocument() {
        this.reactions = new ArrayList<Reaction>();    
    }

    public WorkflowDocument(Workflow workflow, List<Reaction> reactions) {
        this.workflow = workflow;
        this.reactions = new ArrayList<Reaction>(reactions);
        sortReactions(reactions);
    }

    private void sortReactions(List<Reaction> reactions) {
        Comparator comp = new Comparator<Reaction>(){
            public int compare(Reaction o1, Reaction o2) {
                return (int)(o2.getDate().getTime()-o1.getDate().getTime());
            }
        };
        Collections.sort(reactions, comp);
    }

    public WorkflowDocument(ResultSet resultSet) throws SQLException{
        int workflowId = resultSet.getInt("workflow.id");
        String workflowName = resultSet.getString("workflow.name");
        this.reactions = new ArrayList<Reaction>();
        workflow = new Workflow(workflowId, workflowName);
        addRow(resultSet);
    }

    public List<DocumentField> getDisplayableFields() {
        return Collections.EMPTY_LIST;
    }

    public Object getFieldValue(String fieldCodeName) {
        return null;
    }

    public String getName() {
        return workflow == null ? "Untitled" : workflow.getName();
    }

    public URN getURN() {
        return null;
    }

    public Date getCreationDate() {
        return null;
    }

    public String getDescription() {
        return null;
    }

    public String toHTML() {
        return null;
    }

    public Element toXML() {
        Element element = new Element("WorkflowDocument");
        if(workflow != null) {
            element.addContent(workflow.toXML());
        }
        if(reactions != null) {
            for(Reaction r : reactions) {
                element.addContent(XMLSerializer.classToXML("reaction", r));
            }
        }
        
        return element;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        workflow = new Workflow(element.getChild("workflow"));
        reactions = new ArrayList<Reaction>();
        for(Element e : element.getChildren("reaction")) {
            reactions.add(XMLSerializer.classFromXML(e, Reaction.class));
        }
    }

    public int getNumberOfParts() {
        return reactions.size();
    }

    public Part getPart(int index) {
        return new ReactionPart(reactions.get(index));
    }

    public void addRow(ResultSet resultSet) throws SQLException{
        //add extractions
        if(resultSet.getObject("extraction.id") != null) {
            int reactionId = resultSet.getInt("extraction.id");
            //check we don't already have it
            boolean alreadyThere = false;
            for(Reaction r : reactions) {
                if(r.getType() == Reaction.Type.Extraction && r.getId() == reactionId) {
                    alreadyThere = true;
                }
            }
            if(!alreadyThere) {
                Reaction r = new ExtractionReaction(resultSet, workflow);
                reactions.add(r);
            }
        }

        //add PCR's
        if(resultSet.getObject("pcr.id") != null) {
            int reactionId = resultSet.getInt("pcr.id");
            //check we don't already have it
            boolean alreadyThere = false;
            for(Reaction r : reactions) {
                if(r.getType() == Reaction.Type.PCR && r.getId() == reactionId) {
                    alreadyThere = true;
                }
            }
            if(!alreadyThere) {
                Reaction r = new PCRReaction(resultSet, workflow);
                reactions.add(r);
            }
        }
    }

    private class ReactionPart extends Part {
        private Reaction reaction;

        public ReactionPart(Reaction reaction) {
            super();
            this.reaction = reaction;
            init();
        }

        private void init() {
            OptionsPanel optionsPanel = new OptionsPanel(true, false);
            List<DocumentField> documentFields = reaction.getDisplayableFields();
            for(DocumentField field : documentFields) {
                optionsPanel.addComponentWithLabel("<html><b>"+field.getName()+": </b></html>", new JLabel(reaction.getFieldValue(field.getCode()).toString()), false);
            }
            setOpaque(false);
            setLayout(new BorderLayout());
            add(optionsPanel, BorderLayout.CENTER);
            if(reaction.getThermocycle() != null) {
                ThermocycleEditor.ThermocycleViewer viewer = new ThermocycleEditor.ThermocycleViewer(reaction.getThermocycle());
                add(viewer, BorderLayout.EAST);
            }
        }

        public String getName() {
            switch(reaction.getType()) {
                case Extraction:
                    return "Extraction: "+dateFormat.format(reaction.getCreated());
                case PCR:
                    return "PCR: "+dateFormat.format(reaction.getCreated());
                case CycleSequencing:
                    return "Cycle Sequencing: "+dateFormat.format(reaction.getCreated());
            }
            return "Unknown Reaction";
        }

        public ExtendedPrintable getExtendedPrintable() {
            return null;
        }

        public boolean hasChanges() {
            return false; //todo: implement
        }
    }
}
