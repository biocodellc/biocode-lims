package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.plugin.GeneiousServiceWithPanel;
import com.biomatters.geneious.publicapi.plugin.Icons;
import com.biomatters.geneious.publicapi.plugin.ExtendedPrintable;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.components.RoundedShadowBorder;
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
import java.awt.print.PrinterException;
import java.awt.print.Printable;
import java.text.AttributedString;
import java.sql.SQLException;

/**
 * Created by IntelliJ IDEA.
 * User: Steve
 * Date: 29/01/11
 * Time: 4:05 AM
 * To change this template use File | Settings | File Templates.
 */
public class ReportingService extends GeneiousServiceWithPanel implements Chartable {
    private JPanel panel;
    JComponent chartPanel;
    JSplitPane splitPane;

    public void setChartPanel(final JComponent panel) {
        Runnable runnable = new Runnable() {
            public void run() {
                if(splitPane != null) {
                    splitPane.setBottomComponent(new GPanel());
                }
                chartPanel = panel;
                if(splitPane != null && chartPanel != null) {
                    splitPane.setBottomComponent(chartPanel);
                    splitPane.revalidate();
                }
            }
        };
        ThreadUtilities.invokeNowOrLater(runnable);
    }

    public void notifyLoginStatusChanged() {
        if(panel != null) {
            Runnable runnable = new Runnable() {
                public void run() {
                    try {
                        fillPanel();
                    } catch (SQLException e) {
                        e.printStackTrace();
                        e.printStackTrace();
                        Dialogs.showMessageDialog(e.getMessage());  //todo: add the stacktrace
                    }
                    panel.revalidate();
                }
            };
            ThreadUtilities.invokeNowOrLater(runnable);
        }
    }


    @Override
    public JPanel getPanel() {
        if(panel == null) {
            panel = new GPanel(new BorderLayout());
        }
        try {
            fillPanel();
        } catch (SQLException e) {
            e.printStackTrace();
            Dialogs.showMessageDialog(e.getMessage());  //todo: add the stacktrace
        }
        return panel;
    }

    public ExtendedPrintable getExtendedPrintable() {
        return new ExtendedPrintable() {
            public int print(Graphics2D graphics, Dimension dimensions, int pageIndex, Options options) throws PrinterException {
                if(chartPanel == null || pageIndex > 0) {
                    return Printable.NO_SUCH_PAGE;
                }
                Component component;
                component = chartPanel.getComponent(0);
                Rectangle bounds = component.getBounds();
                component.setBounds(0,0,dimensions.width, dimensions.height);
                ((JComponent)component).setOpaque(false);
                component.print(graphics);
                component.setBounds(bounds);
                return Printable.PAGE_EXISTS;
            }

            public int getPagesRequired(Dimension dimensions, Options options) {
                return chartPanel == null ? 0 : 1;
            }
        };
    }

    private void fillPanel() throws SQLException {
        panel.removeAll();

        if(!BiocodeService.getInstance().isLoggedIn()) {
            JTextPane textPane = new JTextPane();
            textPane.setContentType("text/html");
            textPane.setText("<html>" + GuiUtilities.getHtmlHead() + "<body>" + "<center>" + "Please Log in to a LIMS to view the reporting functions." + "</center>" + "</body>" + "</html>");
            JPanel roundedBorderPanel = new JPanel(new BorderLayout());
            roundedBorderPanel.setBorder(new RoundedShadowBorder());
            roundedBorderPanel.add(textPane, BorderLayout.CENTER);
            roundedBorderPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
            Dimension textPanePreferredSize = textPane.getPreferredSize();
            roundedBorderPanel.setPreferredSize(new Dimension(textPanePreferredSize.width + 50, textPanePreferredSize.height + 50));
            JPanel roundedBorderPanelWrapper = new JPanel();
            roundedBorderPanelWrapper.add(roundedBorderPanel);
            JPanel holderPanel = new GPanel(new GridBagLayout());
            holderPanel.add(roundedBorderPanelWrapper, new GridBagConstraints());
            panel.add(holderPanel, BorderLayout.CENTER);
            return;
        }

        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        final ReportGenerator reportGenerator = new ReportGenerator(this);
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
        chartPanel = new ChartPanel(new JFreeChart(piePlot));

        splitPane.setBottomComponent(chartPanel);


        panel.add(splitPane, BorderLayout.CENTER);
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
