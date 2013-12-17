package com.biomatters.plugins.biocode.labbench;

import com.biomatters.plugins.biocode.utilities.ObjectAndColor;
import jxl.write.*;
import jxl.write.Label;
import jxl.format.Colour;
import jxl.NumberCell;

import javax.swing.table.TableModel;

import jebl.util.ProgressListener;
import com.biomatters.geneious.publicapi.plugin.Options;

import java.awt.*;
import java.lang.Number;
import java.lang.Boolean;
import java.util.Date;

public class ExcelUtilities {
    @SuppressWarnings({"UnusedDeclaration"})
    public static void exportTable(WritableSheet sheet, TableModel table, ProgressListener progressListener) throws WriteException {
        WritableFont defaultFont = new WritableFont(WritableFont.ARIAL);

        //write out the column headers
        for (int i = 0; i < table.getColumnCount(); i++) {
            WritableFont font = new WritableFont(defaultFont);
            font.setBoldStyle(WritableFont.BOLD);
            sheet.addCell(new Label(i, 0, table.getColumnName(i), new WritableCellFormat(font)));
            sheet.setColumnView(i, 25);
        }



        //write out the values
        for (int i = 0; i < table.getColumnCount(); i++) {
            for (int j = 0; j < table.getRowCount(); j++) {
                Object tableValue = table.getValueAt(j, i);
                WritableCell cell;
                WritableCellFormat format = null;
                if (tableValue instanceof ObjectAndColor) {
                    ObjectAndColor objectAndColor = (ObjectAndColor) tableValue;
                    WritableFont font = new WritableFont(defaultFont);
                    Color color = objectAndColor.getColor();
                    if (color.equals(Color.WHITE)) {
                        color = Color.BLACK;
                    }
                    Colour closestColour = getClosestColour(color);
                    font.setColour(closestColour);
                    format = new WritableCellFormat(font);
                }


                if(tableValue == null) {
                    cell = new Blank(i, j + 1);
                }
                else if(tableValue instanceof Number) {
                    cell = new jxl.write.Number(i, j + 1, ((Number)tableValue).doubleValue());
                }
                else if(tableValue instanceof Boolean) {
                    cell = new jxl.write.Boolean(i, j + 1, (Boolean)tableValue);
                }
                else if(tableValue instanceof Date) {
                    cell = new DateTime(i, j + 1, (Date)tableValue);
                }
                else {
                    cell = new Label(i, j + 1, tableValue.toString());
                }
                if(format != null) {
                    cell.setCellFormat(format);
                }

                sheet.addCell(cell);
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