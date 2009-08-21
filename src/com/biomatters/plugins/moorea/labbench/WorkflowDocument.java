package com.biomatters.plugins.moorea.labbench;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.OptionsPanel;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.DefaultSequenceListDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentViewer;
import com.biomatters.geneious.publicapi.plugin.DocumentViewerFactory;
import com.biomatters.geneious.publicapi.plugin.ExtendedPrintable;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.labbench.reaction.*;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 18/06/2009 4:06:40 PM
 */
public class WorkflowDocument extends MuitiPartDocument {
    private Workflow workflow;
    private List<Reaction> reactions;
    private List<ReactionPart> parts;


    public WorkflowDocument() {
        this(null, Collections.<Reaction>emptyList());
    }

    public WorkflowDocument(Workflow workflow, List<Reaction> reactions) {
        this.workflow = workflow;
        this.reactions = new ArrayList<Reaction>(reactions);
        sortReactions(reactions);
        parts = new ArrayList<ReactionPart>();
        for(Reaction r : reactions) {
            parts.add(new ReactionPart(r));
        }
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
        this.parts = new ArrayList<ReactionPart>();
        addRow(resultSet);
    }

    public List<DocumentField> getDisplayableFields() {
        return Collections.emptyList();
    }

    public Object getFieldValue(String fieldCodeName) {
        return null;
    }

    public String getName() {
        return workflow == null ? "Untitled" : workflow.getName();
    }

    /**
     * @return workflow id or -1 if no workflow associated with this document
     */
    public int getId() {
        return workflow == null ? -1 : workflow.getId();
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
        for(Reaction r : reactions) {
            parts.add(new ReactionPart(r));
        }
    }

    public int getNumberOfParts() {
        return parts.size();
    }

    public Part getPart(int index) {
        return parts.get(index);
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
                    Reaction r = new ExtractionReaction(resultSet);
                    reactions.add(r);
                    parts.add(new ReactionPart(r));
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
                Reaction r = new PCRReaction(resultSet);
                reactions.add(r);
                parts.add(new ReactionPart(r));
            }
            break;
        case CycleSequencing :
            reactionId = resultSet.getInt("cyclesequencing.id");
            //check we don't already have it
            alreadyThere = false;
            for(Reaction r : reactions) {
                if(r.getType() == Reaction.Type.CycleSequencing && r.getId() == reactionId) {
                    alreadyThere = true;
                }
            }
            if(!alreadyThere) {
                Reaction r = new CycleSequencingReaction(resultSet);
                reactions.add(r);
                parts.add(new ReactionPart(r));
            }
            break;
        }
        Collections.sort(parts, new Comparator<ReactionPart>(){
            public int compare(ReactionPart o1, ReactionPart o2) {
                return (int)(o1.getReaction().getCreated().getTime() - o2.getReaction().getCreated().getTime());
            }
        });
    }

    public static class ReactionPart extends Part {
        private Reaction reaction;
        private List<SimpleListener> changesListeners;
        private boolean changes = false;

        public ReactionPart(Reaction reaction) {
            super();
            this.reaction = reaction;
            changesListeners = new ArrayList<SimpleListener>();
        }

        @Override
        public void addModifiedStateChangedListener(SimpleListener sl) {
            changesListeners.add(sl);
        }

        @Override
        public void removeModifiedStateChangedListener(SimpleListener sl) {
            changesListeners.remove(sl);
        }

        private void fireChangeListeners() {
            for(SimpleListener listener : changesListeners) {
                listener.objectChanged();
            }
        }

        public JPanel getPanel() {
            final JPanel panel = new JPanel();
            final AtomicReference<OptionsPanel> optionsPanel = new AtomicReference<OptionsPanel>(getReactionPanel(reaction));
            final JPanel holder = new JPanel(new BorderLayout());
            holder.setOpaque(false);
            holder.add(optionsPanel.get(), BorderLayout.CENTER);
            JButton editButton = new JButton("Edit");
            editButton.setOpaque(false);
            editButton.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e) {
                    Element oldOptions = XMLSerializer.classToXML("options", reaction.getOptions());
                    ReactionUtilities.editReactions(Arrays.asList(reaction), false, panel, true, false);
                    if(reaction.hasError()) {
                        try {
                            reaction.setOptions(XMLSerializer.classFromXML(oldOptions, ReactionOptions.class));
                        } catch (XMLSerializationException e1) {
                            Dialogs.showMessageDialog("Error resetting options: could not deserailse your option's values.  It is recommended that you discard changes to this document if possible.");
                        }
                    }
                    else {
                        holder.remove(optionsPanel.get());
                        optionsPanel.set(getReactionPanel(reaction));
                        holder.add(optionsPanel.get(), BorderLayout.CENTER);
                        holder.validate();
                        changes = true;
                        fireChangeListeners();
                    }
                }
            });
            JPanel editPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            editPanel.setOpaque(false);
            editPanel.add(editButton);
            holder.add(editPanel, BorderLayout.SOUTH);
            panel.setOpaque(false);
            panel.setLayout(new BorderLayout());
            panel.add(holder, BorderLayout.CENTER);
            ThermocycleEditor.ThermocycleViewer viewer = getThermocyclePanel(reaction);
            if(viewer != null) {
                panel.add(viewer, BorderLayout.EAST);
            }
            return panel;
        }

        private static OptionsPanel getReactionPanel(Reaction reaction) {
            OptionsPanel optionsPanel = new OptionsPanel(false, false);
            List<DocumentField> documentFields = reaction.getDisplayableFields();
            for(DocumentField field : documentFields) {
                if(field.getName().length() > 0) {
                    optionsPanel.addComponentWithLabel("<html><b>"+field.getName()+": </b></html>", new JLabel(reaction.getFieldValue(field.getCode()).toString()), false);
                }
                else {
                    optionsPanel.addSpanningComponent(new JLabel(reaction.getFieldValue(field.getCode()).toString()));
                }
            }
            if(reaction.getPlateId() >= 0) {
                optionsPanel.addComponentWithLabel("<html><b>Plate Name: </b></html>", new JLabel(reaction.getPlateName()), false);
                optionsPanel.addComponentWithLabel("<html><b>Well: </b></html>", new JLabel(reaction.getLocationString()), false);
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
                    if(pageIndex == 1) {
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
                    }
                    else {
                        if(reaction instanceof CycleSequencingReaction) {
                        List<NucleotideSequenceDocument> sequences = ((CycleSequencingOptions) reaction.getOptions()).getSequences();
                        if(sequences != null && sequences.size() > 0) {
                                DefaultSequenceListDocument sequenceList = DefaultSequenceListDocument.forNucleotideSequences(sequences);
                                DocumentViewerFactory factory = TracesEditor.getViewerFactory(sequenceList);
                                DocumentViewer viewer = factory.createViewer(new AnnotatedPluginDocument[]{DocumentUtilities.createAnnotatedPluginDocument(sequenceList)});
                                ExtendedPrintable printable = viewer.getExtendedPrintable();
                                Options op = printable.getOptions(false);
                                return printable.print(graphics, dimensions, pageIndex-2, op);
                            }
                        }
                    }
                    return Printable.PAGE_EXISTS;
                }

                public int getPagesRequired(Dimension dimensions, Options options) {
                    int pagesRequired = 1;
                    if(reaction instanceof CycleSequencingReaction) {
                        List<NucleotideSequenceDocument> sequences = ((CycleSequencingOptions) reaction.getOptions()).getSequences();
                        if(sequences != null && sequences.size() > 0) {
                            DefaultSequenceListDocument sequenceList = DefaultSequenceListDocument.forNucleotideSequences(sequences);
                            DocumentViewerFactory factory = TracesEditor.getViewerFactory(sequenceList);
                            DocumentViewer viewer = factory.createViewer(new AnnotatedPluginDocument[]{DocumentUtilities.createAnnotatedPluginDocument(sequenceList)});
                            ExtendedPrintable printable = viewer.getExtendedPrintable();
                            Options op = printable.getOptions(false);
                            pagesRequired += printable.getPagesRequired(dimensions, op);
                        }
                    }
                    return pagesRequired;
                }
            };
        }

        public boolean hasChanges() {
            return changes;
        }

        public void saveChangesToDatabase(MooreaLabBenchService.BlockingDialog progress, Connection connection) throws SQLException{
            reaction.areReactionsValid(Arrays.asList(reaction));
            Reaction.saveReactions(new Reaction[] {reaction}, reaction.getType(), connection, progress);
            changes = false;
        }
    }
}
