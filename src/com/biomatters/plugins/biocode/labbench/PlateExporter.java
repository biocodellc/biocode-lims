package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.plugin.DocumentFileExporter;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;

import java.io.File;
import java.io.IOException;
import java.util.List;

import jebl.util.ProgressListener;
import jxl.write.WritableWorkbook;
import jxl.write.WritableSheet;
import jxl.write.WriteException;
import jxl.Workbook;

import javax.swing.table.TableModel;
import javax.swing.event.TableModelListener;

/**
 * @author Steve
 * @version $Id: 20/05/2010 12:49:42 PM steve $
 */
public class PlateExporter extends DocumentFileExporter{


    public String getDefaultExtension() {
        return "xls";
    }

    public String getFileTypeDescription() {
        return "Plate backup (Excel)";
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument[] documentsToExport) {
        Options options = new Options(this.getClass());
        options.addBooleanOption("includeFIMS", "Include field information", false);
        return options;
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {new DocumentSelectionSignature(PlateDocument.class, 1, Integer.MAX_VALUE)};
    }

    @Override
    public void export(File file, PluginDocument[] documents, ProgressListener progressListener, Options options) throws IOException {
        boolean includeFims = (Boolean)options.getValue("includeFIMS");
        try {
            WritableWorkbook workbook = Workbook.createWorkbook(file);

            for (int i = 0; i < documents.length; i++) {
                TableModel tableModel = getTableModel((PlateDocument)documents[i], includeFims);
                WritableSheet sheet = workbook.createSheet(documents[i].getName(), i);
                ExcelUtilities.exportTable(sheet, tableModel, progressListener, options);
            }

            workbook.write();
            workbook.close();
        } catch (WriteException e) {
            throw new IOException(e.getMessage());
        }
    }

    private static TableModel getTableModel(PlateDocument plateDoc, boolean includeFims) {
        final Plate plate = plateDoc.getPlate();
        final Reaction firstReaction = plate.getReaction(0,0);
        @SuppressWarnings({"unchecked"}) final List<DocumentField> documentFields = includeFims ? firstReaction.getAllDisplayableFields() : firstReaction.getDisplayableFields();

        return new TableModel() {
            public int getRowCount() {
                return plate.getReactions().length;
            }

            public int getColumnCount() {
                return documentFields.size()+1;
            }

            public String getColumnName(int columnIndex) {
                if(columnIndex == 0) {
                    return "Location";
                }
                return documentFields.get(columnIndex-1).getName();
            }

            public Class<?> getColumnClass(int columnIndex) {
                return String.class;
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                Reaction r = plate.getReactions()[rowIndex];
                if(columnIndex == 0) {
                    return r.getLocationString();
                }
                return r.getFieldValue(documentFields.get(columnIndex-1).getCode());
            }

            public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            }

            public void addTableModelListener(TableModelListener l) {
            }

            public void removeTableModelListener(TableModelListener l) {
            }
        };

    }
}
