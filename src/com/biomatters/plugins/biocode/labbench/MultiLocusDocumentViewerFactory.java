package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.reaction.*;

import javax.swing.table.TableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.event.TableModelListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.util.*;
import java.util.List;
import java.awt.*;

/**
 * @author Steve
 */
public class MultiLocusDocumentViewerFactory extends TableDocumentViewerFactory{



    protected TableModel getTableModel(AnnotatedPluginDocument[] docs, Options options) {
        final List<WorkflowDocument> workflows = BiocodeUtilities.getWorkflowDocuments(docs);
        final List<ExtractionAndWorkflow> extractionAndWorkflows = getExtractionAndWorkflows(workflows);
        final List<DocumentField> fimsFields = BiocodeUtilities.getFimsFields(workflows);
        final List<String> loci = getLoci(extractionAndWorkflows);


        return new TableModel(){
            public int getRowCount() {
                return extractionAndWorkflows.size();
            }

            public int getColumnCount() {
                return 1 + fimsFields.size() + loci.size();
            }

            public String getColumnName(int columnIndex) {
                if(columnIndex == 0) {
                    return "Extraction ID";
                }
                if(columnIndex <= fimsFields.size()) {
                    return fimsFields.get(columnIndex-1).getName();
                }
                return loci.get(columnIndex-fimsFields.size()-1);
            }

            public Class<?> getColumnClass(int columnIndex) {
                if(columnIndex == 0) {
                    return String.class;
                }
                if(columnIndex <= fimsFields.size()) {
                    return fimsFields.get(columnIndex-1).getValueType();
                }
                return LocusReactions.class;
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                ExtractionAndWorkflow workflow = extractionAndWorkflows.get(rowIndex);
                if(columnIndex == 0) {
                    return workflow.getExtraction().getExtractionId();
                }
                if(columnIndex <= fimsFields.size()) {
                    return workflow.getExtraction().getFieldValue(fimsFields.get(columnIndex-1).getCode());
                }
                return workflow.getLocusReactions(loci.get(columnIndex-fimsFields.size()-1));
            }

            public void setValueAt(Object aValue, int rowIndex, int columnIndex) {}

            public void addTableModelListener(TableModelListener l) {}

            public void removeTableModelListener(TableModelListener l) {}
        };
    }

    @Override
    protected void messWithTheTable(JTable table, TableModel model) {
        super.messWithTheTable(table, model);
        table.setDefaultRenderer(LocusReactions.class, new DefaultTableCellRenderer(){
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component rendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if(rendererComponent instanceof JComponent) {
                 ((JComponent)rendererComponent).setBorder(new EmptyBorder(5,5,5,5));
                }
                return rendererComponent;
            }
        });
        table.setRowHeight(4*table.getRowHeight()+10);//5px padding
    }

    public String getName() {
        return "Locus Overview";
    }

    public String getDescription() {
        return "View a summary of multiple workflows by locus";
    }

    public String getHelp() {
        return "This view displays a summary of each locus run on the samples in the selected workflows.  It shows the most recent reactions of each type run in for each locus.  Green text represents a passed reaction, while red text represents a failed reaction.";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {new DocumentSelectionSignature(WorkflowDocument.class, 2, Integer.MAX_VALUE)};
    }

    @Override
    protected boolean columnVisibleByDefault(int columnIndex, AnnotatedPluginDocument[] selectedDocuments) {
        if(columnIndex == 0) {
            return true;
        }
        final List<WorkflowDocument> workflowDocuments = BiocodeUtilities.getWorkflowDocuments(selectedDocuments);
        final List<DocumentField> fimsFields = BiocodeUtilities.getFimsFields(workflowDocuments);
        return columnIndex > fimsFields.size();
    }

    private List<ExtractionAndWorkflow> getExtractionAndWorkflows(List<WorkflowDocument> workflows) {
        Map<String, ExtractionAndWorkflow> result = new LinkedHashMap<String, ExtractionAndWorkflow>();
        for(WorkflowDocument workflow : workflows) {
            List<Reaction> extractionReactions = workflow.getReactions(Reaction.Type.Extraction);
            assert extractionReactions.size() > 0;
            if(extractionReactions.size() > 0) {
                Reaction extraction = extractionReactions.get(0);
                ExtractionAndWorkflow extractionAndWorkflow = result.get(extraction.getExtractionId());
                if(extractionAndWorkflow == null) {
                    extractionAndWorkflow = new ExtractionAndWorkflow((ExtractionReaction)extraction);
                    result.put(extraction.getExtractionId(), extractionAndWorkflow);
                }
                extractionAndWorkflow.addReaction(workflow.getMostRecentReaction(Reaction.Type.PCR));
                extractionAndWorkflow.addReaction(workflow.getMostRecentSequencingReaction(true));
                extractionAndWorkflow.addReaction(workflow.getMostRecentSequencingReaction(false));
            }
        }
        return new ArrayList<ExtractionAndWorkflow>(result.values());
    }

    private List<String> getLoci(List<ExtractionAndWorkflow> workflows) {
        Set<String> loci = new LinkedHashSet<String>();
        for(ExtractionAndWorkflow workflow : workflows) {
            loci.addAll(workflow.getLoci());
        }
        return new ArrayList<String>(loci);
    }


    private static class ExtractionAndWorkflow {
        private ExtractionReaction extraction;
        private Map<String, LocusReactions> locusReactions;

        public ExtractionAndWorkflow(ExtractionReaction extraction) {
            this.extraction = extraction;
            locusReactions = new HashMap<String, LocusReactions>();
        }

        public ExtractionReaction getExtraction() {
            return extraction;
        }

        public LocusReactions getLocusReactions(String locus) {
            return locusReactions.get(locus);
        }

        public void addReaction(Reaction r) {
            if(r == null) {
                return;
            }
            LocusReactions locusReaction = locusReactions.get(r.getLocus());
            if(locusReaction == null) {
                locusReaction = new LocusReactions();
                locusReactions.put(r.getLocus(), locusReaction);
            }
            locusReaction.addReaction(r);
        }

        public Collection<String> getLoci() {
            return locusReactions.keySet();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ExtractionAndWorkflow that = (ExtractionAndWorkflow) o;

            return extraction.getExtractionId().equals(that.extraction.getExtractionId());
        }

        @Override
        public int hashCode() {
            return extraction.getExtractionId().hashCode();
        }
    }

    private static class LocusReactions {
        private PCRReaction pcrReaction;
        private CycleSequencingReaction forwardSequencingReaction;
        private CycleSequencingReaction reverseSequencingReaction;

        public LocusReactions() {}

        public LocusReactions(PCRReaction pcrReaction, CycleSequencingReaction forwardSequencingReaction, CycleSequencingReaction reverseSequencingReaction) {
            this.pcrReaction = pcrReaction;
            this.forwardSequencingReaction = forwardSequencingReaction;
            this.reverseSequencingReaction = reverseSequencingReaction;
        }

        public PCRReaction getPcrReaction() {
            return pcrReaction;
        }

        public void setPcrReaction(PCRReaction pcrReaction) {
            this.pcrReaction = pcrReaction;
        }

        public CycleSequencingReaction getForwardSequencingReaction() {
            return forwardSequencingReaction;
        }

        public void setForwardSequencingReaction(CycleSequencingReaction forwardSequencingReaction) {
            this.forwardSequencingReaction = forwardSequencingReaction;
        }

        public CycleSequencingReaction getReverseSequencingReaction() {
            return reverseSequencingReaction;
        }

        public void setReverseSequencingReaction(CycleSequencingReaction reverseSequencingReaction) {
            this.reverseSequencingReaction = reverseSequencingReaction;
        }

        public void addReaction(Reaction r) {
            switch(r.getType()) {
                case Extraction:
                    throw new IllegalArgumentException("Cannot add extraction reactions");
                case PCR:
                    if(!(pcrReaction != null && pcrReaction.getDate().getTime() > r.getDate().getTime())) {
                        pcrReaction = (PCRReaction)r;
                    }
                    break;
                case CycleSequencing:
                    if(((Options.OptionValue)r.getOptions().getValue(CycleSequencingOptions.DIRECTION)).getName().equals(CycleSequencingOptions.FORWARD_VALUE)) {
                        if(!(forwardSequencingReaction != null && forwardSequencingReaction.getDate().getTime() > r.getDate().getTime()) || forwardSequencingReaction == null) {
                            forwardSequencingReaction = (CycleSequencingReaction)r;
                        }
                    }
                    else {
                        if(!(reverseSequencingReaction != null && reverseSequencingReaction.getDate().getTime() > r.getDate().getTime()) || reverseSequencingReaction == null) {
                            reverseSequencingReaction = (CycleSequencingReaction)r;
                        }
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported reaction type: "+r.getType());
            }
        }

        @SuppressWarnings({"StringConcatenationInsideStringBufferAppend"})
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("<html><head><1></head><body style=\"font-size: 10pt;\">");
            builder.append("<font"+getColorString(pcrReaction)+"><b>PCR (f): </b>"+(pcrReaction != null ? pcrReaction.getFieldValue(PCROptions.PRIMER_OPTION_ID) : "<i>not run...</i>")+"<br>");
            builder.append("<font"+getColorString(pcrReaction)+"><b>PCR (r): </b>"+(pcrReaction != null ? pcrReaction.getFieldValue(PCROptions.PRIMER_REVERSE_OPTION_ID) : "<i>not run...</i>")+"<br><br>");

            builder.append("<font"+getColorString(forwardSequencingReaction)+"><b>Sequencing (f): </b>"+(forwardSequencingReaction != null ? forwardSequencingReaction.getFieldValue(CycleSequencingOptions.PRIMER_OPTION_ID) : "<i>not run...</i>")+"<br>");
            builder.append("<font"+getColorString(reverseSequencingReaction)+"><b>Sequencing (r): </b>"+(reverseSequencingReaction != null ? reverseSequencingReaction.getFieldValue(CycleSequencingOptions.PRIMER_OPTION_ID) : "<i>not run...</i>"));
            builder.append("</body></html>");
            return builder.toString();
        }

        private static String getColorString(Reaction r) {
            if(r == null) {
                return " color='#000000'";
            }
            if(ReactionOptions.FAILED_VALUE.getLabel().equals(r.getFieldValue(ReactionOptions.RUN_STATUS))) {
                return " color='#C13D26'";
            }
            else if(ReactionOptions.PASSED_VALUE.getLabel().equals(r.getFieldValue(ReactionOptions.RUN_STATUS))) {
                return " color='#49993F'";
            }
            else if (ReactionOptions.RUN_VALUE.getLabel().equals(r.getFieldValue(ReactionOptions.RUN_STATUS))) {
                return " color='#fe9418'";
            }
            else {
                return " color='#000000'";
            }
        }
    }
}
