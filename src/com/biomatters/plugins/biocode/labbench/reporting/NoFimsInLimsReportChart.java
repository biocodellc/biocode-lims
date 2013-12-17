package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.components.RoundedShadowBorder;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;

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
        JPanel roundedBorderPanel = BiocodeUtilities.getRoundedBorderPanel(message);
        JPanel roundedBorderPanelWrapper = new JPanel(new GridBagLayout());
        roundedBorderPanelWrapper.add(roundedBorderPanel);
        return roundedBorderPanelWrapper;
    }
}
