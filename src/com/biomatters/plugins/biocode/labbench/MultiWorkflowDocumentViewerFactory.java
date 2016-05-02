package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.utilities.ObjectAndColor;

import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.List;

/**
 * @author Steven Stones-Havas
 *          <p/>
 *          Created on 28/06/2009 5:27:11 PM
 */
public class MultiWorkflowDocumentViewerFactory extends TableDocumentViewerFactory {
    private static String[] columnNames = new String[] {
            "Extraction",
            "PCR forward primer",
            "PCR reverse primer",
            "PCR plate",
            "PCR status",
            "Cycle Sequencing primer (forward)",
            "Cycle Sequencing Plate (forward)",
            "Cycle sequencing status (forward)",
            "Cycle Sequencing primer (reverse)",
            "Cycle Sequencing Plate (reverse)",
            "Cycle sequencing status (reverse)",
            LIMSConnection.SEQUENCE_PROGRESS.getName(),
            CycleSequencingReaction.NUM_TRACES_FIELD.getName(),
            CycleSequencingReaction.NUM_SEQS_FIELD.getName(),
            CycleSequencingReaction.NUM_PASSED_SEQS_FIELD.getName(),
            WorkflowDocument.EXTRACTION_PLATE_NAME_DOCUMENT_FIELD.getName(),
            WorkflowDocument.EXTRACTION_WELL_DOCUMENT_FIELD.getName()
    };

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

    @Override
    protected boolean columnVisibleByDefault(int columnIndex, AnnotatedPluginDocument[] selectedDocuments) {
        if (columnIndex == 0) {
            return true;
        }
        final List<DocumentField> fimsFields = BiocodeUtilities.getFimsFields(BiocodeUtilities.getWorkflowDocuments(selectedDocuments));
        return columnIndex > fimsFields.size() + 1;
    }

    public TableModel getTableModel(final AnnotatedPluginDocument[] docs, Options options) {
        final List<WorkflowDocument> workflowDocuments = BiocodeUtilities.getWorkflowDocuments(docs);
        final List<DocumentField> fimsFields = BiocodeUtilities.getFimsFields(workflowDocuments);
        if (workflowDocuments.size() == 0) {
            return null;
        }
        return new TableModel(){
            public int getRowCount() {
                return workflowDocuments.size();
            }

            public int getColumnCount() {
                return columnNames.length + fimsFields.size() + 1;
            }

            public String getColumnName(int columnIndex) {
                if (columnIndex == 0) {
                    return "Name";
                }

                if (columnIndex <= fimsFields.size()) {
                    return fimsFields.get(columnIndex - 1).getName();
                }

                return columnNames[columnIndex - fimsFields.size() - 1];
            }

            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) {
                    return String.class;
                } else if (columnIndex <= fimsFields.size()) {
                    return fimsFields.get(columnIndex - 1).getValueType();
                } else if (columnIndex < fimsFields.size() + 2) {
                    return String.class;
                } else if (columnIndex < fimsFields.size() + 13) {
                    return ObjectAndColor.class;
                }
                return String.class;
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
                if (columnIndex == 0) {
                    return doc.getName();
                }
                if (columnIndex <= fimsFields.size()) {
                    FimsSample fimsSample = doc.getFimsSample();
                    if (fimsSample == null) {
                        return null;
                    }
                    return fimsSample.getFimsAttributeValue(fimsFields.get(columnIndex - 1).getCode());
                }

                int adjustedColumnIndex = columnIndex - fimsFields.size();

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
                    case 12:
                        Object sequenceProgress = doc.getFieldValue(LIMSConnection.SEQUENCE_PROGRESS.getCode());
                        return sequenceProgress != null ? new ObjectAndColor(sequenceProgress, sequenceProgress.equals(LIMSConnection.SEQUENCE_PROGRESS.getEnumerationValues()[0]) ? Color.GREEN.darker() : Color.RED.darker()) : null;
                    case 13:
                        return doc.getFieldValue(CycleSequencingReaction.NUM_TRACES_FIELD.getCode());
                    case 14:
                        return doc.getFieldValue(CycleSequencingReaction.NUM_SEQS_FIELD.getCode());
                    case 15:
                        return doc.getFieldValue(CycleSequencingReaction.NUM_PASSED_SEQS_FIELD.getCode());
                    case 16:
                        return doc.getFieldValue(WorkflowDocument.EXTRACTION_PLATE_NAME_DOCUMENT_FIELD.getCode());
                    case 17:
                        return doc.getFieldValue(WorkflowDocument.EXTRACTION_WELL_DOCUMENT_FIELD.getCode());
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