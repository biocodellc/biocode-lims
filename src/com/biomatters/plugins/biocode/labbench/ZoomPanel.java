package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;


/**
 * borrowed from package com.biomatters.components
 * @author Matt Kearse
 * @version $Id: ZoomPanel.java 26600 2009-06-04 04:54:26Z richard $
 */
public class ZoomPanel extends JPanel {

    SpinnerNumberModel zoomSpinnerModel;
    JSpinner zoomSpinner;

    private double zoomFactor = Math.sqrt(2.0);
    private double bigZoomFactor = 2.0;

    double fitToScreenZoomLevel = 0.0;
    private double minZoomLevel=0.0;
    double maxZoomLevel = 1.0;
    double zoomLevel = 0;

    boolean settingSpinner = false;

    private JButton zoomIn = new JButton("");
    private JButton zoomOut = new JButton("");
    private JButton zoomToSelection = new JButton("");
    private JButton zoomTo100Percent = new JButton("");
    private JButton fitToScreen = new JButton("");

    private Vector<ChangeListener> listeners = new Vector<ChangeListener>();

    static private Icon zoomout1 = null;
    static private Icon zoomout2 = null;
    static private Icon zoomin1 = null;
    static private Icon zoomin2 = null;
    static private Icon zoomToSelectionIcon = null;
    private boolean showZoomButtons;
    private boolean showZoomCompletelyButtons = true;
    private ActionListener zoomToSelectionAction;
    private int spinnerIncrement;

    public ZoomPanel() {
        this(true, true, null, 5);
    }

    /**
     *
     * @param showZoomButtons true to show the "zoom in" and "zoom out" buttons on the left
     * @param showZoomCompletelyButtons true to show the "zoom to 100% and "fit to screen" buttons on the right
     * @param zoomToSelectionAction non-null to show a zoom to selection button. null to not show it.
     * @param spinnerIncrement
     */
    public ZoomPanel(boolean showZoomButtons, boolean showZoomCompletelyButtons, ActionListener zoomToSelectionAction, int spinnerIncrement) {
        this.zoomToSelectionAction = zoomToSelectionAction;
        this.spinnerIncrement = spinnerIncrement;
        this.showZoomButtons = showZoomButtons;
        this.showZoomCompletelyButtons = showZoomCompletelyButtons;
        createZoomButtons();
    }

    public void addChangeListener(ChangeListener listener) {
        listeners.add(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        listeners.remove(listener);
    }

    public JSpinner getZoomSpinner() {
        return zoomSpinner;
    }

    public double getMinZoomLevel() {
        return fitToScreenZoomLevel;
    }

    public double getMaxZoomLevel() {
        return maxZoomLevel;
    }

    public void setMaximumZoomLevel(double maxZoomLevel) {
        this.maxZoomLevel = maxZoomLevel;

        updateSpinnerRanges();
        checkButtonsEnabled();
    }

    public void setMinimumAndFitToScreenZoomLevel(double minZoomLevel) {

        if (this.fitToScreenZoomLevel == minZoomLevel && this.minZoomLevel == minZoomLevel) return;

        boolean wasFitToScreenZoomLevel = (getZoomLevel() == this.fitToScreenZoomLevel);

        this.fitToScreenZoomLevel = minZoomLevel;
        this.minZoomLevel = minZoomLevel;

        if ((getZoomLevel() < minZoomLevel) || wasFitToScreenZoomLevel) {
            setZoomLevel(minZoomLevel);
        }
        updateSpinnerRanges();
        checkButtonsEnabled();
    }

    private void updateSpinnerRanges () {
        settingSpinner = true;
        int value = (int)Math.round (minZoomLevel * 100);
//        Logs.temp("set minimum =" + value);
        zoomSpinnerModel.setMinimum(value);
        value = (int) Math.round(maxZoomLevel * 100);
//        Logs.temp("set maximum =" + value);
        zoomSpinnerModel.setMaximum(value);
        settingSpinner = false;
    }

    public void setMinimumAndMaximumAndCurrentZoomLevels(double minimum, double maximum, double current, boolean fireChanges) {
        setMinimumAndMaximumAndCurrentZoomLevels(minimum, minimum, maximum,  current, fireChanges);
    }

    public void setMinimumAndMaximumAndCurrentZoomLevels(double minimum, double zoomOutCompletelyZoomLevel, double maximum, double current, boolean fireChanges) {
        this.minZoomLevel = minimum;
        this.fitToScreenZoomLevel = zoomOutCompletelyZoomLevel;
        this.maxZoomLevel= maximum;
        setZoomLevel(current, fireChanges);
    }

    private static void setTooltipSuffix(JButton b, KeyStroke s) {
        if (s!=null)
            b.setToolTipText(b.getToolTipText() + " (" + GuiUtilities.getStringForKeyStroke(s) + ")");
    }

    public void addKeyStrokesToTooltips(KeyStroke zoomIn, KeyStroke zoomOut, KeyStroke zoomToSelection, KeyStroke zoomInComplete, KeyStroke zoomOutComplete) {
        setTooltipSuffix(this.zoomIn,zoomIn);
        setTooltipSuffix(this.zoomOut,zoomOut);
        setTooltipSuffix(this.zoomToSelection,zoomToSelection);
        setTooltipSuffix(this.zoomTo100Percent,zoomInComplete);
        setTooltipSuffix(this.fitToScreen,zoomOutComplete);
    }

    /**
     *
     * @return zoom level as fraction. i.e. 1 = 100%
     */
    public double getZoomLevel() {
        return zoomLevel;
    }

    public void setZoomLevel(double zoomLevel) {
        setZoomLevel(zoomLevel,true);
    }


    private void checkButtonsEnabled () {
        zoomIn.setEnabled(zoomLevel < maxZoomLevel);
        zoomTo100Percent.setEnabled((zoomLevel>1 && fitToScreenZoomLevel <=1) || (zoomLevel != 1 && fitToScreenZoomLevel <= 1 && zoomLevel < maxZoomLevel));
        zoomOut.setEnabled(zoomLevel > minZoomLevel);
        fitToScreen.setEnabled(zoomLevel != fitToScreenZoomLevel);
    }

   public void setZoomLevel(double zoomLevel, boolean firechanges) {
       if(maxZoomLevel< fitToScreenZoomLevel) maxZoomLevel= fitToScreenZoomLevel;

        if (zoomLevel > maxZoomLevel) {
           zoomLevel = maxZoomLevel;
       }
       if (zoomLevel < minZoomLevel) {
           zoomLevel = minZoomLevel;
       }

       boolean hasChanged =zoomLevel!= this.zoomLevel;

       this.zoomLevel = zoomLevel;

       checkButtonsEnabled();
       updateSpinnerRanges();


       int z = (int) Math.round(zoomLevel * 100.0);

       settingSpinner = true;
//       Logs.temp("set value =" +z);
       zoomSpinnerModel.setValue(z);
       settingSpinner = false;

       if (! hasChanged) return;
       if(! firechanges) return;
       for (ChangeListener listener : listeners) {
           listener.stateChanged(new ChangeEvent(this));
       }
   }

    private int getZoomAdjustment(ActionEvent e) {
        int adjustment = 0;
        if ((e.getModifiers() & ActionEvent.ALT_MASK)!=0) {
            adjustment+=2;
        }
        if ((e.getModifiers() & ActionEvent.SHIFT_MASK)!=0) {
            adjustment+=4;
        }
        return adjustment;
    }

    private void createZoomButtons() {
        zoomSpinnerModel = new SpinnerNumberModel(100, 0, 200, spinnerIncrement);

        zoomSpinner = new JSpinner();
        zoomSpinner.setModel(zoomSpinnerModel);
        zoomSpinnerModel.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
//                Logs.temp("State change");
                if (!settingSpinner) {
                    double zoomLevel = zoomSpinnerModel.getNumber().doubleValue();
//                    Logs.temp("new value =" + zoomLevel);
                    setZoomLevel(zoomLevel/100);
                }
            }
        });

        if (zoomout1 == null) {
            zoomout1 = IconUtilities.getIcons("zoomout.png").getIcon16();
            zoomout2 = IconUtilities.getIcons("minzoom.png").getIcon16();
            zoomin1 = IconUtilities.getIcons("zoomin.png").getIcon16();
            zoomin2 = IconUtilities.getIcons("fullzoom.png").getIcon16();
            zoomToSelectionIcon = IconUtilities.getIcons("zoomToSelection16.png").getIcon16();
        }

        zoomIn.setIcon(zoomin1);
        zoomTo100Percent.setIcon(zoomin2);
        zoomToSelection.setIcon(zoomToSelectionIcon);
        zoomOut.setIcon(zoomout1);
        fitToScreen.setIcon(zoomout2);

        zoomIn.setToolTipText("Zoom in. Hold down alt (and/or shift) to zoom in only a little bit");
        zoomTo100Percent.setToolTipText("Zoom to 100%");
        zoomToSelection.setToolTipText("Zoom to selection");
        zoomOut.setToolTipText("Zoom out. Hold down alt (and/or shift) to zoom out only a little bit");
        fitToScreen.setToolTipText("Zoom out to full view");
        zoomSpinner.setToolTipText("Zoom to level");

        Insets spaces = new Insets(2, 4, 2, 4);

        zoomOut.setMargin(spaces);
        fitToScreen.setMargin(spaces);
        zoomToSelection.setMargin(spaces);
        zoomIn.setMargin(spaces);
        zoomTo100Percent.setMargin(spaces);

        /*int height = zoomSpinner.getPreferredSize().height;
        //noinspection SuspiciousNameCombination
        Dimension buttonSize = new Dimension(height, height);
        zoomOut.setPreferredSize(buttonSize);
        fitToScreen.setPreferredSize(buttonSize);
        zoomIn.setPreferredSize(buttonSize);
        zoomTo100Percent.setPreferredSize(buttonSize);*/

        zoomOut.setOpaque(false);
        fitToScreen.setOpaque(false);
        zoomIn.setOpaque(false);
        zoomTo100Percent.setOpaque(false);
        zoomToSelection.setOpaque(false);

        zoomSpinner.setOpaque(false);

        zoomIn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                zoomIn (getZoomAdjustment(e));
            }

        });
        zoomOut.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                zoomOut (getZoomAdjustment(e));
            }


        });
        zoomToSelection.addActionListener(zoomToSelectionAction);
        zoomTo100Percent.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                zoomTo100Percent();
            }

        });
        fitToScreen.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                fitToScreen();
            }
        });


        setLayout(new GridBagLayout());
        setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
//        c.insets = new Insets(5, 5, 5, 5);
        c.insets = new Insets(1, 1, 1, 1);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0.0;
        c.weighty = 0.0;
        c.anchor = GridBagConstraints.CENTER;
        c.gridwidth = 1;

        if (showZoomButtons) {
            add(zoomIn, c);
            add(zoomOut, c);
        }
        c.weightx = 1.0;
        add(new JLabel(), c);

        c.weightx = 0.0;
        add(zoomSpinner, c);
        add(new JLabel("%"));

        if(showZoomCompletelyButtons) {
            c.weightx = 1.0;
            add(new JLabel(), c);
            c.weightx = 0.0;
            if (zoomToSelectionAction!=null) {
                add(zoomToSelection,c);
            }
            add(zoomTo100Percent, c);
            c.gridwidth = GridBagConstraints.REMAINDER;
            add(fitToScreen, c);
        }
        else {
            c.weightx = 1.0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            add(new JLabel(), c);
        }
    }

    public void zoomIn() {
        zoomIn(0);
    }

    public void zoomIn(int amountToReduceZoomFactorBy) {
        double oldZoom = getZoomLevel();
        double factor = zoomFactor;
        if (oldZoom <= 0.2501 && oldZoom> fitToScreenZoomLevel /1.01) {
            factor = bigZoomFactor;
        }
        for (int i = 0; i < amountToReduceZoomFactorBy; i++) {
            factor=Math.sqrt(factor);
        }
        double zoom=oldZoom * factor;
        if(zoom *1.01>maxZoomLevel) zoom *=1.01; // if it's almost at maximum,
        // make it the maximum in order to avoid the situation where the user was at
        // maximum zoom, zooms out, then zooms in again, but due to rounding is no
        // longer at maximum zoom

        if (oldZoom< fitToScreenZoomLevel /1.05 && zoom> fitToScreenZoomLevel /1.01) {
            zoom = fitToScreenZoomLevel;   // don't skip past zoomOutCompletelyZoomLevel
        }

        if(oldZoom<0.95 && zoom>0.99999) {
            zoom=1; // don't skip past exactly 100%
        }
        setZoomLevel(zoom);
    }

    public void zoomOut() {
        zoomOut(0);
    }

    public void zoomOut(int amountToReduceZoomFactorBy) {
        double oldZoom = getZoomLevel();
        double factor = zoomFactor;
        if (oldZoom <= 0.5001 && oldZoom> fitToScreenZoomLevel *1.01) {
            factor = bigZoomFactor;
        }
        for (int i = 0; i < amountToReduceZoomFactorBy; i++) {
            factor=Math.sqrt(factor);
        }
        double zoom = oldZoom / factor;
        if (zoom> fitToScreenZoomLevel && zoom / 1.01  < fitToScreenZoomLevel) zoom = fitToScreenZoomLevel; // if it's almost at minimum,
        // make it the minimum in order to avoid the situation where the user was at
        // minimum zoom, zooms in, then zooms out again, but due to rounding is no
        // longer at minimum zoom
        if (zoom> minZoomLevel && zoom / 1.01  < minZoomLevel) zoom = minZoomLevel;
        if (oldZoom> fitToScreenZoomLevel *1.05 && zoom< fitToScreenZoomLevel) {
            zoom = fitToScreenZoomLevel;   // don't skip past minZoomLevel
        }
        if (oldZoom >1.05 && zoom <1.0001) {
            zoom = 1; // don't skip past exactly 100%
        }
        setZoomLevel(zoom);
    }

    public void zoomTo100Percent() {
        setZoomLevel(1.0);
    }

    public void fitToScreen() {
        setZoomLevel(fitToScreenZoomLevel);
    }

    public boolean isAtMinimumZoom() {
        return zoomLevel== minZoomLevel;
    }
}
