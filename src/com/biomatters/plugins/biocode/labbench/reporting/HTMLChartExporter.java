package com.biomatters.plugins.biocode.labbench.reporting;

import jebl.util.ProgressListener;

import javax.swing.table.TableModel;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;

import com.biomatters.geneious.publicapi.utilities.GuiUtilities;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 15/12/2011 12:01:32 PM
 */


public class HTMLChartExporter implements ChartExporter{


    private String tableName;
    private TableModel model;

    public HTMLChartExporter(String tableName, TableModel model) {
        this.tableName = tableName;
        this.model = model;
    }


    public String getFileTypeDescription() {
        return "HTML File";
    }

    public String getDefaultExtension() {
        return "html";
    }

    public void export(File file, ProgressListener progressListener) throws IOException {
        PrintWriter out = new PrintWriter(new FileWriter(file));
        try {
            out.println("<html>");
            out.println("<head>");
            out.println("<style type=\"text/css\">");
            out.println(GuiUtilities.getHtmlStylesheet());
            out.println("tr.even td {");
            out.println("\tbackground-color : #EDF3FE;");
            out.println("}");
            out.println("td, th {");
            out.println("\tborder-width:1px;\n");
            out.println("\tborder-style:solid;\n");
            out.println("\tborder-color:#aaaaaa;\n");
            out.println("\tborder-collapse:collapse\n");
            out.println("}");
            out.println("flatBorder {\n");
            out.println("}");
            out.println("</style>");
            out.println("</head>");
            out.println("<body>");
            out.println("<table class=\"flatBorder\">");
            out.println("<tr>");
            for(int i=0; i < model.getColumnCount(); i++) {
                out.print("<th>"+model.getColumnName(i)+"</th>");
            }
            out.println();
            for(int row = 0; row < model.getRowCount(); row++) {
                out.print("<tr class=\""+(row %2 == 0 ? "even" : "odd")+"\">");
                for(int col = 0; col < model.getColumnCount(); col++) {
                    out.print("<td class=\"flatBorder\">"+model.getValueAt(row, col)+"</td>");
                }
                out.println("</tr>");
            }
            out.println("</table>");
            out.println("</body>");
            out.println("</html>");
        }
        finally {
            out.close();
        }
    }
}
