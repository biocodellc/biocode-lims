package com.biomatters.plugins.biocode.utilities;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
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
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Contains methods and classes for exporting JTables in various formats.
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

    public static String escapeValue(String value, String separator) {
        value = value.replaceAll("\"", "\"\"");

        if (value.contains("\"") || value.contains(separator) || value.contains("\n")) {
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
            writer.write(escapeValue(value, separator));
        }
        writer.newLine();
    }

    /**
     * Repeatedly invokes {@link #writeRow(java.io.BufferedWriter, java.util.List, String)} to write the
     * entire table file with values separated by the supplied separator.
     * @param outFile file to write the table to.
     * @param table table whose data is to be written.
     */
    private static void writeTableToSeparatedValuesFile(File outFile, JTable table, String separator, ProgressListener progressListener) throws IOException {
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(outFile));
            int rowCount = table.getRowCount(), columnCount = table.getColumnModel().getColumnCount();
            List<String> values = new LinkedList<String>();


            for (int column = 0; column < columnCount; column++) {
                // the sense fragments viewer needs this rather than table.getColumnName(column)
                values.add(table.getColumnModel().getColumn(column).getHeaderValue().toString());
            }

            writeRow(writer, values, separator);

            for (int row = 0; row < rowCount; row++) {
                if (progressListener.isCanceled()) {
                    return;
                }

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
                progressListener.setProgress(row + 1, rowCount);
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
     * @param progressListener
     */
    public static void writeTableToCSV(File outFile, JTable table, ProgressListener progressListener) throws IOException {
        writeTableToSeparatedValuesFile(outFile, table, COMMA, progressListener);
    }

    /**
     * Repeatedly invokes {@link #writeRow(java.io.BufferedWriter, java.util.List, String)} to write the
     * entire table into a .tsv file.
     * @param outFile .tsv file to write the table to.
     * @param table table whose data is to be written
     * @param progressListener
     */
    public static void writeTableToTSV(File outFile, JTable table, ProgressListener progressListener) throws IOException {
        writeTableToSeparatedValuesFile(outFile, table, TAB, progressListener);
    }

    private static final Pattern htmlTagPattern = Pattern.compile("</?[a-zA-Z0-9][^>]*>");

    /**
     * Strips all html tags and their contents from the provided String
     * @param string the string to strip html tags from
     * @param onlyIfStartsWithHtmlTag if this is true, then only if the string starts with an html tag (&lt;html;&gt;) will any tags be stripped
     * @return the string without any html tags
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
        private String exportFileExtension;
        private String exportFileExtensionDescription;

        public ExportTableAction(String actionName, JTable tableToExport, String exportFileExtension, String exportFileExtensionDescription) {
            super(actionName);
            this.tableToExport = tableToExport;
            this.exportFileExtension = exportFileExtension;
            this.exportFileExtensionDescription = exportFileExtensionDescription;
        }

        private File showSelectOutFileDialog() {
            File outFile = null;
            JFileChooser exportFileChooser = new JFileChooser("", FileSystemView.getFileSystemView());

            exportFileChooser.setFileFilter(new FileNameExtensionFilter(exportFileExtensionDescription, exportFileExtension));

            switch (exportFileChooser.showSaveDialog(tableToExport)) {
                case JFileChooser.ERROR_OPTION:
                    Dialogs.showMessageDialog("An error occurred while selecting export file.");
                case JFileChooser.APPROVE_OPTION:
                    outFile = processExportFile(exportFileChooser.getSelectedFile());
                case JFileChooser.CANCEL_OPTION:
                default:
            }

            return outFile;
        }

        private File processExportFile(File exportFile) {
            if (!exportFile.getAbsolutePath().endsWith(exportFileExtension)) {
                exportFile = new File(exportFile.getAbsolutePath() + exportFileExtension);
            }

            if (exportFile.exists()) {
                if (exportFile.isDirectory()) {
                    Dialogs.showMessageDialog(
                            "A directory named " + exportFile.getName() + " exists in the output directory.",
                            "Cannot Export Table",
                            tableToExport,
                            Dialogs.DialogIcon.WARNING
                    );

                    exportFile = null;
                }
                else if (!Dialogs.showYesNoDialog(
                        "A file named " + exportFile.getName() + " already exists in the output directory. " +
                                "Do you wish to overwrite the existing file?",
                        "File With Same Name Exists",
                        tableToExport,
                        Dialogs.DialogIcon.WARNING)) {
                    exportFile = null;
                }
            }

            return exportFile;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            final File exportFile = showSelectOutFileDialog();
            if (exportFile != null) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            export(exportFile);
                        } catch (DocumentOperationException e) {
                            Dialogs.showMessageDialog(
                                    "An error occurred while trying to export table " + tableToExport.getName() + ": " + e.getMessage(),
                                    "Could not export table " + tableToExport.getName(),
                                    tableToExport,
                                    Dialogs.DialogIcon.ERROR
                            );
                        }
                    }
                }).start();
            }
        }

        protected abstract void export(File exportFile) throws DocumentOperationException;
    }

    public static class ExportTableToSpreadsheetAction extends ExportTableAction {
        public ExportTableToSpreadsheetAction(JTable tableToExport) {
            super("Export to Spreadsheet", tableToExport, ".xls", "Excel Binary File Format (*.xls)");
        }

        @Override
        protected void export(File exportFile) throws DocumentOperationException {
            WritableWorkbook workbook = null;
            try {
                workbook = Workbook.createWorkbook(exportFile);

                ProgressFrame progressFrame = new ProgressFrame("Exporting Table To Excel", "", 0, true);

                ExcelUtilities.exportTable(
                        workbook.createSheet(tableToExport.getName() + "Table to export", 0),
                        tableToExport.getModel(),
                        progressFrame
                );

                if (progressFrame.isCanceled()) {
                    return;
                }

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

    public static class ExportTableToCSVAction extends ExportTableAction {
        public ExportTableToCSVAction(JTable tableToExport) {
            super("Export to CSV file", tableToExport, ".csv", "Comma Separated Values (*.csv)");
        }

        @Override
        protected void export(File exportFile) throws DocumentOperationException {
            try {
                TableExporter.writeTableToCSV(
                        exportFile,
                        tableToExport,
                        new ProgressFrame("Exporting Table To CSV", "", 0, true)
                );
            } catch (IOException e) {
                throw new DocumentOperationException(e.getMessage(), e);
            }
        }
    }

    public static class ExportTableToTSVAction extends ExportTableAction {
        public ExportTableToTSVAction(JTable tableToExport) {
            super("Export to TSV file", tableToExport, ".tsv", "Tab Separated Values (*.tsv)");
        }

        @Override
        protected void export(File exportFile) throws DocumentOperationException {
            try {
                TableExporter.writeTableToTSV(
                        exportFile,
                        tableToExport,
                        new ProgressFrame("Exporting Table To TSV", "", 0, true)
                );
            } catch (IOException e) {
                throw new DocumentOperationException(e.getMessage(), e);
            }
        }
    }
}