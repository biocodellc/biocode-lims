package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.plugin.ExtendedPrintable;
import com.biomatters.geneious.publicapi.plugin.Options;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.print.PrinterException;
import java.awt.print.Printable;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 27/07/2009 3:49:27 PM
 */
public class JTablePrintable extends ExtendedPrintable {
    private JTable table;
    private JTableHeader header;

    public JTablePrintable(JTable table) {
        this.table = table;
        this.header = table.getTableHeader();
    }


    public int print(Graphics2D graphics, Dimension dimensions, int pageIndex, Options options) throws PrinterException {
        if(pageIndex > getPagesRequired(dimensions, options)) {
            return Printable.NO_SUCH_PAGE;
        }
        int rowHeight = table.getRowHeight();
        table.setBounds(0, 0, dimensions.width, table.getPreferredSize().height);
        table.validate();

        int headerHeight = 0;
        if(header != null) {
            headerHeight = header.getPreferredSize().height;
            header.setBounds(new Rectangle(0,0,dimensions.width, headerHeight));
            header.validate();
            header.print(graphics);
        }
        int numberOfRows = (dimensions.height-headerHeight)/rowHeight;

        table.setBounds(0,0, dimensions.width, table.getPreferredSize().height);
        table.validate();

        graphics.clipRect(0, headerHeight, dimensions.width, numberOfRows*rowHeight);
        graphics.translate(0, headerHeight-numberOfRows*table.getRowHeight()*pageIndex);
                
        table.print(graphics);

        return Printable.PAGE_EXISTS;
    }

    public int getPagesRequired(Dimension dimensions, Options options) {
        int rowHeight = table.getRowHeight();
        int headerHeight = header == null ? 0 : header.getPreferredSize().height;
        return (int)Math.ceil(((double)table.getRowCount())/((dimensions.height-headerHeight)/rowHeight));
    }
}
