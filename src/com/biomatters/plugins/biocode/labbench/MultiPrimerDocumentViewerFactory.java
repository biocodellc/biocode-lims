package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.reaction.Cocktail;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingOptions;

import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 7/07/2009 7:36:04 PM
 */
public class MultiPrimerDocumentViewerFactory extends TableDocumentViewerFactory{
    private Reaction.Type type;

    public MultiPrimerDocumentViewerFactory(Reaction.Type type) {
        this.type = type;
    }

    public String getName() {
        return type.toString();
    }

    public String getDescription() {
        return "An overview of all "+type.toString()+" reactions performed on the selected workflows";
    }

    public String getHelp() {
        return "An overview of all "+type.toString()+" reactions performed on the selected workflows.  The table shows all primers run in the reactions.";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(PluginDocument.class, 1, Integer.MAX_VALUE)
        };
    }

    private static class PrimerIdentifier{
        public enum Type {
            forward,
            reverse
        }

        private Type type;
        private String name;

        private PrimerIdentifier(Type type, String name) {
            this.type = type;
            this.name = name;
        }

        public Type getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PrimerIdentifier)) return false;

            PrimerIdentifier that = (PrimerIdentifier) o;

            if (!name.equals(that.name)) return false;
            if (type != that.type) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + name.hashCode();
            return result;
        }
    }

    private List<WorkflowDocument> getWorkflows(AnnotatedPluginDocument[] docs, Reaction.Type type) {
        List<WorkflowDocument> reactionList = new ArrayList<WorkflowDocument>();
        for(AnnotatedPluginDocument doc : docs) {
            if(PlateDocument.class.isAssignableFrom(doc.getDocumentClass())) {
                PlateDocument plateDoc = (PlateDocument)doc.getDocumentOrCrash();
                if(plateDoc.getPlate().getReactionType() == type) {
                    for(Reaction r : plateDoc.getPlate().getReactions()) {
                        Workflow reactionWorkflow = r.getWorkflow();
                          //workflows can be null if reactions don't have extraction id's
//                        if(reactionWorkflow == null) {
//                            assert false;
//                        }
                        reactionList.add(new WorkflowDocument(reactionWorkflow, Arrays.asList(r)));
                    }
                }
            }
            if(WorkflowDocument.class.isAssignableFrom(doc.getDocumentClass())) {
                WorkflowDocument workflowDoc = (WorkflowDocument)doc.getDocumentOrCrash();
                reactionList.add(workflowDoc);
            }
        }
        return reactionList;
    }

    /**
     * combines a list of partial workflows (generated by the {@link #getWorkflows(com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument[], com.biomatters.plugins.biocode.labbench.reaction.Reaction.Type)} method for plates, and combines the reactions into their proper workflows.
     * @param workflows
     * @return
     */
    public List<WorkflowDocument> combineWorkflows(List<WorkflowDocument> workflows) {
        Map<String, WorkflowDocument> workflowMap = new HashMap<String, WorkflowDocument> ();
        for(WorkflowDocument workflow : workflows) {
            if(workflowMap.get(workflow.getName()) != null) {
                WorkflowDocument existingWorkflow = workflowMap.get(workflow.getName());
                for(Reaction r : workflow.getReactions()) {
                    if(!existingWorkflow.getReactions().contains(r)) {
                        existingWorkflow.addReaction(r);
                    }
                }
            }
            else {
                workflowMap.put(workflow.getName(), workflow);
            }
        }
        return new ArrayList<WorkflowDocument>(workflowMap.values());
    }

    private int getReactionCount(List<WorkflowDocument> workflows, Reaction.Type type) {
        int count = 0;
        for(WorkflowDocument w : workflows) {
            count += w.getReactions(type).size();
        }
        return count;
    }

    public TableModel getTableModel(final AnnotatedPluginDocument[] docs) {
        Set<PrimerIdentifier> primerNamesSet = new HashSet<PrimerIdentifier>();
        final List<WorkflowDocument> workflows = combineWorkflows(getWorkflows(docs, type));
        if(workflows.size() < 2) {
            return null;
        }
        for(WorkflowDocument workflow : workflows) {
            List<Reaction> reactions = workflow.getReactions(type);
            if(type == Reaction.Type.PCR) {
                for(Reaction r : reactions) {
                    Options.OptionValue primerValue = (Options.OptionValue) r.getOptions().getValue("primer");
                    primerNamesSet.add(new PrimerIdentifier(PrimerIdentifier.Type.forward, primerValue.getName()));
                }
                for(Reaction r : reactions) {
                    Options.OptionValue primerValue = (Options.OptionValue) r.getOptions().getValue("revPrimer");
                    primerNamesSet.add(new PrimerIdentifier(PrimerIdentifier.Type.reverse, primerValue.getName()));
                }
            }
            else {
                for(Reaction r : reactions) {
                    boolean isForward = CycleSequencingOptions.FORWARD_VALUE.equals(r.getOptions().getValueAsString(CycleSequencingOptions.DIRECTION));
                    Options.OptionValue primerValue = (Options.OptionValue) r.getOptions().getValue("primer");
                    primerNamesSet.add(new PrimerIdentifier(isForward ? PrimerIdentifier.Type.forward : PrimerIdentifier.Type.reverse, primerValue.getName()));
                }
            }

        }
        final List<PrimerIdentifier> primerList = new ArrayList<PrimerIdentifier>(primerNamesSet);

        final ObjectAndColor[][] tableValues = new ObjectAndColor[workflows.size()][primerNamesSet.size()];
        ObjectAndColor notTriedValue = new ObjectAndColor("Not tried", Color.black);
        for(int i=0; i < tableValues.length; i++) {
            Arrays.fill(tableValues[i], notTriedValue);
        }

        for (int i = 0; i < workflows.size(); i++) {
            WorkflowDocument workflow = workflows.get(i);
            List<Reaction> reactions = workflow.getReactions(type);
            for (int j = 0; j < primerList.size(); j++) {
                PrimerIdentifier primer = primerList.get(j);
                String s = primer.getName();
                for (Reaction r : reactions) {
                    String primerName;
                    if(r.getType() == Reaction.Type.PCR) {
                        primerName = ((Options.OptionValue) r.getOptions().getValue(primer.getType() == PrimerIdentifier.Type.forward ? "primer" : "revPrimer")).getName();
                    }
                    else {
                        primerName = ((Options.OptionValue) r.getOptions().getValue("primer")).getName();    
                    }
                    Cocktail cocktail = r.getOptions().getCocktail();
                    if (primerName.equals(s)) {
                        tableValues[i][j] = new ObjectAndColor(r.getPlateName()+" "+r.getLocationString()+(cocktail != null ? ", "+cocktail.getName() : ""), r.getBackgroundColor());
                    }
                }
            }
        }

        return new TableModel(){
            public int getRowCount() {
                return tableValues.length;
            }

            public int getColumnCount() {
                return 2+primerList.size();
            }

            public String getColumnName(int columnIndex) {
                if(columnIndex == 0) {
                    return "Workflow";
                }
                if(columnIndex == 1) {
                    return "Extraction";
                }
                PrimerIdentifier primerIdentifier = primerList.get(columnIndex - 2);
                return primerIdentifier.getName() + " ("+primerIdentifier.getType()+")";
            }

            public Class<?> getColumnClass(int columnIndex) {
                if(columnIndex < 2) {
                    return String.class;
                }
                return ObjectAndColor.class;
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                if(columnIndex == 0) {
                    return workflows.get(rowIndex).getName();
                }
                if(columnIndex == 1) {
                    WorkflowDocument workflowDocument = workflows.get(rowIndex);
                    Reaction extraction = workflowDocument.getMostRecentReaction(Reaction.Type.Extraction);
                    if(extraction != null) {
                        return extraction.getExtractionId();
                    }
                    Reaction pcr = workflowDocument.getMostRecentReaction(Reaction.Type.PCR);
                    if(pcr != null) {
                        return pcr.getExtractionId();
                    }
                    Reaction sequencing = workflowDocument.getMostRecentReaction(Reaction.Type.CycleSequencing);
                    if(sequencing != null) {
                        return sequencing.getExtractionId();
                    }
                    return "";
                }
                return tableValues[rowIndex][columnIndex-2];
            }

            public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            public void addTableModelListener(TableModelListener l) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            public void removeTableModelListener(TableModelListener l) {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        };
    }
}
