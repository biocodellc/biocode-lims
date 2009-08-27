package com.biomatters.plugins.moorea.submission.bold;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.moorea.MooreaPlugin;
import jebl.util.ProgressListener;
import jxl.Workbook;
import jxl.read.biff.BiffException;
import jxl.write.Label;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class ExportForBoldOperation extends DocumentOperation {

    private static final URL DATA_XLS_TEMPLATE = ExportForBoldOperation.class.getResource("data.xls");

    public static void main(String[] args) throws IOException, WriteException, BiffException, URISyntaxException {
        File dataXsl = new File(DATA_XLS_TEMPLATE.toURI());
        Workbook dataWorkbook = Workbook.getWorkbook(dataXsl);
        WritableWorkbook writableDataWorkbook = Workbook.createWorkbook(new File("/Users/richard/Desktop/data.xls"), dataWorkbook);
        WritableSheet dataSheet = writableDataWorkbook.getSheet(0);
        dataSheet.addCell(new Label(0,1,"bar"));
        writableDataWorkbook.write();
        writableDataWorkbook.close();
        dataWorkbook.close();
    }

    public GeneiousActionOptions getActionOptions() {
        GeneiousActionOptions geneiousActionOptions = new GeneiousActionOptions("Export for BOLD Submission...").setInPopupMenu(true, 0.8);
        return GeneiousActionOptions.createSubmenuActionOptions(MooreaPlugin.getSuperBiocodeAction(), geneiousActionOptions);
    }

    public String getHelp() {
        return ""; //todo
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[0];
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        return super.getOptions(documents);
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options) throws DocumentOperationException {
        throw new DocumentOperationException("Coming soon");
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }
}