package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.plugin.DocumentViewerFactory;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.DocumentViewer;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.plugins.moorea.reaction.PCRReaction;
import com.biomatters.plugins.moorea.reaction.PCROptions;
import com.biomatters.plugins.moorea.reaction.CycleSequencingOptions;
import com.biomatters.plugins.moorea.reaction.Reaction;

import javax.swing.*;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

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
                return 4;
            }

            public String getColumnName(int columnIndex) {
                String[] names = new String[] {"Name", "Extraction", "PCR", "Cycle Sequencing"};
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
                switch(columnIndex) {
                    case 0 :
                        return doc.getName();
                    case 1 :
                        Reaction recentExtraction = doc.getMostRecentReaction(Reaction.Type.Extraction);
                        return recentExtraction != null ? recentExtraction.getExtractionId() : null;
                    case 2 :
                        Reaction recentPCR = doc.getMostRecentReaction(Reaction.Type.PCR);
                        return recentPCR != null ? new ObjectAndColor(recentPCR.getFieldValue(PCROptions.PRIMER_OPTION_ID), recentPCR.getBackgroundColor()) : null;
                    case 3 :
                        Reaction recentCycleSequencing = doc.getMostRecentReaction(Reaction.Type.CycleSequencing);
                        return recentCycleSequencing != null ? new ObjectAndColor(recentCycleSequencing.getFieldValue(CycleSequencingOptions.PRIMER_OPTION_ID), recentCycleSequencing.getBackgroundColor()) : null;
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
