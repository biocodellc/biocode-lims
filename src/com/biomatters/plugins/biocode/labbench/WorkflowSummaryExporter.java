package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.DocumentFileExporter;
import com.biomatters.geneious.publicapi.plugin.DocumentViewerFactory;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;

import java.io.File;
import java.io.IOException;
import java.awt.*;

import jebl.util.ProgressListener;

import javax.swing.table.TableModel;

import jxl.write.*;
import jxl.write.Label;
import jxl.Workbook;
import jxl.format.Colour;

/**
 * Created by IntelliJ IDEA.
 * User: Steve
 * Date: 19/05/2010
 * Time: 8:38:02 PM
 * To change this template use File | Settings | File Templates.
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
        return "Workflow Summary (Excel)";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {new DocumentSelectionSignature(WorkflowDocument.class, 1, Integer.MAX_VALUE)};
    }

    @Override
    public void export(File file, AnnotatedPluginDocument[] documents, ProgressListener progressListener, Options options) throws IOException {
        try {
            WritableWorkbook workbook = Workbook.createWorkbook(file);

            for (int i = 0; i < factoriesToExport.length; i++) {
                TableDocumentViewerFactory factory = factoriesToExport[i];
                TableModel tableModel = factory.getTableModel(documents);
                WritableSheet sheet = workbook.createSheet(factory.getName(), i);
                exportTable(sheet, tableModel, progressListener, options);
            }

            workbook.write();
            workbook.close();
        } catch (WriteException e) {
            throw new IOException(e.getMessage());
        }
    }

    protected void exportTable(WritableSheet sheet, TableModel table, ProgressListener progressListener, Options options) throws IOException, WriteException {
        //write out the column headers
        for(int i=0; i < table.getColumnCount(); i++) {
            sheet.addCell(new Label(i,0, table.getColumnName(i)));
            sheet.setColumnView(i,25);
        }

        WritableFont defaultFont = new WritableFont(sheet.getCell(0,0).getCellFormat().getFont());


        //write out the column headers
        for(int i=0; i < table.getColumnCount(); i++) {
            for(int j=0; j < table.getRowCount(); j++) {
                Object tableValue = table.getValueAt(j, i);
                Label label;
                if(tableValue instanceof TableDocumentViewerFactory.ObjectAndColor) {
                    TableDocumentViewerFactory.ObjectAndColor objectAndColor = (TableDocumentViewerFactory.ObjectAndColor)tableValue;
                    WritableFont font = new WritableFont(defaultFont);
                    Color color = objectAndColor.getColor();
                    if(color.equals(Color.WHITE)) {
                        color = Color.BLACK;
                    }
                    Colour closestColour = getClosestColour(color);
                    font.setColour(closestColour);
                    label = new Label(i,j+1, objectAndColor.getObject().toString(), new WritableCellFormat(font));
                }
                else {
                    label = new Label(i, j+1, ""+tableValue);
                }
                sheet.addCell(label);
            }
        }
    }

    protected Colour getClosestColour(Color c) {
        double distance = Double.MAX_VALUE;
        int indexWithSmallestDistance = 0;
        Colour[] allColours = Colour.getAllColours();
        for (int i = 0; i < allColours.length; i++) {
            Colour col = Colour.getAllColours()[i];
            double thisDistance = getDistance(col, c);
            if(thisDistance < distance) {
                indexWithSmallestDistance = i;
                distance = thisDistance;
            }
        }
        return allColours[indexWithSmallestDistance];
    }

    private static double getDistance(Colour color, Color color2) {
        double distance = 0;
        distance += Math.pow(color.getDefaultRGB().getRed() - color2.getRed(), 2);
        distance += Math.pow(color.getDefaultRGB().getGreen() - color2.getGreen(), 2);
        distance += Math.pow(color.getDefaultRGB().getBlue() - color2.getBlue(), 2);
        return Math.sqrt(distance);
    }
}
