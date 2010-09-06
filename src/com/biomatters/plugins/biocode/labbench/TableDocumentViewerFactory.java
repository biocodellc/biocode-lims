package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.components.GTable;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.ActionProvider;
import com.biomatters.geneious.publicapi.plugin.DocumentViewer;
import com.biomatters.geneious.publicapi.plugin.DocumentViewerFactory;
import com.biomatters.geneious.publicapi.plugin.ExtendedPrintable;
import com.biomatters.utilities.ObjectAndColor;

import javax.swing.*;
import javax.swing.text.View;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 7/07/2009 6:39:05 PM
 */
public abstract class TableDocumentViewerFactory extends DocumentViewerFactory{

    public abstract TableModel getTableModel(AnnotatedPluginDocument[] docs);

    /**
     * Override this to make changes to the table before Geneious gets hold of it
     *
     * @param table
     */
    protected void messWithTheTable(JTable table) {

    }

    /**
     * override this for an action provider
     *
     * @param table
     * @return
     */
    protected ActionProvider getActionProvider(JTable table) {
        return null;
    }

    public DocumentViewer createViewer(final AnnotatedPluginDocument[] annotatedDocuments) {
        final TableModel model = getTableModel(annotatedDocuments);
        if(model == null) {
            return null;
        }
        return new DocumentViewer(){
            JTable table;
            public JComponent getComponent() {
                TableSorter sorter = new TableSorter(model);
                final AtomicReference<JScrollPane> scroller = new AtomicReference<JScrollPane>();
                table = new GTable(sorter){
                    @Override
                    public Dimension getPreferredSize() {
                        Dimension size = super.getPreferredSize();
                        return new Dimension(Math.max(scroller.get().getViewportBorderBounds().width, size.width), size.height);
                    }

                };
                scroller.set(new JScrollPane(table));
                table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);                
                table.setGridColor(Color.lightGray);
                sorter.setTableHeader(table.getTableHeader());
                table.setDefaultRenderer(ObjectAndColor.class, new DefaultTableCellRenderer(){
                    @Override
                    public Component getTableCellRendererComponent(JTable table, Object avalue, boolean isSelected, boolean hasFocus, int row, int column) {
                        ObjectAndColor value = (ObjectAndColor)avalue;
                        Component comp = super.getTableCellRendererComponent(table, value == null ? null : value.getObject(), isSelected, hasFocus, row, column);

                        Color color = Color.black;
                        if(value != null){
                            color = value.getColor(isSelected).equals(Color.white) ? Color.black : value.getColor(isSelected);
                        }
                        comp.setForeground(color);
                        if(comp instanceof JLabel) {
                            Dimension d = getWidthRestrictedPreferredSize((JLabel)comp, table.getColumnModel().getColumn(column).getWidth());
                            if(d.height > table.getRowHeight(row)) {
                                table.setRowHeight(row, d.height);
                            }
                        }

                        return comp;
                    }
                });
                messWithTheTable(table);
                return scroller.get();
            }

            @Override
            public ExtendedPrintable getExtendedPrintable() {
                return new JTablePrintable(table);
            }

            @Override
            public ActionProvider getActionProvider() {
                return TableDocumentViewerFactory.this.getActionProvider(table);
            }
        };
    }

    /**
     * Returns the preferred size to set a component at in order to render an html string. You can     * specify the size of the width.
     */
    protected static java.awt.Dimension getWidthRestrictedPreferredSize(JLabel label, int width) {
        View view = (View) label.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);
        if(view == null) {
            return label.getPreferredSize();
        }
        view.setSize(width, 0);
        float w = view.getPreferredSpan(View.X_AXIS);
        float h = view.getPreferredSpan(View.Y_AXIS);
        return new java.awt.Dimension((int) Math.ceil(w), (int) Math.ceil(h));
    }


    protected static Color getBrighterColor(Color c) {
        return new Color(Math.min(255,c.getRed()+192), Math.min(255,c.getGreen()+192), Math.min(255,c.getBlue()+192));
    }

}
