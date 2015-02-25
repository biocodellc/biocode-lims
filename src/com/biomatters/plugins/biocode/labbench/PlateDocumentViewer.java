package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.components.*;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.geneious.publicapi.utilities.StandardIcons;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.plates.*;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import jebl.math.MachineAccuracy;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 2/07/2009 11:30:04 AM
 */
@SuppressWarnings({"ConstantConditions"})
public class PlateDocumentViewer extends DocumentViewer{
    private PlateDocument plateDoc;

    private GeneiousAction saveAction;
    private GeneiousAction zoomInAction;
    private GeneiousAction zoomOutAction;
    private GeneiousAction fullZoomAction;
    private GeneiousAction thermocycleAction;
    private List<Thermocycle> cycles;
    List<SimpleListener> actionsChangedListeners;
    private AnnotatedPluginDocument annotatedDocument;
    private PlateView plateView;
    private Map<Cocktail, Integer> cocktailCount;
    private final boolean isLocal;
    private JComponent container;
    private JComponent thingInsideScrollPane;
    private JPanel keyPanel;
    private JPanel thermocyclePanel;
    private boolean hasCheckedPlateForErrorsAtLeastOnce = false;

    public PlateDocumentViewer(PlateDocument doc, AnnotatedPluginDocument aDoc, boolean local) {
        this.annotatedDocument = aDoc;
        actionsChangedListeners = new ArrayList<SimpleListener>();
        this.plateDoc = doc;
        this.isLocal = local;
        Plate plate;
        addTracesAction.setProOnly(true);
        try {
            plate = new Plate(plateDoc.getPlate().toXML());
        } catch (XMLSerializationException e) {
            throw new RuntimeException("Could not serialise the plate!",e);
        }
        this.plateView = new PlateView(plate, false);
        zoomInAction = new GeneiousAction("", "Zoom in", IconUtilities.getIcons("zoomin.png")) {
            public void actionPerformed(ActionEvent e) {
                plateView.increaseZoom();
                plateView.revalidate();
            }
        };
        fullZoomAction = new GeneiousAction("", "Full Zoom", IconUtilities.getIcons("fullzoom.png")) {
            public void actionPerformed(ActionEvent e) {
                plateView.setDefaultZoom();
                plateView.revalidate();
            }
        };
        zoomOutAction = new GeneiousAction("", "Zoom out", IconUtilities.getIcons("zoomout.png")) {
            public void actionPerformed(ActionEvent e) {
                plateView.decreaseZoom();
                plateView.revalidate();
            }
        };
        saveAction = new GeneiousAction("Save") {
            public void actionPerformed(ActionEvent e) {
                if(!isLocal && !BiocodeService.getInstance().isLoggedIn()) {
                    Dialogs.showMessageDialog("Please log in");
                    return;
                }
                final Plate plate = plateView.getPlate();
                final List<Reaction> allReactionsOnPlate = Arrays.asList(plate.getReactions());

                plateDoc.setPlate(plate);
                annotatedDocument.saveDocument();
                setEnabled(false);
                if(!isLocal) {
                    final ProgressFrame progressFrame = BiocodeUtilities.getBlockingProgressFrame("Saving your plate", container);
                    Runnable runnable = new Runnable() {
                        public void run() {
                            try {
                                boolean errorDetected = false;

                                if (!hasCheckedPlateForErrorsAtLeastOnce && !plateView.isEditted()) {
                                    String reactionValidityCheckResult = allReactionsOnPlate.get(0).areReactionsValid(allReactionsOnPlate, plateView, true);

                                    if (!reactionValidityCheckResult.isEmpty()) {
                                        Dialogs.showMessageDialog(reactionValidityCheckResult);

                                        errorDetected = true;
                                    }

                                    errorDetected = errorDetected || plateView.checkForPlateSpecificErrors();

                                    hasCheckedPlateForErrorsAtLeastOnce = true;
                                }

                                if (!errorDetected) {
                                    BiocodeService.getInstance().savePlate(plateView.getPlate(), progressFrame);
                                }

                                if(plateView.getPlate().getReactionType() == Reaction.Type.CycleSequencing) {
                                    for(Reaction r : plateView.getPlate().getReactions()) {
                                        ((CycleSequencingReaction)r).purgeChromats();
                                    }
                                }
                            } catch (DatabaseServiceException e1) {
                                Dialogs.showMessageDialog("There was an error saving your plate:\n\n"+e1.getMessage());
                                Runnable runnable = new Runnable() {
                                    public void run() {
                                        setEnabled(true);
                                    }
                                };
                                ThreadUtilities.invokeNowOrLater(runnable);
                            } catch(BadDataException e2) {
                                Dialogs.showMessageDialog("You have some errors in your plate:\n\n"+e2.getMessage());
                                Runnable runnable = new Runnable() {
                                    public void run() {
                                        setEnabled(true);
                                    }
                                };
                                ThreadUtilities.invokeNowOrLater(runnable);
                            } finally {
                                progressFrame.setComplete();
                            }
                        }
                    };

                    Runnable updatePanelRunnable = new Runnable() {
                        @Override
                        public void run() {
                            reloadViewer();

                            updatePanelAndReactions(allReactionsOnPlate);
                        }
                    };

                    BiocodeService.block("Saving plate...", plateView, runnable, updatePanelRunnable);
                }
            }
        };
        saveAction.setIcons(StandardIcons.save.getIcons());
        saveAction.setProOnly(true);

        plateView.addSelectionListener(new ListSelectionListener(){
            public void valueChanged(ListSelectionEvent e) {
                updateToolbar(false);
            }
        });

        plateView.addEditListener(new SimpleListener(){
            public void objectChanged() {
                saveAction.setEnabled(true);    
            }
        });

        cocktailCount = new HashMap<Cocktail, Integer>();

        saveAction.setEnabled(false);
        bulkEditAction.setProOnly(true);
        editThermocycleAction.setProOnly(true);
        gelAction.setProOnly(true);
        updateToolbar(false);
    }

    private void reloadViewer() {
        if(container !=null) {
            container.removeAll();
            container.add(getPanel(), BorderLayout.CENTER);   
        }
    }

    public JComponent getComponent() {
        if(container == null) {
            container = new JPanel(new BorderLayout());
            container.setOpaque(true);
            container.setBackground(Color.white);
            reloadViewer();
        }
        return container;
    }

    private void updateCocktailCount() {
        cocktailCount.clear();
        panelMap.clear();
        for(Reaction r : plateView.getPlate().getReactions()) {
            if(r.isEmpty()) {
                continue;
            }
            Cocktail c = r.getCocktail();
            if(c != null && c.getId() > 0) {
                int count = 1;
                Integer existing = cocktailCount.get(c);
                if(existing != null) {
                    count += existing;
                }
                cocktailCount.put(c, count);
            }
        }
    }

    private void updateToolbar(boolean showDialogs) {
        updateThermocycleAction(showDialogs);
        boolean buttonsEnabled = !isLocal;
        if(plateView.getPlate().getReactionType() != Reaction.Type.Extraction) {
            thermocycleAction.setEnabled(buttonsEnabled);
            editThermocycleAction.setEnabled(buttonsEnabled);
            addTracesAction.setEnabled(buttonsEnabled);
        }
        gelAction.setEnabled(buttonsEnabled);
        if (!plateView.getPlate().isDeleted()) {
            editAction.setName(plateView.getSelectedReactions().size() > 0 ? "Edit Selected Wells" : "Edit All Wells");
        }
        else {
            editAction.setEnabled(false);
        }
        bulkEditAction.setEnabled(buttonsEnabled);

        if(plateView.getPlate().isDeleted()) {
            for(Action a : getActionProvider().getOtherActions()) {
                a.setEnabled(false);
            }
            saveAction.setEnabled(false);
        }
    }

    private JScrollPane getScrollPane(Container component) {
        for(Component comp : component.getComponents()) {
            if(comp instanceof JScrollPane) {
                return (JScrollPane)comp;
            }
            else if(comp instanceof Container) {
                JScrollPane scroller = getScrollPane((Container)comp);
                if(scroller != null) {
                    return scroller;
                }
            }
        }
        return null;
    }

    private void updatePanel() {
        keyPanel.removeAll();
        keyPanel.add(getColorKeyPanel(plateView.getPlate().getReaction(0, 0).getBackgroundColorer()));
        if(thingInsideScrollPane != null) {
            keyPanel.invalidate();
            plateView.invalidate();
            thingInsideScrollPane.revalidate();
        }
    }

    private void updatePanelAndReactions(Collection<Reaction> reactions) {
        ReactionUtilities.invalidateFieldWidthCacheOfReactions(reactions);
        updatePanel();
    }

    private void updateThermocycleAction(boolean showDialogs){
        if(plateView.getPlate().getReactionType() == Reaction.Type.Extraction) {
            return;
        }
        List<GeneiousAction> actions = new ArrayList<GeneiousAction>();
        Thermocycle thermocycle = plateView.getPlate().getThermocycle();
        final AtomicReference<String> name = new AtomicReference<String>(thermocycle == null ? "None" : thermocycle.getName());
        if(!BiocodeService.getInstance().isLoggedIn()) {
            if(showDialogs) {
                Dialogs.showMessageDialog("Please log in");
            }
        }
        else {
            switch(plateView.getPlate().getReactionType()) {
                case Extraction:
                    cycles = Collections.emptyList();
                    break;
                case PCR:
                    cycles = BiocodeService.getInstance().getPCRThermocycles();
                    break;
                case CycleSequencing:
                    cycles = BiocodeService.getInstance().getCycleSequencingThermocycles();
                    break;
            }
            if(cycles.size() == 0) {
                thermocycleAction.setEnabled(false);
                return;
            }
            if(!cycles.contains(plateView.getPlate().getThermocycle())) {
                plateView.getPlate().setThermocycle(cycles.get(0));
            }
            for(final Thermocycle tc : cycles) {
                if(tc.getName() == null) {
                    continue;
                }
                GeneiousAction action = new GeneiousAction(tc.getName()) {
                    public void actionPerformed(ActionEvent e) {
                        if(tc.getId() != plateView.getPlate().getThermocycle().getId()) {
                            selectThermocycle(tc);
                        }
                    }
                };
                actions.add(action);
                if(plateView.getPlate().getThermocycle() != null && tc.getId() == plateView.getPlate().getThermocycle().getId()) {
                    name.set(tc.getName());
                }
            }
            actions.add(new GeneiousAction.Divider());
            actions.add(editThermocycleAction);
        }
        thermocycleAction = new GeneiousAction.SubMenu(new GeneiousActionOptions("Thermocycle: "+name.get(), null, BiocodePlugin.getIcons("thermocycle_16.png")), actions);
        thermocycleAction.setProOnly(true);
        Runnable runnable = new Runnable() {
            public void run() {
                for(SimpleListener listener : actionsChangedListeners) {
                    listener.objectChanged();
                }
            }
        };
        ThreadUtilities.invokeNowOrLater(runnable);
    }

    private void selectThermocycle(Thermocycle tc) {
        plateView.getPlate().setThermocycle(tc);
        updateToolbar(true);
        updateThremocycle();
        saveAction.setEnabled(true);
        updatePanel();
    }

    private void updateThremocycle() {
        if(thermocyclePanel != null) {
            thermocyclePanel.removeAll();
            thermocyclePanel.add(getThermocyclePanel(plateView.getPlate()));
            thermocyclePanel.revalidate();
        }
    }

    @Override
    public ActionProvider getActionProvider() {
        return new ActionProvider(){
            @Override
            public GeneiousAction getSaveAction() {
                return saveAction;
            }

            @Override
            public List<GeneiousAction> getOtherActions() {
                List<GeneiousAction> actions = new ArrayList<GeneiousAction>();
                actions.add(zoomInAction);
                actions.add(fullZoomAction);
                actions.add(zoomOutAction);
                if(plateView.getPlate().getReactionType() == Reaction.Type.Extraction) {
                    thermocycleAction = editThermocycleAction = null;
                    actions.addAll(Arrays.asList(editAction,
                            displayAction,
                            gelAction));
                }
                else {
                    actions.addAll(Arrays.asList(
                            thermocycleAction,
                            gelAction,
                            editAction,
                            displayAction
                    ));
                }
                if(plateView.getPlate().getReactionType() == Reaction.Type.CycleSequencing) {
                    actions.addAll(Arrays.asList(
                            addTracesAction,
                            exportPlateAction
                    ));
                }

                actions.add(bulkEditAction);

                return actions;
            }

            @Override
            public void addActionsChangedListener(SimpleListener listener) {
                actionsChangedListeners.add(listener);
            }

            @Override
            public void removeActionsChangedListener(SimpleListener listener) {
                actionsChangedListeners.remove(listener);
            }
        };
    }

    GeneiousAction gelAction = new GeneiousAction("GEL Images", null, BiocodePlugin.getIcons("addImage_16.png")) {
        public void actionPerformed(ActionEvent e) {
            if(!BiocodeService.getInstance().isLoggedIn()) {
                Dialogs.showMessageDialog("Please log in");
                return;
            }
            Runnable updatePanelRunnable = new Runnable() {
                public void run() {
                    List<GelImage> gelimages = GelEditor.editGels(plateView.getPlate(), container);
                    plateView.getPlate().setImages(gelimages);
                    saveAction.setEnabled(true);
                    updatePanel();
                }
            };
            if(!plateView.getPlate().gelImagesHaveBeenDownloaded()) {
                BiocodeService.block("Downloading existing GEL images", container, new Runnable() {
                    public void run() {
                        try {
                            BiocodeService.getInstance().getActiveLIMSConnection().getGelImagesForPlates(Arrays.asList(plateView.getPlate()));
                        } catch (DatabaseServiceException e1) {
                            Dialogs.showMessageDialog(e1.getMessage());
                        }
                    }
                }, updatePanelRunnable);
            } else {
                updatePanelRunnable.run();
            }
        }
    };



    GeneiousAction editThermocycleAction = new GeneiousAction("View/Add Thermocycles", null, StandardIcons.edit.getIcons()){
        public void actionPerformed(ActionEvent e) {
            if(!BiocodeService.getInstance().isLoggedIn()) {
                Dialogs.showMessageDialog("Please log in");
                return;
            }
            ThermocycleEditor editor = new ThermocycleEditor();
            if(!editor.editThermocycles(cycles, container)) {
                return;
            }
            final List<Thermocycle> newThermocycles = editor.getNewThermocycles();
            final List<Thermocycle> deletedThermocycles = editor.getDeletedThermocycles();
            if (newThermocycles.size() > 0 || deletedThermocycles.size() > 0) {
                Runnable runnable = new Runnable() {
                    public void run() {
                        ProgressFrame progressFrame = BiocodeUtilities.getBlockingProgressFrame("Saving Thermocycles", container);
                        try {

                            Thermocycle.Type type;
                            switch (plateView.getPlate().getReactionType()) {
                                case PCR:
                                    type = Thermocycle.Type.pcr;
                                    break;
                                case CycleSequencing:
                                    type = Thermocycle.Type.cyclesequencing;
                                    break;
                                default:
                                    assert false : "Extractions do not have thermocycles!";
                                    return;
                            }
                            if(newThermocycles.size() > 0) {
                                BiocodeService.getInstance().insertThermocycles(newThermocycles, type);
                            }
                            if(deletedThermocycles.size() > 0) {
                                BiocodeService.getInstance().removeThermoCycles(deletedThermocycles, type);
                            }
                        } catch (final DatabaseServiceException e1) {
                            e1.printStackTrace();
                            Runnable runnable = new Runnable() {
                                public void run() {
                                    Dialogs.showMessageDialog("Could not save thermocycles to the database: " + e1.getMessage());
                                }
                            };
                            ThreadUtilities.invokeNowOrLater(runnable);
                        }
                        progressFrame.setComplete();
                        updateToolbar(false);
                    }
                };
                new Thread(runnable).start();
            }
        }
    };

    GeneiousAction editAction = new GeneiousAction("Edit All Wells", null, StandardIcons.edit.getIcons()) {
        public void actionPerformed(ActionEvent e) {
            List<Reaction> selectedReactions = plateView.getSelectedReactions();

            if (selectedReactions.isEmpty()) {
                selectedReactions = Arrays.asList(plateView.getPlate().getReactions());
            }

            if (ReactionUtilities.editReactions(selectedReactions, plateView, false, true)) {
                plateView.checkForPlateSpecificErrors();

                updatePanelAndReactions(selectedReactions);

                hasCheckedPlateForErrorsAtLeastOnce = true;

                saveAction.setEnabled(true);
            }
        }
    };

    GeneiousAction displayAction = new GeneiousAction("Display Options", null, IconUtilities.getIcons("monitor16.png")) {
        public void actionPerformed(ActionEvent e) {
            ReactionUtilities.showDisplayDialog(plateView.getPlate(), plateView);
            updatePanel();
        }
    };

    GeneiousAction addTracesAction = new GeneiousAction("Bulk Add Traces", null, IconUtilities.getIcons("chromatogram32.png")) {
        public void actionPerformed(ActionEvent e) {
            String error = ReactionUtilities.bulkLoadChromatograms(plateView.getPlate(), plateView);
            if(error != null) {
                Dialogs.showMessageDialog(error);
                actionPerformed(e);
            }
            saveAction.setEnabled(true);
        }
    };

    GeneiousAction bulkEditAction = new GeneiousAction("Bulk Edit", null, BiocodePlugin.getIcons("bulkEdit_16.png")) {
        public void actionPerformed(ActionEvent e) {
            if(!BiocodeService.getInstance().isLoggedIn()) {
                Dialogs.showMessageDialog(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
                return;
            }
            PlateBulkEditor editor = new PlateBulkEditor(plateView.getPlate());
            List<Reaction> allReactionsOnPlate = Arrays.asList(plateView.getPlate().getReactions());
            if (editor.editPlate(container)) {
                String reactionValidityErrorCheck = allReactionsOnPlate.get(0).areReactionsValid(allReactionsOnPlate, plateView, true);

                if (!reactionValidityErrorCheck.isEmpty()) {
                    Dialogs.showMessageDialog(reactionValidityErrorCheck);
                }

                plateView.checkForPlateSpecificErrors();

                updatePanelAndReactions(allReactionsOnPlate);

                hasCheckedPlateForErrorsAtLeastOnce = true;

                saveAction.setEnabled(true);
            }
        }
    };

    GeneiousAction exportPlateAction = new GeneiousAction("Export Sequencer File", "Generate an input file for ABI sequencers", BiocodePlugin.getIcons("abi_16.png")) {
        public void actionPerformed(ActionEvent e) {
            ReactionUtilities.saveAbiFileFromPlate(plateView.getPlate(), plateView);
        }
    };

    private JPanel getThermocyclePanel(Plate plate) {
        JPanel thermocyclePanel = new JPanel(new BorderLayout());
        thermocyclePanel.setOpaque(false);
        ThermocycleEditor.ThermocycleViewer tViewer = new ThermocycleEditor.ThermocycleViewer(plate.getThermocycle());
        thermocyclePanel.add(tViewer, BorderLayout.CENTER);
        JTextArea notes = new JTextArea();
        notes.setEditable(false);
        notes.setText(plate.getThermocycle().getNotes());
        notes.setWrapStyleWord(true);
        notes.setLineWrap(true);
        thermocyclePanel.add(notes, BorderLayout.SOUTH);
        //thermocyclePanel.setBorder(new OptionsPanel.RoundedLineBorder(plate.getThermocycle().getName(), false));
        JPanel holderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        holderPanel.setBorder(new OptionsPanel.RoundedLineBorder(plate.getThermocycle().getName(), false));
        holderPanel.setOpaque(false);
        holderPanel.add(thermocyclePanel);
        return holderPanel;
    }

    private Map<Cocktail, OptionsPanel> panelMap = new HashMap<Cocktail, OptionsPanel>();

    private OptionsPanel getColorKeyPanel(Reaction.BackgroundColorer colorer) {
        OptionsPanel panel = new OptionsPanel();
        for(Map.Entry<String, Color> e : colorer.getColorMap().entrySet()) {
            panel.addSpanningComponent(new ColoringPanel.ColorPanel(e.getKey(), e.getValue(), false), false, GridBagConstraints.WEST);
        }
        return panel;
    }

    private OptionsPanel getCocktailPanel(Map.Entry<Cocktail, Integer> entry) {
        final Cocktail ct = entry.getKey();
        final int count = entry.getValue();
        if(panelMap.get(ct) != null) {
            return panelMap.get(ct);
        }
        final JSpinner fudgeSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 100, 1));
        JPanel fudgePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fudgePanel.setOpaque(false);
        fudgePanel.add(new JLabel("Fudge factor: "));
        fudgePanel.add(fudgeSpinner);
        fudgePanel.add(new JLabel("%"));
        OptionsPanel cockatilPanel = new OptionsPanel();
        cockatilPanel.addSpanningComponent(fudgePanel);
        String extraItem = ct.getOptions().getValueAsString("extraItem");
        for(final Options.Option option : ct.getOptions().getOptions()) {
            if(option.getName().toLowerCase().contains("template")) {
                continue;
            }
            if(option instanceof Options.DoubleOption) {
                int concIndex = option.getName().toLowerCase().indexOf("conc");
                if(concIndex >= 0 && ct.getOptions().getOption(option.getName().substring(0, concIndex)) != null) {
                    continue;
                }
                final Options.DoubleOption doubleOption = (Options.DoubleOption)option;

                String optionLabel = doubleOption.getName();
                if(option.getName().equals("extraItemAmount")) {
                    if(extraItem == null || extraItem.isEmpty()) {
                        continue;
                    }
                    optionLabel = extraItem;
                }
                Options.Option concOption = ct.getOptions().getOption(option.getName()+"Conc");
                if(concOption != null && concOption instanceof Options.DoubleOption){
                    optionLabel = concOption.getValue().toString() + " " + ((Options.DoubleOption)concOption).getUnits() + " " + optionLabel;
                }

                final JLabel label = new JLabel();
                ChangeListener listener = new ChangeListener() {
                    public void stateChanged(ChangeEvent e) {
                        double fudgeFactor = 1 + (((Integer) fudgeSpinner.getValue()) / 100.0);
                        if(option.getName().toLowerCase().contains("conc")) { //just display the concentration, it doesn't add (or need a fudge factor)
                            label.setText(doubleOption.getValue() + " " + doubleOption.getUnits());
                        }
                        else {
                            double vol = fudgeFactor * doubleOption.getValue();
                            double totalVol = vol * count;
                            DecimalFormat format = new DecimalFormat("#0.0"+ " " + doubleOption.getUnits());
                            label.setText(format.format(totalVol));
                        }
                    }
                };
                fudgeSpinner.addChangeListener(listener);
                listener.stateChanged(null);
                label.setOpaque(false);
                cockatilPanel.addComponentWithLabel(optionLabel, label, false);
            }
        }
        final JLabel countLabel = new JLabel();
        final JLabel perReactionCount = new JLabel();
        ChangeListener changeListener = new ChangeListener(){
            public void stateChanged(ChangeEvent e) {
                double fudgeFactor = 1 + (((Integer) fudgeSpinner.getValue()) / 100.0);
                DecimalFormat format = new DecimalFormat("#0.0 " + '\u00B5' + "L");
                double vol = fudgeFactor * ct.getReactionVolume(ct.getOptions());
                double totalVol = vol * count;
                perReactionCount.setText("<html><b>" + format.format(vol) + "</b></html>");
                countLabel.setText("<html><b>" + format.format(totalVol) + "</b></html>");
            }
        };
        fudgeSpinner.addChangeListener(changeListener);
        changeListener.stateChanged(null);
        countLabel.setOpaque(false);
        cockatilPanel.addComponentWithLabel("<html><b>Volume per well</b></html>", perReactionCount, false);
        cockatilPanel.addComponentWithLabel("<html><b>Total volume</b></html>", countLabel, false);
        cockatilPanel.setBorder(new OptionsPanel.RoundedLineBorder(ct.getName(), false));
        panelMap.put(ct, cockatilPanel);
        return cockatilPanel;
    }

    private JPanel getGelImagePanel(GelImage gelImage) {
        final Image img = gelImage.getImage();
        final JPanel imagePanel = getImagePanel(img);
        JPanel holderPanel = new JPanel(new BorderLayout()){  //delegate mouse listeners to the image panel (it's what we want people to click on)
            @Override
            public void addMouseListener(MouseListener l) {
                imagePanel.addMouseListener(l);
            }

            @Override
            public void removeMouseListener(MouseListener l) {
                imagePanel.removeMouseListener(l);
            }

            @Override
            public MouseListener[] getMouseListeners() {
                return imagePanel.getMouseListeners();
            }
        };
        holderPanel.setOpaque(false);
        holderPanel.add(imagePanel, BorderLayout.CENTER);
        JTextArea notes = new JTextArea(gelImage.getNotes());
        notes.setWrapStyleWord(true);
        notes.setLineWrap(true);
        notes.setEditable(false);
        holderPanel.add(notes, BorderLayout.SOUTH);
        return holderPanel;
    }

    private JPanel getImagePanel(final Image img) {
        return new GPanel(){
            @Override
            public Dimension getPreferredSize() {
                if (isPreferredSizeSet()) {
                    return super.getPreferredSize();
                }
                return new Dimension(img.getWidth(this), img.getHeight(this));
            }

            @Override
            protected void paintComponent(Graphics g) {
                //g.setColor(getBackground());
                //g.fillRect(0,0,getWidth(),getHeight());
                double scaleFactor = Double.MAX_VALUE;

                scaleFactor = Math.min(scaleFactor, (double)getWidth()/img.getWidth(this));
                scaleFactor = Math.min(scaleFactor, (double)getHeight()/img.getHeight(this));
                ((Graphics2D)g).scale(scaleFactor, scaleFactor);
                g.drawImage(img, 0, 0, this);
                ((Graphics2D)g).scale(1/scaleFactor, 1/scaleFactor);
            }
        };
    }

    public ExtendedPrintable getExtendedPrintable() {

        return new ExtendedPrintable(){
            private double masterScale = 0.75;

            public int print(Graphics2D graphics, Dimension dimensions, int pageIndex, Options options) throws PrinterException {
                int pages = printAndPagesRequired(graphics, dimensions, pageIndex, options);
                return pages > pageIndex ? Printable.PAGE_EXISTS : Printable.NO_SUCH_PAGE;
            }

            public int getPagesRequired(Dimension dimensions, Options options) {
                return printAndPagesRequired(null, dimensions, -1, options);
            }

            @Override
            public Options getOptions(boolean isSavingToFile) {
                Options o = new Options(this.getClass());
                o.addBooleanOption("colorPlate", "Color the plate", true);
                if(plateView.getPlate().getImages() != null && plateView.getPlate().getImages().size() > 0) {
                    o.addBooleanOption("printImages", "Print GEL Images", true);
                }
                return o;
            }

            public int printAndPagesRequired(Graphics2D g, Dimension dimensions, int pageIndex, Options options) {
                int page = 1;

                //everything is too big by default...
                if(g != null) {
                    g.scale(masterScale, masterScale);
                }
                dimensions = new Dimension((int)(dimensions.width/masterScale), (int)(dimensions.height/masterScale));

                int availableHeight = dimensions.height;
                boolean oldColorBackground = plateView.isColorBackground();
                boolean colorPlate = (Boolean) options.getValue("colorPlate");
                plateView.setColorBackground(colorPlate);
                double scaleFactor = 1;
                scaleFactor = Math.min(scaleFactor, dimensions.getWidth()/plateView.getPreferredSize().width);
                scaleFactor = Math.min(scaleFactor, dimensions.getHeight()/plateView.getPreferredSize().height);
                double requiredPlateHeight = scaleFactor*plateView.getPreferredSize().height;



                if(pageIndex+1 == page && g != null) {
                    int initialSize = 16;
                    //draw the title
                    g.setColor(Color.black);
                    g.setFont(new Font("sans serif", Font.BOLD, initialSize));
                    String plateName = plateDoc.getName();
                    TextLayout layout = new TextLayout(plateName, g.getFont(), g.getFontRenderContext());
                    Rectangle2D layoutBounds = layout.getBounds();

                    //quick hack to try and fit the font to the width of the page...
                    if(layoutBounds.getWidth() > dimensions.width) {
                        g.setFont(new Font("sans serif", Font.BOLD, (int)(initialSize*(dimensions.width/layoutBounds.getWidth()))));
                        layout = new TextLayout(plateName, g.getFont(), g.getFontRenderContext());
                        layoutBounds = layout.getBounds();
                    }

                    g.drawString(plateName, (int)(dimensions.width- layoutBounds.getWidth())/2, layout.getAscent());
                    availableHeight -= layout.getAscent()+5; //5 is for padding...

                    //draw the plate
                    g.translate(0, dimensions.height-availableHeight);
                    g.scale(scaleFactor, scaleFactor);
                    plateView.setBounds(0,0,plateView.getPreferredSize().width, plateView.getPreferredSize().height);
                    plateView.print(g);
                    g.scale(1/scaleFactor, 1/scaleFactor);
                    g.translate(0, availableHeight-dimensions.height);
                }
                plateView.setColorBackground(oldColorBackground);

                availableHeight -= requiredPlateHeight+10;

                int cocktailHeight = 0;
                int cocktailWidth = 0;
                int maxRowHeight = 0;
                updateCocktailCount();
                if(colorPlate) {
                    JPanel panel = getColorKeyPanel(plateView.getPlate().getReaction(0,0).getBackgroundColorer());
                    LayoutData layoutData = layoutComponent(g, dimensions, pageIndex, page, availableHeight, cocktailHeight, cocktailWidth, maxRowHeight, panel);
                    maxRowHeight = layoutData.getMaxRowHeight();
                    availableHeight = layoutData.getAvailableHeight();
                    cocktailWidth = layoutData.getCocktailWidth();
                    page = layoutData.getPage();
                }
                if(cocktailCount.size() > 0) {
                    for(Map.Entry<Cocktail, Integer> entry : cocktailCount.entrySet()) {
                        JPanel cocktailPanel = getCocktailPanel(entry);
                        ((Container)((JScrollPane)cocktailPanel.getComponent(0)).getViewport().getView()).getComponent(0).setVisible(false);
                        LayoutData layoutData = layoutComponent(g, dimensions, pageIndex, page, availableHeight, cocktailHeight, cocktailWidth, maxRowHeight, cocktailPanel);
                        maxRowHeight = layoutData.getMaxRowHeight();
                        availableHeight = layoutData.getAvailableHeight();
                        cocktailWidth = layoutData.getCocktailWidth();
                        page = layoutData.getPage();


                    }
                    availableHeight -= maxRowHeight;

                }

                if(plateView.getPlate().getThermocycle() != null) {
                    int x;
                    int y;


                    JPanel thermocyclePanel = getThermocyclePanel(plateView.getPlate());
                    thermocyclePanel.setBorder(null);
                    thermocyclePanel.setSize(thermocyclePanel.getPreferredSize());

                    if(dimensions.width-cocktailWidth-10 > thermocyclePanel.getPreferredSize().width) {
                        x = ((dimensions.width+cocktailWidth)-thermocyclePanel.getPreferredSize().width)/2;
                        y = dimensions.height-availableHeight-maxRowHeight;
                    }
                    else {
                        x = (dimensions.width-thermocyclePanel.getPreferredSize().width)/2;
                        y = dimensions.height-availableHeight;
                    }

                    if(thermocyclePanel.getPreferredSize().getHeight()+10 > dimensions.height-y) {
                        page++;
                        availableHeight = dimensions.height;
                        x = (dimensions.width-thermocyclePanel.getPreferredSize().width)/2;
                        y = dimensions.height-availableHeight;
                    }
                    if(page == pageIndex+1 && g != null) {
                        thermocyclePanel.setBounds(x, y, thermocyclePanel.getPreferredSize().width, thermocyclePanel.getPreferredSize().height+30);
                        MultiPartDocumentViewerFactory.recursiveDoLayout(thermocyclePanel);
                        thermocyclePanel.validate();
                        thermocyclePanel.invalidate();
                        g.translate(x, y);
                        thermocyclePanel.print(g);
                        g.translate(-x, -y);
                    }
                }

                if(plateView.getPlate().getImages() != null && plateView.getPlate().getImages().size() > 0 && (Boolean)options.getValue("printImages")) {
                    for(GelImage image : plateView.getPlate().getImages()) {
                        JPanel imagePanel = getGelImagePanel(image);

                        Dimension preferredSize = imagePanel.getPreferredSize();
                        if(preferredSize.height > availableHeight) {
                            page++;
                            availableHeight = dimensions.height;
                        }
                        if(preferredSize.width > dimensions.width || imagePanel.getPreferredSize().height > availableHeight) {
                            double imageScaleFactor = 1.0;
                            imageScaleFactor = Math.min(imageScaleFactor,(double)dimensions.width/ preferredSize.width);
                            imageScaleFactor = Math.min(imageScaleFactor, (double)dimensions.height/preferredSize.height);
                            imagePanel.setSize(new Dimension((int)(preferredSize.width*imageScaleFactor), (int)(preferredSize.height*imageScaleFactor)));
                        }
                        else {
                            imagePanel.setSize(preferredSize);
                        }
                        if(pageIndex+1 == page && g != null) {
                            MultiPartDocumentViewerFactory.recursiveDoLayout(imagePanel);
                            g.translate(0, dimensions.height-availableHeight);
                            imagePanel.print(g);
                            g.translate(0, availableHeight-dimensions.height);
                        }
                        availableHeight-=imagePanel.getHeight();
                    }
                }

                if(g != null) {
                    g.scale(1/masterScale, 1/masterScale);
                }
                return page;
            }
        };
    }

    private static class LayoutData {
        private Graphics2D g;
        private Dimension dimensions;
        private int pageIndex;
        private int page;
        private int availableHeight;
        private int cocktailHeight;
        private int cocktailWidth;
        private int maxRowHeight;
        private JPanel cocktailPanel;

        public LayoutData(
                Graphics2D g,
                Dimension dimensions,
                int pageIndex,
                int page,
                int availableHeight,
                int cocktailHeight,
                int cocktailWidth,
                int maxRowHeight,
                JPanel cocktailPanel) {
            this.g = g;
            this.dimensions = dimensions;
            this.pageIndex = pageIndex;
            this.page = page;
            this.availableHeight = availableHeight;
            this.cocktailHeight = cocktailHeight;
            this.cocktailWidth = cocktailWidth;
            this.maxRowHeight = maxRowHeight;
            this.cocktailPanel = cocktailPanel;
        }

        public Graphics2D getG() {
            return g;
        }

        public Dimension getDimensions() {
            return dimensions;
        }

        public int getPageIndex() {
            return pageIndex;
        }

        public int getPage() {
            return page;
        }

        public int getAvailableHeight() {
            return availableHeight;
        }

        public int getCocktailHeight() {
            return cocktailHeight;
        }

        public int getCocktailWidth() {
            return cocktailWidth;
        }

        public int getMaxRowHeight() {
            return maxRowHeight;
        }

        public JPanel getCocktailPanel() {
            return cocktailPanel;
        }
    }

    private static LayoutData layoutComponent(Graphics2D g, Dimension dimensions, int pageIndex, int page, int availableHeight, int cocktailHeight, int cocktailWidth, int maxRowHeight, JPanel cocktailPanel) {
        int widthIncriment = cocktailPanel.getPreferredSize().width + 10;
        int heightIncriment = cocktailPanel.getPreferredSize().height + 10;

        if(cocktailWidth + widthIncriment < dimensions.width) {
            cocktailWidth += widthIncriment;
        }
        else {
            if (cocktailHeight + maxRowHeight > availableHeight) {
                page++;
                cocktailHeight = maxRowHeight;
                availableHeight = dimensions.height;
                cocktailWidth = 0;
            }
            else {
                cocktailWidth = 0;
                cocktailHeight += maxRowHeight;
                availableHeight -= maxRowHeight;
            }
            maxRowHeight = 0;
        }

        maxRowHeight = Math.max(maxRowHeight, heightIncriment);

        if(pageIndex+1 == page && g != null) {
            cocktailPanel.setBounds(0,0, cocktailPanel.getPreferredSize().width, cocktailPanel.getPreferredSize().height);
            MultiPartDocumentViewerFactory.recursiveDoLayout(cocktailPanel);
            cocktailPanel.validate();
            g.translate(cocktailWidth-widthIncriment, dimensions.height - availableHeight + cocktailHeight);
            cocktailPanel.print(g);
            g.translate(-cocktailWidth+widthIncriment, -(dimensions.height - availableHeight + cocktailHeight));
        }
        if(cocktailPanel.getComponentCount() > 0) {
            Container view = (Container) ((JScrollPane) cocktailPanel.getComponent(0)).getViewport().getView();
            if(view.getComponentCount() > 0) {
                view.getComponent(0).setVisible(true);
            }
        }
        return new LayoutData(g, dimensions, pageIndex, page, availableHeight, cocktailHeight, cocktailWidth, maxRowHeight, cocktailPanel);
    }

    public JPanel getPanel() {
        JPanel panel = new JPanel();

        panel.setOpaque(false);

        //the main plate
        final OptionsPanel mainPanel = new OptionsPanel(false, false);
        mainPanel.setOpaque(false);
        mainPanel.addDividerWithLabel("Plate");
        mainPanel.addSpanningComponent(plateView);

        updateCocktailCount();

        keyPanel = new GPanel(new FlowLayout(FlowLayout.LEFT));
        keyPanel.add(getColorKeyPanel(plateView.getPlate().getReaction(0,0).getBackgroundColorer()));
        mainPanel.addSpanningComponent(keyPanel);

        if(cocktailCount.size() > 0) {
            JPanel cocktailHolderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            cocktailHolderPanel.setOpaque(false);
            for(Map.Entry<Cocktail, Integer> entry : cocktailCount.entrySet()) {
                OptionsPanel cockatilPanel = getCocktailPanel(entry);
                cocktailHolderPanel.add(cockatilPanel);
            }



            mainPanel.addDividerWithLabel("Cocktails");
            mainPanel.addSpanningComponent(cocktailHolderPanel);

        }

        if(plateView.getPlate().getThermocycle() != null) {
            thermocyclePanel = new GPanel(new BorderLayout());
            thermocyclePanel.add(getThermocyclePanel(plateView.getPlate()), BorderLayout.CENTER);
            mainPanel.addDividerWithLabel("Thermocycle");
            mainPanel.addSpanningComponent(thermocyclePanel);
        }

        if(plateView.getPlate().getImages() != null && plateView.getPlate().getImages().size() > 0 || (!plateView.getPlate().gelImagesHaveBeenDownloaded())) {
            mainPanel.addDividerWithLabel("GEL Images");
            if(plateView.getPlate().gelImagesHaveBeenDownloaded()) {
                addGelImages(mainPanel);
            }
            else {
                final JButton button = new JButton("Download GEL Images");
                final JPanel buttonHolder = new GPanel(new FlowLayout(FlowLayout.LEFT));
                buttonHolder.add(button);
                mainPanel.addSpanningComponent(buttonHolder);
                button.addActionListener(new ActionListener(){
                    public void actionPerformed(ActionEvent e) {
                        final JLabel loadingLabel = new GLabel("Downloading images...", JLabel.CENTER);
                        AnimatedIcon activityIcon = AnimatedIcon.getActivityIcon();
                        loadingLabel.setIcon(activityIcon);
                        activityIcon.startAnimation();
                        buttonHolder.removeAll();
                        buttonHolder.add(loadingLabel);
                        buttonHolder.revalidate();
                        Thread t = new Thread() {
                            public void run() {
                                try {
                                    BiocodeService.getInstance().getActiveLIMSConnection().getGelImagesForPlates(Arrays.asList(plateView.getPlate()));
                                    buttonHolder.removeAll();
                                    OptionsPanel gelPanel = new OptionsPanel(false, false);
                                    gelPanel.setOpaque(false);
                                    addGelImages(gelPanel);
                                    buttonHolder.setLayout(new BorderLayout());
                                    buttonHolder.add(gelPanel);
                                    buttonHolder.revalidate();
                                } catch (DatabaseServiceException e1) {
                                    buttonHolder.remove(loadingLabel);
                                    buttonHolder.add(new GLabel("Could not load GEL images: "+e1.getMessage(), JLabel.CENTER));
                                    buttonHolder.revalidate();
                                }
                            }
                        };
                        t.start();
                    }
                });
            }
        }

        panel.setLayout(new BorderLayout());
        JScrollPane scroller = new JScrollPane(mainPanel);
        thingInsideScrollPane = mainPanel;
        scroller.setOpaque(false);
        scroller.getViewport().setOpaque(false);
        scroller.getVerticalScrollBar().setUnitIncrement(20);
        scroller.getHorizontalScrollBar().setUnitIncrement(20);
        panel.add(scroller, BorderLayout.CENTER);
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                mainPanel.scrollRectToVisible(new Rectangle(1,1,1,1));
            }
        });
        return panel;
    }

    private void addGelImages(OptionsPanel mainPanel) {
        if(plateView.getPlate().getImages().size() == 0) {
            mainPanel.addSpanningComponent(new GLabel("This plate has no GEL images."));
        }
        for(final GelImage image : plateView.getPlate().getImages()) {
            final JPanel imagePanel = getGelImagePanel(image);
            imagePanel.setToolTipText("Double-click to pop out");
            imagePanel.addMouseListener(new MouseAdapter(){
                JFrame frame;
                @Override
                public void mouseClicked(MouseEvent e) {
                    if(e.getClickCount() == 2) {
                        final JPanel gelImagePanel = getImagePanel(image.getImage());
                        final Dimension originalImageSize = new Dimension(gelImagePanel.getPreferredSize());
                        if(frame == null || !frame.isShowing()) {
                            frame = new JFrame();
                            frame.setTitle("GEL Image");
                            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                            frame.setSize(640, 480);
                            frame.setLocationRelativeTo(imagePanel);
                            final AtomicReference<Double> zoom = new AtomicReference<Double>(1.0);
                            final ZoomPanel zoomer = new ZoomPanel();
                            zoomer.setZoomLevel(zoom.get());
                            zoomer.setMaximumZoomLevel(4.0);
                            frame.getContentPane().add(zoomer, BorderLayout.NORTH);
                            JPanel holder = new JPanel(new FlowLayout());
                            holder.add(gelImagePanel);
                            final JScrollPane scroller = new JScrollPane(holder);
                            frame.getContentPane().add(scroller, BorderLayout.CENTER);

                            frame.addComponentListener(new ComponentAdapter(){
                                @Override
                                public void componentResized(ComponentEvent e) {
                                    boolean isAtMinZoom = MachineAccuracy.same(zoomer.getZoomLevel(),zoomer.getMinZoomLevel());
                                    double newMinZoom = Math.min(1.0, Math.min(scroller.getViewport().getSize().getWidth()/originalImageSize.getWidth(), scroller.getViewport().getSize().getHeight()/originalImageSize.getHeight()));
                                    zoomer.setMinimumAndFitToScreenZoomLevel(newMinZoom);
                                    if(isAtMinZoom) {
                                        zoomer.setZoomLevel(newMinZoom);
                                    }
                                }
                            });

                            zoomer.addChangeListener(new ChangeListener(){
                                public void stateChanged(ChangeEvent e) {
                                    Dimension preferredSize = new Dimension((int) (originalImageSize.width * zoomer.getZoomLevel()), (int) (originalImageSize.height * zoomer.getZoomLevel()));
                                    gelImagePanel.setPreferredSize(preferredSize);
                                    gelImagePanel.invalidate();
                                    scroller.invalidate();
                                    scroller.validate();
                                }
                            });

                            frame.setVisible(true);
                        }
                        else {
                            frame.requestFocus();
                        }
                    }
                }
            });
            mainPanel.addSpanningComponent(imagePanel, false, GridBagConstraints.WEST);
        }
    }
}