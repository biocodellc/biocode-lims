package com.biomatters.plugins.biocode.utilities;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.GeneiousAction;
import com.biomatters.geneious.publicapi.utilities.GeneralUtilities;
import com.biomatters.plugins.biocode.labbench.ExcelUtilities;
import jebl.util.ProgressListener;
import jxl.Workbook;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import java.awt.event.ActionEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Contains methods and classes for the exportation of JTables into various formats.
 *
 * Adapted from:
 * - com.biomatters.geneious.common.CSVTableExporter
 * - com.biomatters.geneious.publicapi.utilities.StringUtilities
 *
 * @author Gen Li
 */
public class TableExporter {
    private static final String COMMA   = ",";
    private static final String TAB     = "\t";

    private TableExporter() {
    }

    public static String escapeValue(String value) {
        value = value.replaceAll("\"", "\"\"");

        if (value.contains("\"") || value.contains(",") || value.contains("\n")) {
            value = "\"" + value + "\"";
        }

        return value;
    }

    private static void writeRow(BufferedWriter writer, List<String> values, String separator) throws IOException {
        boolean first = true;
        for (String value : values) {
            if (first) {
                first = false;
            } else {
                writer.write(separator);
            }
            writer.write(escapeValue(value));
        }
        writer.newLine();
    }

    /**
     * Repeatedly invokes {@link #writeRow(java.io.BufferedWriter, java.util.List, String)} to write the
     * entire table file with values separated by the supplied separator.
     * @param outFile file to write the table to.
     * @param table table whose data is to be written.
     */
    private static void writeTableToSeparatedValuesFile(File outFile, JTable table, String separator) throws IOException {
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(outFile));
            int columnCount = table.getColumnModel().getColumnCount();
            List<String> values = new LinkedList<String>();

            for (int column = 0; column < columnCount; column++) {
                // the sense fragments viewer needs this rather than table.getColumnName(column)
                values.add(table.getColumnModel().getColumn(column).getHeaderValue().toString());
            }

            writeRow(writer, values, separator);

            for (int row = 0; row < table.getRowCount(); row++) {
                values.clear();

                for (int column = 0; column < columnCount; column++) {
                    Object value = table.getValueAt(row, column);
                    if (value == null) {
                        values.add("");
                    } else {
                        values.add(stripHtmlTags(value.toString(), true));
                    }
                }

                writeRow(writer, values, separator);
            }
        } finally {
            GeneralUtilities.attemptClose(writer);
        }
    }

    /**
     * Repeatedly invokes {@link #writeRow(java.io.BufferedWriter, java.util.List, String)} to write the
     * entire table into a .csv file.
     * @param outFile .csv file to write the table to.
     * @param table table whose data is to be written
     */
    public static void writeTableToCSV(File outFile, JTable table) throws IOException {
        writeTableToSeparatedValuesFile(outFile, table, COMMA);
    }

    /**
     * Repeatedly invokes {@link #writeRow(java.io.BufferedWriter, java.util.List, String)} to write the
     * entire table into a .tsv file.
     * @param outFile .tsv file to write the table to.
     * @param table table whose data is to be written
     */
    public static void writeTableToTSV(File outFile, JTable table) throws IOException {
        writeTableToSeparatedValuesFile(outFile, table, TAB);
    }

    private static final Pattern htmlTagPattern = Pattern.compile("</?[a-zA-Z0-9][^>]*>");

    /**
     * Strips all html tags and their contents from the provided String
     * @param string the string to strip html tags from
     * @param onlyIfStartsWithHtmlTag if this is true, then only if the string starts with an html tag (&lt;html;&gt;) will any tags be stripped
     * @return the string without any html tags
     * @since API 4.800 (Geneious 8.0.0)
     */
    private static String stripHtmlTags(String string, boolean onlyIfStartsWithHtmlTag) {
        if ((string == null) || "".equals(string) || (onlyIfStartsWithHtmlTag && !string.regionMatches(true, 0, "<html>", 0, 6))) {
            return string;
        }
        string = htmlTagPattern.matcher(string).replaceAll("");
        string = string.replace("&gt;",">");
        string = string.replace("&lt;","<");
        return string;
    }

    public static abstract class ExportTableAction extends GeneiousAction {
        protected JTable tableToExport;
        public ExportTableAction(String actionName, JTable tableToExport) {
            super(actionName);
            this.tableToExport = tableToExport;
        }

        protected final File showSelectOutFileDialog(List<String> validFileExtensions, String description) {
            File outFile = null;
            JFileChooser exportFileChooser = new JFileChooser("", FileSystemView.getFileSystemView());

            exportFileChooser.setFileFilter(new FileNameExtensionFilter(description, validFileExtensions.toArray(new String[validFileExtensions.size()])));

            switch (exportFileChooser.showSaveDialog(tableToExport)) {
                case JFileChooser.APPROVE_OPTION:
                    outFile = exportFileChooser.getSelectedFile();
                    break;
                case JFileChooser.ERROR_OPTION:
                    Dialogs.showMessageDialog("Error occurred while selecting export file.");
                case JFileChooser.CANCEL_OPTION:
                default:
            }

            return outFile;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            try {
                export();
            } catch (DocumentOperationException e) {
                Dialogs.showMessageDialog("An error occurred while trying to export table " + tableToExport.getName() + ": " + e.getMessage(), "Could not export table " + tableToExport.getName(), tableToExport, Dialogs.DialogIcon.ERROR);
            }
        }

        protected abstract void export() throws DocumentOperationException;
    }

    public static class ExportTableToSpreadsheetAction extends ExportTableAction {
        public ExportTableToSpreadsheetAction(JTable tableToExport) {
            super("Export to Spreadsheet", tableToExport);
        }

        @Override
        protected void export() throws DocumentOperationException {
            File exportFile = showSelectOutFileDialog(Collections.singletonList(".xls"), "Excel Binary File Format (*.xls)");
            if (exportFile != null) {
                WritableWorkbook workbook = null;
                try {
                    workbook = Workbook.createWorkbook(exportFile);

                    ExcelUtilities.exportTable(workbook.createSheet(tableToExport.getName() + "Table to export", 0), tableToExport.getModel(), ProgressListener.EMPTY);

                    workbook.write();
                } catch (WriteException e) {
                    throw new DocumentOperationException(e.getMessage(), e);
                } catch (IOException e) {
                    throw new DocumentOperationException(e.getMessage(), e);
                } finally {
                    if (workbook != null) {
                        try {
                            workbook.close();
                        } catch (WriteException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public static class ExportTableToCSVAction extends ExportTableAction {
        public ExportTableToCSVAction(JTable tableToExport) {
            super("Export to CSV file", tableToExport);
        }

        @Override
        protected void export() throws DocumentOperationException {
            File exportFile = showSelectOutFileDialog(Collections.singletonList(".csv"), "Comma Separated Values (*.csv)");
            if (exportFile != null) {
                try {
                    TableExporter.writeTableToCSV(exportFile, tableToExport);
                } catch (IOException e) {
                    throw new DocumentOperationException(e.getMessage(), e);
                }
            }
        }
    }

    public static class ExportTableToTSVAction extends ExportTableAction {
        public ExportTableToTSVAction(JTable tableToExport) {
            super("Export to TSV file", tableToExport);
        }

        @Override
        protected void export() throws DocumentOperationException {
            File exportFile = showSelectOutFileDialog(Collections.singletonList(".tsv"), "Tab Separated Values (*.tsv)");
            if (exportFile != null) {
                try {
                    TableExporter.writeTableToTSV(exportFile, tableToExport);
                } catch (IOException e) {
                    throw new DocumentOperationException(e.getMessage(), e);
                }
            }
        }
    }
}