package com.biomatters.plugins.moorea.assembler.verify;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.URN;
import com.biomatters.geneious.publicapi.implementations.Percentage;
import com.biomatters.geneious.publicapi.plugin.ActionProvider;
import com.biomatters.geneious.publicapi.plugin.GeneiousAction;
import com.biomatters.geneious.publicapi.plugin.Icons;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

/**
 * @author Richard
 * @version $Id$
 */
public class VerifyTaxonomyTableModel implements TableModel {

    private final List<VerifyRow> rows = new ArrayList<VerifyRow>();
    private int selectedRow = -1;
    private JComponent dialogParent = null;

    public VerifyTaxonomyTableModel(VerifyTaxonomyResultsDocument results) {
        for (VerifyResult entry : results.getResults()) {
            rows.add(new VerifyRow(entry));
        }
        saveAction.setEnabled(false);
        goToAssemblyAction.setEnabled(false);
        showOtherHitsAction.setEnabled(false);
    }

    /**
     *
     * @param i selected row or -1 for none
     */
    public void setSelectedRow(int i) {
        selectedRow = i;
        goToAssemblyAction.setEnabled(i != -1);
        showOtherHitsAction.setEnabled(i != -1 && rows.get(selectedRow).result.hitDocuments.size() > 1);
    }

    public int getRowCount() {
        return rows.size();
    }

    public int getColumnCount() {
        return COLUMNS.length;
    }

    public String getColumnName(int columnIndex) {
        return COLUMNS[columnIndex].name;
    }

    public Class<?> getColumnClass(int columnIndex) {
        return COLUMNS[columnIndex].columnClass;
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
        return COLUMNS[columnIndex].getValue(rows.get(rowIndex));
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        throw new UnsupportedOperationException("Edit this value, you shall not!");
    }

    public void addTableModelListener(TableModelListener l) {
    }

    public void removeTableModelListener(TableModelListener l) {
    }

    public void setColumnWidths(TableColumnModel columnModel) {
        for (int i = 0; i < COLUMNS.length; i++) {
            VerifyColumn column = COLUMNS[i];
            columnModel.getColumn(i).setPreferredWidth(column.getPreferredWidth());
            if (column.fixedWidth) {
                columnModel.getColumn(i).setMinWidth(column.getPreferredWidth());
            }
            if (column.fixedWidth) {
                columnModel.getColumn(i).setMaxWidth(column.getPreferredWidth());
            }
        }
    }

    private final GeneiousAction goToAssemblyAction = new GeneiousAction("Go To Query",
            "Select the query document for the selected result", IconUtilities.getIcons("sequenceSearch16.png")) {
        public void actionPerformed(ActionEvent e) {
            URN urn = rows.get(selectedRow).result.queryDocument.getURN();
            DocumentUtilities.selectDocument(urn);
        }
    };

    private static final Preferences PREFS = Preferences.userNodeForPackage(VerifyTaxonomyTableModel.class);

    private final GeneiousAction showOtherHitsAction = new GeneiousAction("Show Other Hits",
            "Show the other hits that were downloaded for this query (SuperTip: Double click in table for quick access)", IconUtilities.getIcons("nucleotideList16.png")) {
        public void actionPerformed(ActionEvent e) {
            List<VerifyResult> dummyResults = new ArrayList<VerifyResult>();
            for (AnnotatedPluginDocument hitDocument : rows.get(selectedRow).result.hitDocuments) {
                dummyResults.add(new VerifyResult(Collections.singletonList(hitDocument), rows.get(selectedRow).result.queryDocument));
            }
            JComponent tableComponent = new VerifyTaxonomyDocumentViewerFactory().createViewer(new AnnotatedPluginDocument[]{
                    DocumentUtilities.createAnnotatedPluginDocument(new VerifyTaxonomyResultsDocument(dummyResults, null))
            }).getComponent();
            Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(new String[] {"Close"}, "Other Hits", dialogParent, Dialogs.DialogIcon.NO_ICON);
            int width = PREFS.getInt("popupWidth4", 1024);
            int height = PREFS.getInt("popupHeight4", 500);
            tableComponent.setPreferredSize(new Dimension(width, height));
            dialogOptions.setMaxWidth(width + 50);
            dialogOptions.setMaxHeight(height + 100);
            tableComponent.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    PREFS.putInt("popupWidth4", e.getComponent().getWidth());
                    PREFS.putInt("popupHeight4", e.getComponent().getHeight());
                }
            });
            Dialogs.showDialog(dialogOptions, tableComponent);
        }
    };

    private final GeneiousAction binningParametersAction = new GeneiousAction("Binning Parameters",
            "Set the binning parameters for this taxonomy verification",
            new Icons(new ImageIcon(VerifyTaxonomyTableModel.class.getResource("happy.gif")))) {
        public void actionPerformed(ActionEvent e) {
            Dialogs.showMessageDialog("Coming soon");
        }
    };

    private final GeneiousAction saveAction = new GeneiousAction("Save",
            "Save the current binning parameters", IconUtilities.getIcons("save16.png")) {
        public void actionPerformed(ActionEvent e) {
            Dialogs.showMessageDialog("Coming soon");
        }
    };

    public ActionProvider getActionProvider() {
        return new ActionProvider() {
            @Override
            public List<GeneiousAction> getOtherActions() {
                return Arrays.asList(goToAssemblyAction, showOtherHitsAction, new GeneiousAction.Divider(), binningParametersAction);
            }

            @Override
            public GeneiousAction getSaveAction() {
                return saveAction;
            }
        };
    }

    public void setTable(final JTable table) {
        setColumnWidths(table.getColumnModel());
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                setSelectedRow(table.getSelectedRow());
            }
        });
        dialogParent = table;
    }

    public void doDoubleClick() {
        if (showOtherHitsAction.isEnabled()) {
            showOtherHitsAction.actionPerformed(null);
        }
    }

    private abstract static class VerifyColumn {

        final String name;
        final Class columnClass;
        final boolean fixedWidth;

        protected VerifyColumn(String name, Class columnClass) {
            this(name, columnClass, false);
        }

        VerifyColumn(String name, Class columnClass, boolean fixedWidth) {
            this.name = name;
            this.columnClass = columnClass;
            this.fixedWidth = fixedWidth;
        }

        int getPreferredWidth() {
            return new JLabel(name).getPreferredSize().width;
        }

        abstract Object getValue(VerifyRow row);
    }

    private static final VerifyColumn[] COLUMNS = new VerifyColumn[] {
            new VerifyColumn("Query", String.class) {
                @Override
                Object getValue(VerifyRow row) {
                    return row.queryName;
                }

                @Override
                int getPreferredWidth() {
                    return 30;
                }
            },
            new VerifyColumn("Query Taxon", String.class) {
                @Override
                Object getValue(VerifyRow row) {
                    return row.queryTaxon;
                }

                @Override
                int getPreferredWidth() {
                    return 70;
                }
            },
            new VerifyColumn("Hit Taxon", String.class) {
                @Override
                Object getValue(VerifyRow row) {
                    return row.hitTaxon;
                }

                @Override
                int getPreferredWidth() {
                    return 180;
                }
            },
            new VerifyColumn("Keywords", String.class) {
                @Override
                Object getValue(VerifyRow row) {
                    return row.keywords;
                }

                @Override
                int getPreferredWidth() {
                    return 50;
                }
            },
            new VerifyColumn("Hit Definition", String.class) {
                @Override
                Object getValue(VerifyRow row) {
                    return row.hitDefinition;
                }

                @Override
                int getPreferredWidth() {
                    return 120;
                }
            },
            new VerifyColumn("Hit Length", Integer.class, true) {
                @Override
                Object getValue(VerifyRow row) {
                    return row.hitLength;
                }
            },
            new VerifyColumn("Hit Identity", Percentage.class, true) {
                @Override
                Object getValue(VerifyRow row) {
                    return row.hitIdentity;
                }
            },
            new VerifyColumn("Assembly Bin", String.class, true) {
                @Override
                Object getValue(VerifyRow row) {
                    return row.assemblyBin;
                }
            }
    };

    /**
     *
     * @param keywords keywords separated by delimiter, used to return value too
     * @param delimiter
     * @param s string to check for keywords, used to return value too
     * @return true iff all keywords were found in s, false otherwise
     */
    private static boolean highlight(AtomicReference<String> keywords, String delimiter, AtomicReference<String> s) {
        boolean foundAll = true;
        String[] keys = keywords.get().split(delimiter);
        String keys2 = keywords.get();
        String s2 = s.get();
        for (String key : keys) {
            key = key.trim();
            if (!s2.contains(key)) {
                keys2 = keys2.replace(key, "<font color=\"red\">" + key + "</font>");
                foundAll = false;
            } else {
                keys2 = keys2.replace(key, "<font color=\"green\">" + key + "</font>");
                s2 = s2.replace(key, "<font color=\"green\">" + key + "</font>");
            }
        }
        keywords.set(keys2);
        s.set(s2);
        return foundAll;
    }

    private class VerifyRow {

        final String queryName;
        final String queryTaxon;
        final String hitTaxon;
        final String keywords;
        final String hitDefinition;
        final int hitLength;
        final Percentage hitIdentity;
        final String assemblyBin;
        final VerifyResult result;

        public VerifyRow(VerifyResult result) {
            this.result = result;
            this.queryName = result.queryDocument.getName();
            Object fimsTaxonomy = result.queryDocument.getFieldValue(DocumentField.TAXONOMY_FIELD);
            AtomicReference<String> taxonomy = new AtomicReference<String>(fimsTaxonomy == null ? "" : fimsTaxonomy.toString());
            Object taxObject = result.hitDocuments.get(0).getFieldValue(DocumentField.TAXONOMY_FIELD);
            AtomicReference<String> blastTaxonomy = new AtomicReference<String>(taxObject == null ? "" : taxObject.toString());
            highlight(taxonomy, ";", blastTaxonomy);
            queryTaxon = taxonomy.get();
            hitTaxon = blastTaxonomy.get();
            AtomicReference<String> keys = new AtomicReference<String>("COI, cytochrome");
            AtomicReference<String> definition = new AtomicReference<String>(result.hitDocuments.get(0).getFieldValue(DocumentField.DESCRIPTION_FIELD).toString());
            highlight(keys, ",", definition);
            keywords = keys.get();
            hitDefinition = definition.get();
            hitLength = (Integer)result.hitDocuments.get(0).getFieldValue(DocumentField.SEQUENCE_LENGTH);
            hitIdentity = (Percentage)result.hitDocuments.get(0).getFieldValue(DocumentField.ALIGNMENT_PERCENTAGE_IDENTICAL);
            assemblyBin = result.queryDocument.getFieldValue(DocumentField.BIN).toString();
        }
    }
}
