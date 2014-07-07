package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.geneious.publicapi.components.GTable;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.reaction.ReactionUtilities;
import com.biomatters.plugins.biocode.labbench.reaction.SplitPaneListSelector;
import com.biomatters.plugins.biocode.utilities.ObjectAndColor;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 7/07/2009 6:39:05 PM
 */
public abstract class TableDocumentViewerFactory extends DocumentViewerFactory{

    protected abstract TableModel getTableModel(AnnotatedPluginDocument[] docs, Options options);
    private static Preferences getPrefs() {
        return Preferences.userNodeForPackage(TableDocumentViewerFactory.class);
    }

    /**
     * Override this to make changes to the table before Geneious gets hold of it
     *
     * @param table ...
     * @param model ...
     */
    protected void messWithTheTable(JTable table, TableModel model) {

    }

    protected int getColumnWidth(TableModel model, int column) {
        return -1;
    }

    /**
     * override this to make some columns hidden by default...
     * @param columnIndex the index of the column
     * @param selectedDocuments  ...
     * @return true if the specified column should be visible by default
     */
    @SuppressWarnings({"UnusedDeclaration"})
    protected boolean columnVisibleByDefault(int columnIndex, AnnotatedPluginDocument[] selectedDocuments) {
        return true;
    }

    public Options getOptions() {
        return null;
    }

    /**
     * override this for an action provider
     *
     * @param table  ...
     * @param model ...
     * @return  ...
     */
    protected ActionProvider getActionProvider(JTable table, TableModel model) {
        return null;
    }

    public String getUniqueId() {
        return getClass().getName();
    }

    public String getPreferencesPrefix(AnnotatedPluginDocument[] selectedDocuments) {
        Map<String, Integer> classes = new TreeMap<String, Integer>();
        for(AnnotatedPluginDocument doc : selectedDocuments) {
            String docClassName = doc.getDocumentClass().getCanonicalName();
            Integer count = classes.get(docClassName);
            if(count == null) {
                count = 0;
            }
            count++;
            classes.put(docClassName, count);
        }
        StringBuilder builder = new StringBuilder(getUniqueId()+"|");
        for(Map.Entry<String, Integer> entry : classes.entrySet()) {
            builder.append(entry.getKey());
            if(entry.getValue() > 1) {
                builder.append("*");
            }
        }
        return builder.toString();
    }

    /**
     * we save the columns that are not at their default values (this provides the best result if the available columns change between views...
     * @param indices  x
     * @param model     x
     * @param selectedDocuments x
     * @return           x
     */
    private String columIndiciesToString(int[] indices, TableModel model, AnnotatedPluginDocument[] selectedDocuments) {
        ArrayList<String> names = new ArrayList<String>();

        for(int i=0; i < model.getColumnCount(); i++) {
            boolean on = contains(i, indices);
            if(on != columnVisibleByDefault(i, selectedDocuments)) {
                names.add(model.getColumnName(i));
            }
        }

        return StringUtilities.join("|", names);    
    }

    /**
     * we save the columns that are not at their default values (this provides the best result if the available columns change between views...
     * @param idString   x
     * @param model x
     * @param selectedDocuments  x
     * @return     x
     */
    private int[] stringToColumnIndices(String idString, TableModel model, AnnotatedPluginDocument[] selectedDocuments) {
        String[] names = idString.split("\\|");
        Arrays.sort(names);
        int[] indices = new int[model.getColumnCount()];
        int count = 0;

        for (int i = 0; i < model.getColumnCount(); i++) {
            boolean visibleByDefault = columnVisibleByDefault(i, selectedDocuments);
            String columnName = model.getColumnName(i);
            if (visibleByDefault) {
                if (Arrays.binarySearch(names, columnName) < 0) {
                    indices[count] = i;
                    count++;
                }
            }
            else {
                if (Arrays.binarySearch(names, columnName) >= 0) {
                    indices[count] = i;
                    count++;
                }
            }
        }
        return shrinkArray(indices, count);
    }

    private static int[] shrinkArray(int[] indices, int newSize) {
        if(newSize < indices.length) {
            int[] indices2 = new int[newSize];
            System.arraycopy(indices, 0, indices2, 0, newSize);
            indices = indices2;
        }
        return indices;
    }

    private int[] getDefaultIndices(TableModel model, AnnotatedPluginDocument[] selectedDocuments) {
        int[] indices = new int[model.getColumnCount()];
        int count = 0;
        for(int i=0; i < model.getColumnCount(); i++) {
            if(columnVisibleByDefault(i, selectedDocuments)) {
                indices[count] = i;
                count++;
            }
        }
        return shrinkArray(indices, count);
    }

    public List<JCheckBoxMenuItem> getSelectedColumnMenuItems(final JTable table, final ColumnHidingTableModel model, final String preferencesPrefix, final AnnotatedPluginDocument[] selectedDocuments) {
        List<JCheckBoxMenuItem> items = new ArrayList<JCheckBoxMenuItem>();
        for(int i =0; i < model.getInternalModel().getColumnCount(); i++) {
            final JCheckBoxMenuItem item = new JCheckBoxMenuItem(model.getInternalModel().getColumnName(i), contains(i, model.getVisibleColumns()));
            final int i1 = i;
            item.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e) {
                    model.setColumnVisible(i1, item.isSelected());
                    int[] visibleCols = model.getVisibleColumns();
                    getPrefs().put(preferencesPrefix, columIndiciesToString(visibleCols, model.getInternalModel(), selectedDocuments));
                    for(int i=0; i < visibleCols.length; i++) {
                        int preferredWidth = getColumnWidth(model.getInternalModel(), visibleCols[i]);
                        table.getColumnModel().getColumn(i).setPreferredWidth(preferredWidth);    
                    }

                    //dragged Column still points to old column which causes problem
                    table.getTableHeader().setDraggedColumn(null);
                }
            });
            items.add(item);
        }
        return items;
    }

    private static boolean contains(int num, int[] values) {
        for(int i : values) {
            if(i == num) {
                return true;
            }
        }
        return false;
    }

    public ColumnHidingTableModel getColumnHidingTableModel(AnnotatedPluginDocument[] annotatedDocuments, TableModel model) {
        String initialColumnState = getPrefs().get(getPreferencesPrefix(annotatedDocuments), columIndiciesToString(getDefaultIndices(model, annotatedDocuments), model, annotatedDocuments));
        int[] initialIndices = stringToColumnIndices(initialColumnState, model, annotatedDocuments);
        return new ColumnHidingTableModel(model, initialIndices);
    }

    public DocumentViewer createViewer(final AnnotatedPluginDocument[] annotatedDocuments) {
        final String preferencesPrefix = getPreferencesPrefix(annotatedDocuments);
        final Options options = getOptions();
        if(options != null) {
            options.restorePreferences(preferencesPrefix, true);
        }
        if(getTableModel(annotatedDocuments, options) == null) {
            return null;
        }
        return new DocumentViewer(){
            JTable table;

            GPanel panel = new GPanel(new BorderLayout());
            TableModel internalModel = getTableModel(annotatedDocuments, options);

            SimpleListener changeListener = new SimpleListener(){
                public void objectChanged() {
                    internalModel = getTableModel(annotatedDocuments, options);
                    if (internalModel == null) {
                        return;
                    }
                    JComponent component = createComponent(internalModel);
                    panel.removeAll();
                    JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                    topPanel.add(options.getPanel());
                    panel.add(topPanel, BorderLayout.NORTH);
                    panel.add(component, BorderLayout.CENTER);
                    options.savePreferences(preferencesPrefix);

                }
            };

            @Override
            public JComponent getComponent() {
                if(options != null) {
                    changeListener.objectChanged();
                    options.addChangeListener(changeListener);
                    return panel;
                }
                return createComponent(internalModel);
            }

            public JComponent createComponent(TableModel internalModel) {
                final ColumnHidingTableModel model = getColumnHidingTableModel(annotatedDocuments, internalModel);

                TableSorter sorter = new TableSorter(model);
                final AtomicReference<JScrollPane> scroller = new AtomicReference<JScrollPane>();
                table = new GTable(sorter){
                    @Override
                    public Dimension getPreferredSize() {
                        Dimension size = super.getPreferredSize();
                        return new Dimension(Math.max(scroller.get().getViewportBorderBounds().width, size.width), size.height);
                    }

                };
                sorter.setTableHeader(table.getTableHeader());

                table.getTableHeader().addMouseListener(new MouseAdapter(){
                    @Override
                    public void mousePressed(MouseEvent e) {
                        handleMouse(e);
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        handleMouse(e);
                    }

                    public void handleMouse(MouseEvent e) {
                        if(e.isPopupTrigger()){
                            JPopupMenu menu = new JPopupMenu("Columns");
                            JMenuItem manageColumnsItem = new JMenuItem("Manage Columns...");
                            manageColumnsItem.addActionListener(new ActionListener(){
                                public void actionPerformed(ActionEvent e) {
                                    Vector<DocumentField> availableFields = new Vector<DocumentField>();
                                    for(int i=0; i < model.getInternalModel().getColumnCount(); i++) {
                                        String colName = model.getInternalModel().getColumnName(i);
                                        availableFields.add(new DocumentField(colName, colName, colName, String.class, false, false));
                                    }


                                    final SplitPaneListSelector<DocumentField> fieldChooser = new SplitPaneListSelector<DocumentField>(availableFields, model.getVisibleColumns(), ReactionUtilities.DOCUMENT_FIELD_CELL_RENDERER, false);
                                    fieldChooser.setPreferredSize(new Dimension(400, 500));
                                    if (Dialogs.showOkCancelDialog(fieldChooser, "Manage Columns", table, Dialogs.DialogIcon.NO_ICON)) {
                                        Vector<DocumentField> newSelectedFields = fieldChooser.getSelectedFields();
                                        int[] selectedFields = new int[newSelectedFields.size()];
                                        int index = 0;
                                        for (DocumentField availableField : availableFields) {
                                            boolean visible = newSelectedFields.contains(availableField);
                                            if(visible) {
                                                int indexOf = availableFields.indexOf(availableField);
                                                if(indexOf >= 0) {
                                                    selectedFields[index] = indexOf;
                                                    index++;
                                                }
                                            }
                                            model.setVisibleColumns(selectedFields);
                                            getPrefs().put(preferencesPrefix, columIndiciesToString(selectedFields, model.getInternalModel(), annotatedDocuments));
                                        }
                                    }
                                }
                            });
                            menu.add(manageColumnsItem);
                            menu.addSeparator();
                            List<JCheckBoxMenuItem> selectedColumnMenuItems = getSelectedColumnMenuItems(table, model, preferencesPrefix, annotatedDocuments);
                            for(JCheckBoxMenuItem item : selectedColumnMenuItems) {
                                menu.add(item);
                            }
                            menu.setLocation(e.getPoint());
                            menu.show(table.getTableHeader(), e.getX(), e.getY());
                            e.consume();
                        }
                    }
                });
                scroller.set(new JScrollPane(table));

                table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
                table.setGridColor(Color.lightGray);
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
                messWithTheTable(table, internalModel);
                return scroller.get();
            }

            @Override
            public ExtendedPrintable getExtendedPrintable() {
                return new JTablePrintable(table);
            }

            @Override
            public ActionProvider getActionProvider() {
                return TableDocumentViewerFactory.this.getActionProvider(table, internalModel);
            }
        };
    }

    /**
     * Returns the preferred size to set a component at in order to render an html string. You can
     * specify the size of the width.
     * @param label .
     * @param width .
     * @return the width
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

    private static class ColumnHidingTableModel implements TableModel{
        private TableModel internalModel;
        private int[] visibleColumns;
        private java.util.List<TableModelListener> tableModelListeners;

        public ColumnHidingTableModel(TableModel internalModel, int[] visibleColumns) {
            this.internalModel = internalModel;
            this.visibleColumns = visibleColumns;
            tableModelListeners = new ArrayList<TableModelListener>();
            internalModel.addTableModelListener(new TableModelListener(){
                public void tableChanged(TableModelEvent e) {
                    for(TableModelListener listener : tableModelListeners) {
                        int column;
                        if(e.getColumn() < ColumnHidingTableModel.this.visibleColumns.length && e.getColumn() >= 0) {
                            column = ColumnHidingTableModel.this.visibleColumns[e.getColumn()];
                        }
                        else {
                            column = e.getColumn();
                        }
                        listener.tableChanged(new TableModelEvent(ColumnHidingTableModel.this, e.getFirstRow(), e.getLastRow(), column));
                    }
                }
            });
        }

        public TableModel getInternalModel() {
            return internalModel;
        }

        public void setInternalModel(TableModel internalModel) {
            this.internalModel = internalModel;
        }

        public int[] getVisibleColumns() {
            return visibleColumns;
        }

        public void setVisibleColumns(int[] visibleColumns) {
            this.visibleColumns = visibleColumns;
            for(TableModelListener listener : tableModelListeners) {
                listener.tableChanged(new TableModelEvent(this, TableModelEvent.HEADER_ROW));
            }
        }

        public int getRowCount() {
            return internalModel.getRowCount();
        }

        public int getColumnCount() {
            return visibleColumns.length;
        }

        public String getColumnName(int columnIndex) {
            return internalModel.getColumnName(visibleColumns[columnIndex]);
        }

        public Class<?> getColumnClass(int columnIndex) {
            return internalModel.getColumnClass(visibleColumns[columnIndex]);
        }

        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return internalModel.isCellEditable(rowIndex, visibleColumns[columnIndex]);
        }

        public Object getValueAt(int rowIndex, int columnIndex) {
            return internalModel.getValueAt(rowIndex, visibleColumns[columnIndex]);
        }

        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            internalModel.setValueAt(aValue, rowIndex, visibleColumns[columnIndex]);
        }

        public void addTableModelListener(TableModelListener l) {
            tableModelListeners.add(l);
        }

        public void removeTableModelListener(TableModelListener l) {
            tableModelListeners.remove(l);
        }

        public void setColumnVisible(int col, boolean selected) {
            int[] newVisibleColumns;
            boolean alreadySelected = contains(col, visibleColumns);
            if(selected == alreadySelected) {
                return;
            }
            if(alreadySelected){ //remove the column from the array...
                newVisibleColumns = new int[visibleColumns.length-1];
                int count = 0;
                for (int visibleColumn : visibleColumns) {
                    if (visibleColumn != col) {
                        newVisibleColumns[count] = visibleColumn;
                        count++;
                    }
                }
            }
            else { //add the colum to the array in the correct position
                newVisibleColumns = new int[visibleColumns.length+1];
                System.arraycopy(visibleColumns, 0, newVisibleColumns, 0, visibleColumns.length);
                newVisibleColumns[visibleColumns.length] = col;
                Arrays.sort(newVisibleColumns);
            }
            setVisibleColumns(newVisibleColumns);
        }

        public boolean isColumnVisible(int col) {
            for (int visibleColumn : visibleColumns) {
                if (visibleColumn == col) {
                    return true;
                }
            }
            return false;
        }
    }

}
