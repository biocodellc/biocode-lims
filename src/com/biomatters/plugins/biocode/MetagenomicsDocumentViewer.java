package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.URN;
import com.biomatters.geneious.publicapi.implementations.EValue;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.Icons;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.plugins.biocode.labbench.TableDocumentViewerFactory;

import javax.swing.*;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Created with IntelliJ IDEA.
 * User: Steve
 * Date: 5/04/13
 * Time: 11:00 PM
 * To change this template use File | Settings | File Templates.
 */
public class MetagenomicsDocumentViewer extends TableDocumentViewerFactory {

    private static class NameAndUrn{
        private String name;
        private URN urn;

        private NameAndUrn(String name, URN urn) {
            this.name = name;
            this.urn = urn;
        }

        public String getName() {
            return name;
        }

        public URN getUrn() {
            return urn;
        }

        public String toString() {
            return getName();
        }
    }

    @Override
    protected TableModel getTableModel(AnnotatedPluginDocument[] docs, Options options) {
        final MetagenomicsDocument doc = (MetagenomicsDocument)docs[0].getDocumentOrNull();
        return new TableModel() {
            public int getRowCount() {
                return doc.getOTUCount();
            }

            public int getColumnCount() {
                return 5;
            }

            public String getColumnName(int i) {
                String[] colNames = new String[] {"Contig", "e-Value", "Taxonomy", "Keywords/Description", "# sequences"};
                return colNames[i];
            }

            public Class<?> getColumnClass(int i) {
                return i == 4 ? Integer.class : i == 0 ? NameAndUrn.class : i == 1 ? EValue.class : String.class;
            }

            public boolean isCellEditable(int i, int i1) {
                return false;
            }

            public Object getValueAt(int row, int col) {
                MetagenomicsDocument.OTU otu = doc.getOTU(row);
                if(col == 0 && otu.getUrn() != null) {
                    return new NameAndUrn(otu.getContigName(), otu.getUrn());
                }
                if(col == 1) {
                    return otu.geteValue();
                }
                if(col == 2) {
                    return otu.getTaxonomy();
                }
                if(col ==3) {
                    return otu.getDescription();
                }
                if(col == 4) {
                    return otu.getSequenceCount();
                }
                return null;

            }

            public void setValueAt(Object o, int i, int i1) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            public void addTableModelListener(TableModelListener tableModelListener) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            public void removeTableModelListener(TableModelListener tableModelListener) {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        };
    }

    @Override
    protected void messWithTheTable(final JTable table, final TableModel model) {
        super.messWithTheTable(table, model);
        table.setDefaultRenderer(NameAndUrn.class, new DefaultTableCellRenderer(){
            @Override
            public Component getTableCellRendererComponent(JTable jTable, Object o, boolean b, boolean b1, int i, int i1) {
                String value = null;
                if(o != null)
                    value = ((NameAndUrn) o).getName();
                JLabel label = (JLabel)super.getTableCellRendererComponent(jTable, value, b, b1, i, i1);
                label.setIcon(IconUtilities.getIcons("goToReferenceBlue.png").getOriginalIcon());
                return label;
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                if(table.columnAtPoint(mouseEvent.getPoint()) == 0) {
                    NameAndUrn nameAndUrn = (NameAndUrn)model.getValueAt(table.rowAtPoint(mouseEvent.getPoint()), 0);
                    if(nameAndUrn != null) {
                        DocumentUtilities.selectDocument(nameAndUrn.getUrn());
                    }
                }
            }
        });

    }

    @Override
    public String getName() {
        return "Contigs";
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public String getHelp() {
        return null;
    }

    @Override
    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[]{new DocumentSelectionSignature(MetagenomicsDocument.class, 1, 1)};
    }
}
