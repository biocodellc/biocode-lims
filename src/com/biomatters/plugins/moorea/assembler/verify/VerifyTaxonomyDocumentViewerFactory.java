package com.biomatters.plugins.moorea.assembler.verify;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.implementations.Percentage;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.plugins.moorea.labbench.TableDocumentViewerFactory;
import com.biomatters.plugins.moorea.labbench.TableSorter;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;

/**
 * @author Richard
 * @version $Id$
 */
public class VerifyTaxonomyDocumentViewerFactory extends TableDocumentViewerFactory {

    public TableModel getTableModel(AnnotatedPluginDocument[] docs) {
        return new VerifyTaxonomyTableModel((VerifyTaxonomyResultsDocument)docs[0].getDocumentOrCrash());
    }

    @Override
    public void messWithTheTable(JTable table) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(table.getRowHeight() * 5);
        table.setDefaultRenderer(String.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component cellRendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (cellRendererComponent instanceof JLabel) {
                    JLabel label = (JLabel) cellRendererComponent;
                    if (!label.getText().startsWith("<html>")) {
                        label.setText("<html>" + label.getText() + "</html>");
                    }
                    label.setVerticalAlignment(JLabel.TOP);
                }
                return cellRendererComponent;
            }
        });
        table.setDefaultRenderer(Integer.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component cellRendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (cellRendererComponent instanceof JLabel) {
                    JLabel label = (JLabel) cellRendererComponent;
                    label.setVerticalAlignment(JLabel.TOP);
                    label.setHorizontalAlignment(JLabel.LEFT);
                }
                return cellRendererComponent;
            }
        });
        table.setDefaultRenderer(Percentage.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component cellRendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (cellRendererComponent instanceof JLabel) {
                    JLabel label = (JLabel) cellRendererComponent;
                    label.setVerticalAlignment(JLabel.TOP);
                    label.setHorizontalAlignment(JLabel.LEFT);
                }
                return cellRendererComponent;
            }
        });
        TableModel model = ((TableSorter) table.getModel()).getTableModel();
        ((VerifyTaxonomyTableModel)model).setColumnWidths(table.getColumnModel());
    }

    public String getName() {
        return "Verify Taxonomy";
    }

    public String getDescription() {
        return "Displays the results of verifying taxonomy";
    }

    public String getHelp() {
        return "It is not common sense to eat delicious dinner inside someone else's closet!";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(VerifyTaxonomyResultsDocument.class, 1, 1)
        };
    }
}
