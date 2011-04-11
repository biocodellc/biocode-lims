package com.biomatters.plugins.biocode.labbench.reporting;

import org.jfree.data.xy.XYDataItem;

import java.util.Date;
import java.text.DateFormat;

/**
 * @author Steve
 * @version $Id$
 */
public class DateDataItem extends XYDataItem{
    private Date date;
    private static DateFormat dateformat = DateFormat.getDateInstance();

    public DateDataItem(Date date, Number number1) {
        super(date.getTime(), number1);
        this.date = date;
    }

    @Override
    public String toString() {
        return dateformat.format(date);
    }
}
