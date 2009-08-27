package com.biomatters.plugins.moorea.submission.genbank.barstool;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class TabDelimitedExport {

    static void export(File file, ExportTableModel model) throws IOException {
        StringBuilder s = new StringBuilder();
        for (int y = -1; y < model.getRowCount(); y ++) {
            for (int x = 0; x < model.getColumnCount(); x ++) {
                if (x != 0) {
                    s.append("\t");
                }
                if (y == -1) {
                    s.append(model.getColumnName(x));
                } else {
                    Object value = model.getValueAt(y, x);
                    if (value != null) {
                        s.append(value);
                    }
                }
            }
            if (y != model.getRowCount() - 1) {
                s.append("\n");
            }
        }
        FileUtilities.writeTextToFile(file, s.toString());
    }

    abstract static class ExportTableModel {

        final List<AnnotatedPluginDocument> docs;

        protected ExportTableModel(List<AnnotatedPluginDocument> docs) {
            this.docs = docs;
        }

        public int getRowCount() {
            return docs.size();
        }

        Object getValueAt(int rowIndex, int columnIndex) {
            return getValue(docs.get(rowIndex), columnIndex);
        }

        abstract int getColumnCount();

        abstract String getColumnName(int columnIndex);

        abstract Object getValue(AnnotatedPluginDocument doc, int columnIndex);
    }
}
