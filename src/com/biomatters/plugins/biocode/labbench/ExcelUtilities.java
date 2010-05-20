package com.biomatters.plugins.biocode.labbench;

import jxl.write.*;
import jxl.write.Label;
import jxl.format.Colour;

import javax.swing.table.TableModel;

import jebl.util.ProgressListener;
import com.biomatters.geneious.publicapi.plugin.Options;

import java.io.IOException;
import java.awt.*;

public class ExcelUtilities {
    public static void exportTable(WritableSheet sheet, TableModel table, ProgressListener progressListener, Options options) throws IOException, WriteException {
        //write out the column headers
        for (int i = 0; i < table.getColumnCount(); i++) {
            sheet.addCell(new Label(i, 0, table.getColumnName(i)));
            sheet.setColumnView(i, 25);
        }

        WritableFont defaultFont = new WritableFont(sheet.getCell(0, 0).getCellFormat().getFont());


        //write out the column headers
        for (int i = 0; i < table.getColumnCount(); i++) {
            for (int j = 0; j < table.getRowCount(); j++) {
                Object tableValue = table.getValueAt(j, i);
                Label label;
                if (tableValue instanceof TableDocumentViewerFactory.ObjectAndColor) {
                    TableDocumentViewerFactory.ObjectAndColor objectAndColor = (TableDocumentViewerFactory.ObjectAndColor) tableValue;
                    WritableFont font = new WritableFont(defaultFont);
                    Color color = objectAndColor.getColor();
                    if (color.equals(Color.WHITE)) {
                        color = Color.BLACK;
                    }
                    Colour closestColour = getClosestColour(color);
                    font.setColour(closestColour);
                    label = new Label(i, j + 1, objectAndColor.getObject().toString(), new WritableCellFormat(font));
                } else {
                    label = new Label(i, j + 1, "" + tableValue);
                }
                sheet.addCell(label);
            }
        }
    }

    public static Colour getClosestColour(Color c) {
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