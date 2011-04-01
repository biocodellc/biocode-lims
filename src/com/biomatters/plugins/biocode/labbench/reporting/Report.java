package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import org.jfree.chart.ChartPanel;

import java.sql.SQLException;

/**
 * @author Steve
 * @version $Id$
 */
public interface Report {

    public String getName();

    public Options getOptions(FimsToLims fimsToLims) throws SQLException;

    public ChartPanel getChart(Options options)  throws SQLException;

}
