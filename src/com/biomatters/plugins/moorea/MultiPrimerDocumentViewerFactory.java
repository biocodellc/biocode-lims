package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.plugins.moorea.reaction.Reaction;

import javax.swing.table.TableModel;
import javax.swing.event.TableModelListener;
import java.util.*;
import java.util.List;
import java.awt.*;

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
        return "An overvoew of all "+type.toString()+" reactions performed on the selected workflows";
    }

    public String getHelp() {
        return null;
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(WorkflowDocument.class, 2, Integer.MAX_VALUE)
        };
    }

    public TableModel getTableModel(final AnnotatedPluginDocument[] docs) {
        Set<String> primerNamesSet = new HashSet<String>();
        for(AnnotatedPluginDocument doc : docs) {
            WorkflowDocument workflow = (WorkflowDocument)doc.getDocumentOrCrash();
            List<Reaction> reactions = workflow.getReactions(type);
            for(Reaction r : reactions) {
                primerNamesSet.add(((Options.OptionValue)r.getOptions().getValue("primer")).getName());
            }
        }
        final List<String> primerList = new ArrayList<String>(primerNamesSet);

        final ObjectAndColor[][] tableValues = new ObjectAndColor[docs.length][primerNamesSet.size()];
        ObjectAndColor notTriedValue = new ObjectAndColor("Not tried", Color.black);
        for(int i=0; i < tableValues.length; i++) {
            Arrays.fill(tableValues[i], notTriedValue);
        }

        for (int i = 0; i < docs.length; i++) {
            AnnotatedPluginDocument doc = docs[i];
            WorkflowDocument workflow = (WorkflowDocument) doc.getDocumentOrCrash();
            List<Reaction> reactions = workflow.getReactions(type);
            for (int j = 0; j < primerList.size(); j++) {
                String s = primerList.get(j);
                for (Reaction r : reactions) {
                    String primerName = ((Options.OptionValue) r.getOptions().getValue("primer")).getName();
                    if (primerName.equals(s)) {
                        tableValues[i][j] = new ObjectAndColor(r.getOptions().getValueAsString("runStatus"), r.getBackgroundColor());
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
                    return "Name";
                }
                if(columnIndex == 1) {
                    return "Extraction";
                }
                return primerList.get(columnIndex-2);
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
                    return docs[rowIndex].getName();
                }
                if(columnIndex == 1) {
                    WorkflowDocument workflowDocument = (WorkflowDocument)docs[rowIndex].getDocumentOrCrash();
                    return workflowDocument.getMostRecentReaction(Reaction.Type.Extraction).getExtractionId();
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
