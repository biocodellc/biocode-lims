package com.biomatters.plugins.moorea.submission.genbank.barstool;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class TabDelimitedExport {

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("dd-MMM-yyyy");

    static void export(File file, ExportTableModel model) throws IOException, DocumentOperationException {
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
                    if (value == null) {
                        //todo prompt to continue anyway
                        throw new DocumentOperationException(model.getColumnName(x) + " is missing for one or more of the selected documents");
                    }
                    if (value instanceof Date) {
                        synchronized (DATE_FORMAT) {
                            value = DATE_FORMAT.format((Date)value);
                        }
                    }
                    //todo author format?
                    s.append(value);
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
