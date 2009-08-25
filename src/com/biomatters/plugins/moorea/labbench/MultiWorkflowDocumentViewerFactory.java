package com.biomatters.plugins.moorea.labbench;

import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.plugins.moorea.labbench.reaction.PCROptions;
import com.biomatters.plugins.moorea.labbench.reaction.CycleSequencingOptions;
import com.biomatters.plugins.moorea.labbench.reaction.Reaction;
import com.biomatters.plugins.moorea.labbench.reaction.Cocktail;

import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 28/06/2009 5:27:11 PM
 */
public class MultiWorkflowDocumentViewerFactory extends TableDocumentViewerFactory{
    public String getName() {
        return "Summary View";
    }

    public String getDescription() {
        return "View a summary of multiple workflows";
    }

    public String getHelp() {
        return null;
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {new DocumentSelectionSignature(WorkflowDocument.class, 2, Integer.MAX_VALUE)};
    }


    public TableModel getTableModel(final AnnotatedPluginDocument[] docs) {
        TableModel tableModel = new TableModel(){
            public int getRowCount() {
                return docs.length;
            }

            public int getColumnCount() {
                return 7;
            }

            public String getColumnName(int columnIndex) {
                String[] names = new String[] {"Name", "Extraction", "PCR forward primer", "PCR reverse primer", "PCR plate", "Cycle Sequencing", "Cycle Sequencing Plate"};
                return names[columnIndex];
            }

            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex < 2 ? String.class : ObjectAndColor.class;
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                WorkflowDocument doc = (WorkflowDocument)docs[rowIndex].getDocumentOrCrash();
                Reaction recentExtraction = doc.getMostRecentReaction(Reaction.Type.Extraction);
                Reaction recentPCR = doc.getMostRecentReaction(Reaction.Type.PCR);
                Reaction recentCycleSequencing = doc.getMostRecentReaction(Reaction.Type.CycleSequencing);
                switch(columnIndex) {
                    case 0 :
                        return doc.getName();
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
                        if(recentCycleSequencing == null) return null;
                        cocktail = recentCycleSequencing.getCocktail();
                        return new ObjectAndColor(recentCycleSequencing.getFieldValue(CycleSequencingOptions.PRIMER_OPTION_ID) + (cocktail != null ? ", "+cocktail.getName() : ""), recentCycleSequencing.getBackgroundColor());
                    case 6 :
                        return recentCycleSequencing != null ? new ObjectAndColor(recentCycleSequencing.getPlateName()+" "+recentCycleSequencing.getLocationString(), recentCycleSequencing.getBackgroundColor()) : null;
                }
                return null;
            }

            public void setValueAt(Object aValue, int rowIndex, int columnIndex) {}

            public void addTableModelListener(TableModelListener l) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            public void removeTableModelListener(TableModelListener l) {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        };
        return tableModel;
    }
}
