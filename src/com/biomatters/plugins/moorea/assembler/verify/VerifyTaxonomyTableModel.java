package com.biomatters.plugins.moorea.assembler.verify;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.implementations.Percentage;

import javax.swing.*;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Richard
 * @version $Id$
 */
public class VerifyTaxonomyTableModel implements TableModel {

    private final List<VerifyRow> rows = new ArrayList<VerifyRow>();

    public VerifyTaxonomyTableModel(VerifyTaxonomyResultsDocument results) {
        for (Map.Entry<AnnotatedPluginDocument, List<VerifyResult>> entry : results.getResults().entrySet()) {
            rows.add(new VerifyRow(entry));
        }
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

        public VerifyRow(Map.Entry<AnnotatedPluginDocument, List<VerifyResult>> row) {
            this.queryName = row.getKey().getName();
            Object fimsTaxonomy = row.getKey().getFieldValue(DocumentField.TAXONOMY_FIELD);
            AtomicReference<String> taxonomy = new AtomicReference<String>(fimsTaxonomy == null ? "" : fimsTaxonomy.toString());
            AtomicReference<String> blastTaxonomy = new AtomicReference<String>(row.getValue().get(0).document.getFieldValue(DocumentField.TAXONOMY_FIELD).toString());
            highlight(taxonomy, ";", blastTaxonomy);
            queryTaxon = taxonomy.get();
            hitTaxon = blastTaxonomy.get();
            AtomicReference<String> keys = new AtomicReference<String>("COI, cytochrome");
            AtomicReference<String> definition = new AtomicReference<String>(row.getValue().get(0).document.getFieldValue(DocumentField.DESCRIPTION_FIELD).toString());
            highlight(keys, ",", definition);
            keywords = keys.get();
            hitDefinition = definition.get();
            hitLength = (Integer)row.getKey().getFieldValue(DocumentField.SEQUENCE_LENGTH);
            hitIdentity = (Percentage)row.getKey().getFieldValue(DocumentField.ALIGNMENT_PERCENTAGE_IDENTICAL);
            assemblyBin = row.getKey().getFieldValue(DocumentField.BIN).toString();
        }
    }
}
