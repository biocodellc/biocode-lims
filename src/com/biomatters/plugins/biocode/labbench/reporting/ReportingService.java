package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.components.RoundedShadowBorder;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.PieSectionLabelGenerator;
import org.jfree.chart.plot.PiePlot;
import org.jfree.data.general.PieDataset;

import javax.swing.*;
import javax.swing.border.MatteBorder;
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
    JPanel mainPanel;
    private ReportGenerator reportGenerator;
    private JPanel topPanel;

    public void setChartPanel(final JComponent panel) {
        Runnable runnable = new Runnable() {
            public void run() {
                if(mainPanel != null) {
                    mainPanel.removeAll();
                    mainPanel.add(topPanel, BorderLayout.NORTH);
                    mainPanel.add(new GPanel(), BorderLayout.CENTER);
                }
                chartPanel = panel;

                if(mainPanel != null && chartPanel != null) {
                    chartPanel.setBorder(new MatteBorder(1,0,1,0, panel.getBackground().darker()));
                    mainPanel.add(topPanel, BorderLayout.NORTH);
                    mainPanel.add(chartPanel, BorderLayout.CENTER);
                    mainPanel.revalidate();
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
                        if(BiocodeService.getInstance().isLoggedIn()) {
                            reportGenerator = new ReportGenerator(ReportingService.this, BiocodeService.getInstance().getDataDirectory());
                            reportGenerator.update();
                        }
                        else {
                            reportGenerator = null;
                        }
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


    public ReportGenerator getReportGenerator() {
        return reportGenerator;
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

        if(!BiocodeService.getInstance().isLoggedIn() || reportGenerator == null) {
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

        mainPanel = new GPanel(new BorderLayout());
        topPanel = reportGenerator.getReportingPanel();
        mainPanel.add(topPanel, BorderLayout.NORTH);
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

        mainPanel.add(chartPanel, BorderLayout.CENTER);


        panel.add(mainPanel, BorderLayout.CENTER);
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

    public void updateReportGenerator() {
        if(reportGenerator != null) {
            reportGenerator.update();
        }
    }
}
