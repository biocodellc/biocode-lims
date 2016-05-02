package com.biomatters.plugins.biocode.labbench.reporting;

import jebl.util.ProgressListener;

import javax.swing.table.TableModel;
import java.io.File;
import java.io.IOException;

import jxl.write.WritableWorkbook;
import jxl.write.WritableSheet;
import jxl.write.WriteException;
import jxl.Workbook;
import com.biomatters.plugins.biocode.labbench.ExcelUtilities;

/**
 * @author Steve
 *          <p/>
 *          Created on 2/09/2011 10:15:27 AM
 */


public class ExcelChartExporter implements ChartExporter{

    private String tableName;
    private TableModel model;

    public ExcelChartExporter(String tableName, TableModel model) {
        this.tableName = tableName;
        this.model = model;
    }

    public String getFileTypeDescription() {
        return "Excel File";
    }

    public String getDefaultExtension() {
        return "xls";
    }

    public void export(File file, ProgressListener progressListener) throws IOException {
        WritableWorkbook workbook = null;
        try {
            workbook = Workbook.createWorkbook(file);


            WritableSheet sheet = workbook.createSheet(tableName, 0);
            ExcelUtilities.exportTable(sheet, model, progressListener);

            workbook.write();
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
