package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.URN;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.ExtendedPrintable;
import com.biomatters.geneious.publicapi.components.OptionsPanel;
import com.biomatters.plugins.moorea.reaction.Reaction;

import java.util.*;
import java.util.List;
import java.awt.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

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

    public WorkflowDocument(Workflow workflow, List<Reaction> reactions) {
        this.workflow = workflow;
        this.reactions = new ArrayList<Reaction>(reactions);
        Comparator comp = new Comparator<Reaction>(){
            public int compare(Reaction o1, Reaction o2) {
                return (int)(o2.getDate().getTime()-o1.getDate().getTime());
            }
        };
        Collections.sort(reactions, comp);
    }

    public List<DocumentField> getDisplayableFields() {
        return Collections.EMPTY_LIST;
    }

    public Object getFieldValue(String fieldCodeName) {
        return null;
    }

    public String getName() {
        return "Workflow";
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
        return null;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        
    }

    public int getNumberOfParts() {
        return reactions.size();
    }

    public Part getPart(int index) {
        return new ReactionPart(reactions.get(index));
    }

    private class ReactionPart extends Part {
        private Reaction reaction;

        public ReactionPart(Reaction reaction) {
            super();
            this.reaction = reaction;
        }

        private void init() {
            OptionsPanel optionsPanel = new OptionsPanel(true, false);
            List<DocumentField> documentFields = reaction.getDisplayableFields();
            for(DocumentField field : documentFields) {
                optionsPanel.addComponentWithLabel(field.getName(), new JLabel(reaction.getFieldValue(field.getCode()).toString()), false);
            }
            setOpaque(false);
            setLayout(new BorderLayout());
            add(optionsPanel, BorderLayout.CENTER);
        }

        public String getName() {
            switch(reaction.getType()) {
                case Extraction:
                    return "Extraction";
                case PCR:
                    return "PCR";
                case CycleSequencing:
                    return "Cycle Sequencing";
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
