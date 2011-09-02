package com.biomatters.plugins.biocode.assembler.verify;

import com.biomatters.geneious.publicapi.plugin.DocumentFileExporter;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.plugins.biocode.labbench.ExcelUtilities;

import java.io.File;
import java.io.IOException;

import jebl.util.ProgressListener;
import jxl.write.WritableWorkbook;
import jxl.write.WritableSheet;
import jxl.write.WriteException;
import jxl.Workbook;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 5/07/2010 1:55:24 PM
 */

public class VerifyTaxonomyExporter extends DocumentFileExporter {
    public String getDefaultExtension() {
        return "xls";
    }

    public String getFileTypeDescription() {
        return "Verify Taxonomy Table (Excel)";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {new DocumentSelectionSignature(VerifyTaxonomyResultsDocument.class, 1, 1)};
    }

    @Override
    public void export(File file, AnnotatedPluginDocument[] documents, ProgressListener progressListener, Options options) throws IOException {
        WritableWorkbook workbook = Workbook.createWorkbook(file);
        try {


            VerifyTaxonomyTableModel tableModel = new VerifyTaxonomyTableModel(documents[0], null, false);
            WritableSheet sheet = workbook.createSheet("Verify Taxonomy Results", 0);
            ExcelUtilities.exportTable(sheet, tableModel, progressListener);

            workbook.write();
        } catch (WriteException e) {
            throw new IOException(e.getMessage());
        } finally {
            try {
                workbook.close();
            } catch (WriteException e) {
                //ignore this - should have failed the write method so we will get an IOException anyways...
            }
        }
    }
}
