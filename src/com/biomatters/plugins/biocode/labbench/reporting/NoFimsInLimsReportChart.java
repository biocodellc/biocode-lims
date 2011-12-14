package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.components.RoundedShadowBorder;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 14/12/2011 4:18:35 PM
 */


public class NoFimsInLimsReportChart extends Report.ReportChart {

    private String message;

    public NoFimsInLimsReportChart(String message) {
        this.message = message;
    }

    public JPanel getPanel() {
        JTextPane textPane = new JTextPane();
        textPane.setContentType("text/html");
        textPane.setText(
                "<html>" + GuiUtilities.getHtmlHead() +
                "<body>" +
                "<center>" +
                "<br>" +
                message +
                "</center>" +
                "</body>" +
                "</html>");
        textPane.setEditable(false);
        JPanel roundedBorderPanel = new JPanel(new BorderLayout());
        roundedBorderPanel.setBorder(new RoundedShadowBorder());
        roundedBorderPanel.add(textPane, BorderLayout.CENTER);
        roundedBorderPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        Dimension textPanePreferredSize = textPane.getPreferredSize();
        roundedBorderPanel.setPreferredSize(
                new Dimension(textPanePreferredSize.width + 50, textPanePreferredSize.height + 50));
        JPanel roundedBorderPanelWrapper = new JPanel(new GridBagLayout());
        roundedBorderPanelWrapper.add(roundedBorderPanel);
        return roundedBorderPanelWrapper;
    }
}
