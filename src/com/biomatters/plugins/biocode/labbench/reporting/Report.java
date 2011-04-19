package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import org.jfree.chart.ChartPanel;

import java.sql.SQLException;

import jebl.util.ProgressListener;

import javax.swing.*;

/**
 * @author Steve
 * @version $Id$
 */
public interface Report {

    public String getName();

    public Options getOptions(FimsToLims fimsToLims) throws SQLException;

    public ReportChart getChart(Options options, FimsToLims fimsToLims, ProgressListener progress)  throws SQLException;


    public static abstract class ReportChart {
        public Options getOptions() {
            return null;
        }
        public abstract JPanel getPanel();
    }

}
