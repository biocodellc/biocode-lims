package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.utilities.ObjectAndColor;

import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 28/06/2009 5:27:11 PM
 */
public class MultiWorkflowDocumentViewerFactory extends TableDocumentViewerFactory{
    public String getName() {
        return "Primer Overview";
    }

    public String getDescription() {
        return "View a summary of multiple workflows";
    }

    public String getHelp() {
        return "This view displays a summary of the most recent reactions of each type run in the selected workflows.  Green text represents a passed reaction, while red text represents a failed reaction.";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {new DocumentSelectionSignature(WorkflowDocument.class, 2, Integer.MAX_VALUE)};
    }

    public List<WorkflowDocument> getWorkflowDocuments(AnnotatedPluginDocument[] docs) {
        List<WorkflowDocument> workflows = new ArrayList<WorkflowDocument>();
        for(AnnotatedPluginDocument doc : docs) {
            if(WorkflowDocument.class.isAssignableFrom(doc.getDocumentClass())) {
                workflows.add((WorkflowDocument)doc.getDocumentOrCrash());
            }
        }
        return workflows;
    }

    private static List<DocumentField> getFimsFields(List<WorkflowDocument> docs) {
        Set<DocumentField> fields = new LinkedHashSet<DocumentField>();
        for(WorkflowDocument doc : docs) {
            fields.addAll(doc.getFimsSample().getFimsAttributes());
        }
        return new ArrayList<DocumentField>(fields);
    }

    @Override
    protected boolean columnVisibleByDefault(int columnIndex, AnnotatedPluginDocument[] selectedDocuments) {
        if(columnIndex == 0) {
            return true;
        }
        final List<WorkflowDocument> workflowDocuments = getWorkflowDocuments(selectedDocuments);
        final List<DocumentField> fimsFields = getFimsFields(workflowDocuments);
        return columnIndex > fimsFields.size()+1;
    }

    public TableModel getTableModel(final AnnotatedPluginDocument[] docs) {
        final List<WorkflowDocument> workflowDocuments = getWorkflowDocuments(docs);
        final List<DocumentField> fimsFields = getFimsFields(workflowDocuments);
        if(workflowDocuments.size() == 0) {
            return null;
        }
        return new TableModel(){
            public int getRowCount() {
                return workflowDocuments.size();
            }

            public int getColumnCount() {
                return 12+fimsFields.size();
            }

            public String getColumnName(int columnIndex) {
                if(columnIndex == 0) {
                    return "Name";
                }
                if(columnIndex <= fimsFields.size()) {
                    return fimsFields.get(columnIndex-1).getName();
                }
                String[] names = new String[] {"Extraction", "PCR forward primer", "PCR reverse primer", "PCR plate", "PCR status", "Cycle Sequencing primer (forward)", "Cycle Sequencing Plate (forward)", "Cycle sequencing status (forward)", "Cycle Sequencing primer (reverse)", "Cycle Sequencing Plate (reverse)", "Cycle sequencing status (reverse)"};
                return names[columnIndex-fimsFields.size()-1];
            }

            public Class<?> getColumnClass(int columnIndex) {
                if(columnIndex == 0) {
                    return String.class;
                }
                if(columnIndex <= fimsFields.size()) {
                    return fimsFields.get(columnIndex-1).getValueType();
                }
                return columnIndex < fimsFields.size()+2 ? String.class : ObjectAndColor.class;
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                WorkflowDocument doc = workflowDocuments.get(rowIndex);
                Reaction recentExtraction = doc.getMostRecentReaction(Reaction.Type.Extraction);
                Reaction recentPCR = doc.getMostRecentReaction(Reaction.Type.PCR);
                Reaction recentCycleSequencingForward = doc.getMostRecentSequencingReaction(true);
                Reaction recentCycleSequencingReverse = doc.getMostRecentSequencingReaction(false);
                if(columnIndex == 0) {
                    return doc.getName();
                }
                if(columnIndex <= fimsFields.size()) {
                    return doc.getFimsSample().getFimsAttributeValue(fimsFields.get(columnIndex-1).getCode());
                }
                int adjustedColumnIndex = columnIndex-fimsFields.size();

                switch(adjustedColumnIndex) {
                    case 1 :
                        return recentExtraction != null ? recentExtraction.getExtractionId() : null;
                    case 2 :
                        if(recentPCR == null) return null;
                        Cocktail cocktail = recentPCR.getCocktail();
                        return new ObjectAndColor(recentPCR.getFieldValue(PCROptions.PRIMER_OPTION_ID) + (cocktail != null ? ", "+cocktail.getName() : ""), recentPCR.getBackgroundColor());
                    case 3 :
                        if(recentPCR == null) return null;
                        cocktail = recentPCR.getCocktail();
                        return new ObjectAndColor(recentPCR.getFieldValue(PCROptions.PRIMER_REVERSE_OPTION_ID) + (cocktail != null ? ", "+cocktail.getName() : ""), recentPCR.getBackgroundColor());
                    case 4 :
                        return recentPCR != null ? new ObjectAndColor(recentPCR.getPlateName()+" "+recentPCR.getLocationString(), recentPCR.getBackgroundColor()) : null;
                    case 5 :
                        return recentPCR != null ? new ObjectAndColor(recentPCR.getFieldValue(ReactionOptions.RUN_STATUS), recentPCR.getBackgroundColor()) : null;
                    case 6 :
                        if(recentCycleSequencingForward == null) return null;
                        cocktail = recentCycleSequencingForward.getCocktail();
                        return new ObjectAndColor(recentCycleSequencingForward.getFieldValue(CycleSequencingOptions.PRIMER_OPTION_ID) + (cocktail != null ? ", "+cocktail.getName() : ""), recentCycleSequencingForward.getBackgroundColor());
                    case 7 :
                        return recentCycleSequencingForward != null ? new ObjectAndColor(recentCycleSequencingForward.getPlateName()+" "+recentCycleSequencingForward.getLocationString(), recentCycleSequencingForward.getBackgroundColor()) : null;
                    case 8 :
                        return recentCycleSequencingForward != null ? new ObjectAndColor(recentCycleSequencingForward.getFieldValue(ReactionOptions.RUN_STATUS), recentCycleSequencingForward.getBackgroundColor()) : null;
                    case 9 :
                        if(recentCycleSequencingReverse == null) return null;
                        cocktail = recentCycleSequencingReverse.getCocktail();
                        return new ObjectAndColor(recentCycleSequencingReverse.getFieldValue(CycleSequencingOptions.PRIMER_OPTION_ID) + (cocktail != null ? ", "+cocktail.getName() : ""), recentCycleSequencingReverse.getBackgroundColor());
                    case 10 :
                        return recentCycleSequencingReverse != null ? new ObjectAndColor(recentCycleSequencingReverse.getPlateName()+" "+recentCycleSequencingReverse.getLocationString(), recentCycleSequencingReverse.getBackgroundColor()) : null;
                    case 11 :
                        return recentCycleSequencingReverse != null ? new ObjectAndColor(recentCycleSequencingReverse.getFieldValue(ReactionOptions.RUN_STATUS), recentCycleSequencingReverse.getBackgroundColor()) : null;
                }
                return null;
            }

            public void setValueAt(Object aValue, int rowIndex, int columnIndex) {}

            public void addTableModelListener(TableModelListener l) {
                //blank!
            }

            public void removeTableModelListener(TableModelListener l) {
                //blank!
            }
        };
    }
}
