package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionOption;
import com.biomatters.geneious.publicapi.plugin.DocumentViewerFactory;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.reaction.Cocktail;
import com.biomatters.plugins.biocode.labbench.reaction.PCROptions;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingOptions;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.utilities.ObjectAndColor;

import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Steven Stones-Havas
 *          <p/>
 *          Created on 7/07/2009 7:36:04 PM
 */
public class MultiPrimerDocumentViewerFactory extends TableDocumentViewerFactory{
    private Reaction.Type type;

    public MultiPrimerDocumentViewerFactory(Reaction.Type type) {
        this.type = type;
    }

    @Override
    public String getUniqueId() {
        return super.getUniqueId()+type;
    }

    public String getName() {
        switch(type) {
            case PCR: return "PCR Primers";
            case CycleSequencing: return "Sequencing Primers";
        }
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

    @Override
           public DocumentViewerFactory.ViewPrecedence getPrecedence() {
               return  ViewPrecedence.LOW;
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

            return name.equals(that.name) && type == that.type;
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
                        if(reactionWorkflow != null) {
                            WorkflowDocument workflowDocument = new WorkflowDocument(reactionWorkflow, Arrays.asList(r));
                            workflowDocument.setFimsSample(r.getFimsSample());
                            reactionList.add(workflowDocument);
                        }
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
     * @param workflows x
     * @return x
     */
    public List<WorkflowDocument> combineExtractions(List<WorkflowDocument> workflows) {
        Map<String, WorkflowDocument> workflowMap = new HashMap<String, WorkflowDocument> ();
        for(WorkflowDocument workflow : workflows) {
            if(workflowMap.get(workflow.getName()) != null) {
                WorkflowDocument existingWorkflow = workflowMap.get(workflow.getReactions(Reaction.Type.Extraction).get(0).getExtractionId());
                for(Reaction r : workflow.getReactions()) {
                    if(!existingWorkflow.getReactions().contains(r)) {
                        existingWorkflow.addReaction(r);
                    }
                }
            }
            else {
                String extractionId = null;
                for(Reaction r : workflow.getReactions()) {
                    if(r.getExtractionId() != null) {
                        extractionId = r.getExtractionId();
                        break;
                    }
                }
                if(extractionId != null) { //if there is no extraction ID, this must be an empty workflow = no reason to display it
                    workflowMap.put(extractionId, workflow);
                }
            }
        }
        return new ArrayList<WorkflowDocument>(workflowMap.values());
    }

    /**
     * combines a list of partial workflows (generated by the {@link #getWorkflows(com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument[], com.biomatters.plugins.biocode.labbench.reaction.Reaction.Type)} method for plates, and combines the reactions into their proper workflows.
     * @param workflows x
     * @return x
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

    @Override
    public Options getOptions() {
        Options options = new Options(this.getClass());
        List<Options.OptionValue> values = new ArrayList<Options.OptionValue>();
        values.add(new Options.OptionValue("Workflow", "Workflow Centric"));
        values.add(new Options.OptionValue("Extraction", "Extraction Centric"));
        options.addRadioOption("mode", "", values, values.get(0), Options.Alignment.HORIZONTAL_ALIGN);
        return options;
    }

    //    private int getReactionCount(List<WorkflowDocument> workflows, Reaction.Type type) {
//        int count = 0;
//        for(WorkflowDocument w : workflows) {
//            count += w.getReactions(type).size();
//        }
//        return count;
//    }

    public TableModel getTableModel(final AnnotatedPluginDocument[] docs, Options options) {
        Set<PrimerIdentifier> primerNamesSet = new HashSet<PrimerIdentifier>();
        final boolean workflowCentric = options.getValueAsString("mode").equals("Workflow");
        List<WorkflowDocument> uncombinedWorkflows = getWorkflows(docs, type);
        final List<WorkflowDocument> workflows = workflowCentric ? combineWorkflows(uncombinedWorkflows) : combineExtractions(uncombinedWorkflows);
        final List<DocumentField> fimsFields = BiocodeUtilities.getFimsFields(workflows);
        if(workflows.size() < 2) {
            return null;
        }
        for(WorkflowDocument workflow : workflows) {
            List<Reaction> reactions = workflow.getReactions(type);
            if(type == Reaction.Type.PCR) {
                for(Reaction r : reactions) {
                    DocumentSelectionOption option = (DocumentSelectionOption)r.getOptions().getOption(PCROptions.PRIMER_OPTION_ID);
                    List<AnnotatedPluginDocument> primerValue = option.getDocuments();
                    if(primerValue.size() == 0) {
                        primerNamesSet.add(new PrimerIdentifier(PrimerIdentifier.Type.forward, "None"));
                    }
                    else {
                        AnnotatedPluginDocument document = primerValue.get(0);
                        primerNamesSet.add(new PrimerIdentifier(PrimerIdentifier.Type.forward, document.getName()));
                    }
                }
                for(Reaction r : reactions) {
                    DocumentSelectionOption option = (DocumentSelectionOption)r.getOptions().getOption(PCROptions.PRIMER_REVERSE_OPTION_ID);
                    List<AnnotatedPluginDocument> primerValue = option.getDocuments();
                    if(primerValue.size() == 0) {
                        primerNamesSet.add(new PrimerIdentifier(PrimerIdentifier.Type.reverse, "None"));
                    }
                    else {
                        AnnotatedPluginDocument document = primerValue.get(0);
                        primerNamesSet.add(new PrimerIdentifier(PrimerIdentifier.Type.reverse, document.getName()));
                    }
                }
            }
            else {
                for(Reaction r : reactions) {
                    boolean isForward = CycleSequencingOptions.FORWARD_VALUE.equals(r.getOptions().getValueAsString(CycleSequencingOptions.DIRECTION));
                    DocumentSelectionOption option = (DocumentSelectionOption)r.getOptions().getOption(PCROptions.PRIMER_OPTION_ID);
                    List<AnnotatedPluginDocument> primerValue = option.getDocuments();
                    if(primerValue.size() == 0) {
                        primerNamesSet.add(new PrimerIdentifier(isForward ? PrimerIdentifier.Type.forward : PrimerIdentifier.Type.reverse, "None"));
                    }
                    else {
                        AnnotatedPluginDocument document = primerValue.get(0);
                        primerNamesSet.add(new PrimerIdentifier(isForward ? PrimerIdentifier.Type.forward : PrimerIdentifier.Type.reverse, document.getName()));
                    }
                }
            }

        }
        final List<PrimerIdentifier> primerList = new ArrayList<PrimerIdentifier>(primerNamesSet);

        final ObjectAndColor[][] tableValues = new ObjectAndColor[workflows.size()][primerNamesSet.size()];
        ObjectAndColor notTriedValue = new ObjectAndColor("Not tried", Color.black);
        for (ObjectAndColor[] tableValue : tableValues) {
            Arrays.fill(tableValue, notTriedValue);
        }

        for (int i = 0; i < workflows.size(); i++) {
            WorkflowDocument workflow = workflows.get(i);
            List<Reaction> reactions = workflow.getReactions(type);
            for (int j = 0; j < primerList.size(); j++) {
                PrimerIdentifier primer = primerList.get(j);
                String s = primer.getName();
                for (Reaction r : reactions) {
                    String primerName;
                    List<AnnotatedPluginDocument> primerValue;
                    if(r.getType() == Reaction.Type.PCR) {
                        DocumentSelectionOption option = (DocumentSelectionOption)r.getOptions().getOption(primer.getType() == PrimerIdentifier.Type.forward ? PCROptions.PRIMER_OPTION_ID : PCROptions.PRIMER_REVERSE_OPTION_ID);
                        primerValue = option.getDocuments();
                    }
                    else {
                        DocumentSelectionOption option = (DocumentSelectionOption)r.getOptions().getOption(PCROptions.PRIMER_OPTION_ID);
                        primerValue = option.getDocuments();
                    }
                    if(primerValue.size() == 0) {
                        primerName = "None";
                    }
                    else {
                        AnnotatedPluginDocument document = primerValue.get(0);
                        primerName = document.getName();
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
                return (workflowCentric ? 2 : 1)+fimsFields.size()+primerList.size();
            }

            public String getColumnName(int columnIndex) {
                int colStart = workflowCentric ? 2 : 1;
                if(columnIndex-colStart == -2) {
                    return "Workflow";
                }
                if(columnIndex-colStart == -1) {
                    return "Extraction";
                }
                if(columnIndex < fimsFields.size()+colStart) {
                    return fimsFields.get(columnIndex-colStart).getName();
                }
                PrimerIdentifier primerIdentifier = primerList.get(columnIndex - colStart - fimsFields.size());
                return primerIdentifier.getName() + " ("+primerIdentifier.getType()+")";
            }

            public Class<?> getColumnClass(int columnIndex) {
                int colStart = workflowCentric ? 2 : 1;

                if(columnIndex < colStart) {
                    return String.class;
                }
                if(columnIndex < fimsFields.size() + colStart ) {
                    return fimsFields.get(columnIndex-colStart).getValueType();
                }
                return ObjectAndColor.class;
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                int colStart = workflowCentric ? 2 : 1;

                if(columnIndex-colStart == -2) {
                    return workflows.get(rowIndex).getName();
                }
                if(columnIndex-colStart == -1) {
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
                if(columnIndex < fimsFields.size() + colStart) {
                    WorkflowDocument workflowDocument = workflows.get(rowIndex);
                    if(workflowDocument.getFimsSample() == null) {
                        return "";
                    }
                    return workflowDocument.getFimsSample().getFimsAttributeValue(fimsFields.get(columnIndex-colStart).getCode());
                }
                return tableValues[rowIndex][columnIndex-colStart-fimsFields.size()];
            }

            public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
                //empty!
            }

            public void addTableModelListener(TableModelListener l) {
                //empty!
            }

            public void removeTableModelListener(TableModelListener l) {
                //empty!
            }
        };
    }

    @Override
    protected boolean columnVisibleByDefault(int columnIndex, AnnotatedPluginDocument[] selectedDocuments) {
        final List<WorkflowDocument> workflows = combineWorkflows(getWorkflows(selectedDocuments, type));
        final List<DocumentField> fimsFields = BiocodeUtilities.getFimsFields(workflows);
        return columnIndex < 2 || columnIndex >= fimsFields.size() + 2;
    }
}
