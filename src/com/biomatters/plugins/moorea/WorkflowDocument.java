package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.plugin.ExtendedPrintable;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.components.OptionsPanel;
import com.biomatters.plugins.moorea.reaction.*;

import java.util.*;
import java.util.List;
import java.awt.*;
import java.awt.print.PrinterException;
import java.awt.print.Printable;
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
        String extraction = resultSet.getString("extraction.extractionId");
        this.reactions = new ArrayList<Reaction>();
        workflow = new Workflow(workflowId, workflowName, extraction);
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

    public Reaction getMostRecentReaction(Reaction.Type type) {
        Reaction r = null;
        for(int i=0; i < reactions.size(); i++) {
            if(reactions.get(i).getType() == type) {
                r = reactions.get(i);
            }
        }
        return r;
    }

    public List<Reaction> getReactions(Reaction.Type type) {
        List<Reaction> reactionsList = new ArrayList<Reaction>();
        for(int i=0; i < reactions.size(); i++) {
            if(reactions.get(i).getType() == type) {
                reactionsList.add(reactions.get(i));
            }
        }
        return reactionsList;
    }

    public void addRow(ResultSet resultSet) throws SQLException{
        //add extractions
        Reaction.Type rowType = Reaction.Type.valueOf(resultSet.getString("plate.type"));
        switch(rowType) {
            case Extraction :
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
            break;
        case PCR :
            reactionId = resultSet.getInt("pcr.id");
            //check we don't already have it
            alreadyThere = false;
            for(Reaction r : reactions) {
                if(r.getType() == Reaction.Type.PCR && r.getId() == reactionId) {
                    alreadyThere = true;
                }
            }
            if(!alreadyThere) {
                Reaction r = new PCRReaction(resultSet, workflow);
                reactions.add(r);
            }
            break;
        case CycleSequencing :
            reactionId = resultSet.getInt("cycleSequencing.id");
            //check we don't already have it
            alreadyThere = false;
            for(Reaction r : reactions) {
                if(r.getType() == Reaction.Type.PCR && r.getId() == reactionId) {
                    alreadyThere = true;
                }
            }
            if(!alreadyThere) {
                Reaction r = new CycleSequencingReaction(resultSet, workflow);
                reactions.add(r);
            }
            break;
        }
    }

    public static class ReactionPart extends Part {
        private Reaction reaction;

        public ReactionPart(Reaction reaction) {
            super();
            this.reaction = reaction;
        }

        public JPanel getPanel() {
            JPanel panel = new JPanel();
            OptionsPanel optionsPanel = getReactionPanel(reaction);
            panel.setOpaque(false);
            panel.setLayout(new BorderLayout());
            panel.add(optionsPanel, BorderLayout.CENTER);
            ThermocycleEditor.ThermocycleViewer viewer = getThermocyclePanel(reaction);
            if(viewer != null) {
                panel.add(viewer, BorderLayout.EAST);
            }
            return panel;
        }

        private static OptionsPanel getReactionPanel(Reaction reaction) {
            OptionsPanel optionsPanel = new OptionsPanel(true, false);
            List<DocumentField> documentFields = reaction.getDisplayableFields();
            for(DocumentField field : documentFields) {
                if(field.getName().length() > 0) {
                    optionsPanel.addComponentWithLabel("<html><b>"+field.getName()+": </b></html>", new JLabel(reaction.getFieldValue(field.getCode()).toString()), false);
                }
                else {
                    optionsPanel.addSpanningComponent(new JLabel(reaction.getFieldValue(field.getCode()).toString()));
                }
            }
            return optionsPanel;
        }

        public static ThermocycleEditor.ThermocycleViewer getThermocyclePanel(Reaction reaction) {
            if(reaction.getThermocycle() != null) {
                return new ThermocycleEditor.ThermocycleViewer(reaction.getThermocycle());
            }
            return null;
        }

        public String getName() {
            switch(reaction.getType()) {
                case Extraction:
                    return "Extraction Reaction: "+MooreaLabBenchService.dateFormat.format(reaction.getCreated());
                case PCR:
                    return "PCR Reaction: "+MooreaLabBenchService.dateFormat.format(reaction.getCreated());
                case CycleSequencing:
                    return "Cycle Sequencing Reaction: "+MooreaLabBenchService.dateFormat.format(reaction.getCreated());
            }
            return "Unknown Reaction";
        }

        public Reaction getReaction() {
            return reaction;
        }

        public ExtendedPrintable getExtendedPrintable() {
            return new ExtendedPrintable(){
                public int print(Graphics2D graphics, Dimension dimensions, int pageIndex, Options options) throws PrinterException {
                    if(pageIndex > 1) {
                        return Printable.NO_SUCH_PAGE;
                    }
                    JPanel reactionPanel = getReactionPanel(reaction);
                    JPanel thermocyclePanel = getThermocyclePanel(reaction);
                    JLabel headerLabel = new JLabel(getName());
                    headerLabel.setFont(new Font("arial", Font.BOLD, 16));
                    JPanel holderPanel = new JPanel(new BorderLayout());
                    holderPanel.setOpaque(false);
                    holderPanel.add(headerLabel, BorderLayout.NORTH);
                    holderPanel.add(reactionPanel, BorderLayout.CENTER);
                    if(thermocyclePanel != null) {
                        holderPanel.add(thermocyclePanel, BorderLayout.SOUTH);
                    }
                    holderPanel.setBounds(0,0,dimensions.width, dimensions.height);
                    MultiPartDocumentViewerFactory.recursiveDoLayout(holderPanel);
                    holderPanel.validate();
                    holderPanel.invalidate();
                    holderPanel.print(graphics);
                    return Printable.PAGE_EXISTS;
                }

                public int getPagesRequired(Dimension dimensions, Options options) {
                    return 1;
                }
            };
        }

        public boolean hasChanges() {
            return false; //todo: implement
        }
    }
}
