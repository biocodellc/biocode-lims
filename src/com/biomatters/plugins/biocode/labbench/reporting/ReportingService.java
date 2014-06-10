package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
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

    private Runnable loginStatusChangedRunnable = new Runnable() {
        public void run() {
            try {
                if(BiocodeService.getInstance().isLoggedIn() && BiocodeService.getInstance().getActiveLIMSConnection().supportReporting()) {
                    reportGenerator = new ReportGenerator(ReportingService.this, BiocodeService.getInstance().getDataDirectory());
                    reportGenerator.update();
                }
                else {
                    reportGenerator = null;
                }
                fillPanel();
            } catch (SQLException e) {
                e.printStackTrace();
                Dialogs.showMessageDialog("Error connecting the reporting module: "+e.getMessage());  //todo: add the stacktrace
            } catch (DatabaseServiceException e) {
                e.printStackTrace();
                Dialogs.showMessageDialog(e.getMessage());
            }
            panel.revalidate();
        }
    };

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
            ThreadUtilities.invokeNowOrLater(loginStatusChangedRunnable);
        }
    }


    public ReportGenerator getReportGenerator() {
        return reportGenerator;
    }

    @Override
    public JPanel getPanel() {
        if(panel == null) {
            panel = new GPanel(new BorderLayout());
            loginStatusChangedRunnable.run();
        }
        else {
            try {
                fillPanel();
            } catch (SQLException e) {
                e.printStackTrace();
                String message = e.getMessage();
                if(message.contains("Communications link failure due to underlying exception")) {
                    message = "Could not connect to server.  Please check your network connection, and reconnect.";
                }
                Dialogs.showMessageDialog(message);  //todo: add the stacktrace
            } catch (DatabaseServiceException e) {
                e.printStackTrace();
                Dialogs.showMessageDialog(e.getMessage());
            }
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

    private void fillPanel() throws SQLException, DatabaseServiceException {
        panel.removeAll();

        if(!BiocodeService.getInstance().isLoggedIn() || reportGenerator == null) {
            String message = "Please Log in to a LIMS to view the reporting functions.";
            if(BiocodeService.getInstance().isLoggedIn() && !BiocodeService.getInstance().getActiveLIMSConnection().supportReporting()) {
                message = "This LIMS connection does not support the reporting module.";
            }
            JPanel roundedBorderPanel = BiocodeUtilities.getRoundedBorderPanel(message);
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
