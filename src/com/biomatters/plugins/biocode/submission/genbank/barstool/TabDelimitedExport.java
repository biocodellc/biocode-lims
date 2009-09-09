package com.biomatters.plugins.biocode.submission.genbank.barstool;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

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

    static void export(File file, ExportTableModel model, ProgressListener progressListener, boolean isAutomated) throws IOException, DocumentOperationException {
        StringBuilder s = new StringBuilder();
        CompositeProgressListener progress = new CompositeProgressListener(progressListener, model.getRowCount() + 1);
        boolean[] promptedAboutMissingValues = new boolean[model.getColumnCount()];
        for (int y = -1; y < model.getRowCount(); y ++) {
            progress.beginSubtask();
            for (int x = 0; x < model.getColumnCount(); x ++) {
                if (x != 0) {
                    s.append("\t");
                }
                if (y == -1) {
                    s.append(model.getColumnName(x));
                } else {
                    Object value = model.getValueAt(y, x);
                    if (value == null) {
                        if (!promptedAboutMissingValues[x] && !isAutomated) {
                            promptedAboutMissingValues[x] = true;
                            String continueButton = "Continue";
                            Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(new String[] {continueButton, "Cancel"}, "Missing Value", null, Dialogs.DialogIcon.WARNING);
                            String message = "<html><b>" + model.getColumnName(x) + " is missing for one or more of the selected documents.</b><br><br>" +
                                    "If you choose to continue, the value will be left empty in the submission files.</html>";
                            Object choice = Dialogs.showDialog(dialogOptions, message);
                            if (!choice.equals(continueButton)) {
                                throw new DocumentOperationException.Canceled();
                            }
                        }
                        continue;
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
