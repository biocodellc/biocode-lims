package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.geneious.publicapi.plugin.GeneiousServiceWithPanel;
import com.biomatters.geneious.publicapi.plugin.Icons;
import com.biomatters.plugins.biocode.BiocodePlugin;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.PieSectionLabelGenerator;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PiePlot3D;
import org.jfree.chart.plot.Plot;
import org.jfree.data.general.PieDataset;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.awt.*;
import java.text.AttributedString;

/**
 * Created by IntelliJ IDEA.
 * User: Steve
 * Date: 29/01/11
 * Time: 4:05 AM
 * To change this template use File | Settings | File Templates.
 */
public class ReportingService extends GeneiousServiceWithPanel {


    @Override
    public JPanel getPanel() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        final ReportGenerator reportGenerator = new ReportGenerator();
        JPanel topPanel = reportGenerator.getReportingPanel();
        splitPane.setTopComponent(topPanel);
        splitPane.setBottomComponent(new GPanel());
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(-1);
        final PiePlot piePlot = new PiePlot();
        piePlot.setLabelGenerator(new PieSectionLabelGenerator() {
            public String generateSectionLabel(PieDataset pieDataset, Comparable comparable) {
                Number value = pieDataset.getValue(comparable);
                if(value.intValue() == 0) {
                    return null;
                }
                return comparable.toString()+" ("+value+")";
            }

            public AttributedString generateAttributedSectionLabel(PieDataset pieDataset, Comparable comparable) {
                return new AttributedString("arse2");
            }
        });
        final ChartPanel chartPanel = new ChartPanel(new JFreeChart(piePlot));
        reportGenerator.setChartChangedListener(new SimpleListener() {
            public void objectChanged() {
                piePlot.setDataset(reportGenerator.getDataset());
                chartPanel.getAnchor();
                //chartPanel.createChartPrintJob();

            }
        });

        splitPane.setBottomComponent(chartPanel);

        JPanel holder = new GPanel(new BorderLayout());
        holder.add(splitPane, BorderLayout.CENTER);
        return holder;
    }

    @Override
    public String getUniqueID() {
        return "biocode_reporting";
    }

    @Override
    public String getName() {
        return "Reporting";
    }

    @Override
    public String getDescription() {
        return "Provides reporting metrics for your FIMS and LIMS database";
    }

    @Override
    public String getHelp() {
        return "";
    }

    @Override
    public Icons getIcons() {
        return BiocodePlugin.getIcons("reporting.png");
    }
}
