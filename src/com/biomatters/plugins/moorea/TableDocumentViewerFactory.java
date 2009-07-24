package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.plugin.DocumentViewerFactory;
import com.biomatters.geneious.publicapi.plugin.DocumentViewer;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.components.GTable;

import javax.swing.table.TableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.*;
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

    public DocumentViewer createViewer(final AnnotatedPluginDocument[] annotatedDocuments) {
        return new DocumentViewer(){
            public JComponent getComponent() {
                TableModel model = getTableModel(annotatedDocuments);
                TableSorter sorter = new TableSorter(model);
                final AtomicReference<JScrollPane> scroller = new AtomicReference<JScrollPane>();
                JTable table = new GTable(sorter){
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
                            color = value.getColor().equals(Color.white) ? Color.black : value.getColor();
                        }
                        comp.setForeground(color);

                        return comp;
                    }
                });
                return scroller.get();
            }
        };
    }

    protected static Color getBrighterColor(Color c) {
        return new Color(Math.min(255,c.getRed()+192), Math.min(255,c.getGreen()+192), Math.min(255,c.getBlue()+192));
    }

    protected static class ObjectAndColor{
        private Object object;
        private Color color;

        protected ObjectAndColor(Object object, Color color) {
            this.object = object;
            this.color = color;
        }

        public Object getObject() {
            return object;
        }

        public Color getColor() {
            return color;
        }
    }
}
