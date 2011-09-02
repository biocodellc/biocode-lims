package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentFileExporter;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.CherryPickingDocumentViewerFactory;
import jebl.util.ProgressListener;
import jxl.Workbook;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;

import javax.swing.table.TableModel;
import java.io.File;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Steve
 * Date: 23/04/11
 * Time: 2:55 PM
 * To change this template use File | Settings | File Templates.
 */
public class CherryPickingTableExporter extends DocumentFileExporter {

    public String getDefaultExtension() {
        return "xls";
    }

    public String getFileTypeDescription() {
        return "Cherry Picking table (Excel)";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {new DocumentSelectionSignature(CherryPickingDocument.class, 1, 1)};
    }

    @Override
    public void export(File file, AnnotatedPluginDocument[] documents, ProgressListener progressListener, Options options) throws IOException {
        try {
            WritableWorkbook workbook = Workbook.createWorkbook(file);

            int count = 0;
            TableDocumentViewerFactory factory = new CherryPickingDocumentViewerFactory();
            TableModel tableModel = factory.getTableModel(documents);
            if (tableModel != null) {
                TableModel hidingModel = factory.getColumnHidingTableModel(documents, tableModel);
                WritableSheet sheet = workbook.createSheet(factory.getName(), count);
                ExcelUtilities.exportTable(sheet, hidingModel, progressListener);
                count++;
            }


            workbook.write();
            workbook.close();
        } catch (WriteException e) {
            throw new IOException(e.getMessage());
        }
    }
}
