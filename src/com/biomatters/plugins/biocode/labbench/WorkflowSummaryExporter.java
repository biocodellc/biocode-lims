package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.DocumentFileExporter;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;

import java.io.File;
import java.io.IOException;

import jebl.util.ProgressListener;

import javax.swing.table.TableModel;

import jxl.write.*;
import jxl.Workbook;

/**
 * @author Steve
 * @version $Id: 19/05/2010 8:38:02 PM steve $
 */
public class WorkflowSummaryExporter extends DocumentFileExporter{

    private static TableDocumentViewerFactory[] factoriesToExport = new TableDocumentViewerFactory[] {
        new MultiWorkflowDocumentViewerFactory(),
        new MultiPrimerDocumentViewerFactory(Reaction.Type.PCR),
        new MultiPrimerDocumentViewerFactory(Reaction.Type.CycleSequencing)
    };

    public String getDefaultExtension() {
        return "xls";
    }

    public String getFileTypeDescription() {
        return "Primer Summary (Excel)";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {new DocumentSelectionSignature(new DocumentSelectionSignature.DocumentSelectionSignatureAtom[] {new DocumentSelectionSignature.DocumentSelectionSignatureAtom(WorkflowDocument.class, 1, Integer.MAX_VALUE), new DocumentSelectionSignature.DocumentSelectionSignatureAtom(PlateDocument.class, 0, Integer.MAX_VALUE)}),
        new DocumentSelectionSignature(new DocumentSelectionSignature.DocumentSelectionSignatureAtom[] {new DocumentSelectionSignature.DocumentSelectionSignatureAtom(WorkflowDocument.class, 0, Integer.MAX_VALUE), new DocumentSelectionSignature.DocumentSelectionSignatureAtom(PlateDocument.class, 1, Integer.MAX_VALUE)})};
    }

    @Override
    public void export(File file, AnnotatedPluginDocument[] documents, ProgressListener progressListener, Options options) throws IOException {
        WritableWorkbook workbook = null;
        try {
            workbook = Workbook.createWorkbook(file);

            int count = 0;
            for (TableDocumentViewerFactory factory : factoriesToExport) {
                TableModel tableModel = factory.getTableModel(documents);
                if (tableModel != null) {
                    TableModel hidingModel = factory.getColumnHidingTableModel(documents, tableModel);
                    WritableSheet sheet = workbook.createSheet(factory.getName(), count);
                    ExcelUtilities.exportTable(sheet, hidingModel, progressListener);
                    count++;
                }
            }

            if(count > 0) {
                workbook.write();
            }
            else {
                throw new IOException("Geneious could not find any tables to export for your selected documents");
            }
        } catch (WriteException e) {
            throw new IOException(e.getMessage());
        } finally {
            if(workbook != null) {
                try {
                    workbook.close();
                } catch (WriteException e) {}
            }
        }
    }

}
