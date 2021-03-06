package com.biomatters.plugins.biocode.labbench.plates;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.GComboBox;
import com.biomatters.geneious.publicapi.components.GeneiousActionToolbar;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.plugin.GeneiousAction;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.TestGeneious;
import com.biomatters.geneious.publicapi.utilities.*;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BadDataException;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

/**
 * @author steve
 */
public class PlateViewer extends JPanel {

    private PlateView plateView;
    private PlateViewer selfReference = this;
    private Options.StringOption nameField;
    private JScrollPane scroller;
    private boolean hasCheckedPlateForErrorsAtLeastOnce = false;

    public PlateViewer(int numberOfReactions, Reaction.Type type) {
        plateView = new PlateView(numberOfReactions, type, true);
        init();
    }

    public PlateViewer(Plate.Size size, Reaction.Type type) {
        plateView = new PlateView(size, type, true);
        init();
    }

    private void init() {
        setLayout(new BorderLayout());

        scroller = new JScrollPane(plateView);
        scroller.setBorder(new EmptyBorder(0,0,0,0));
        scroller.getVerticalScrollBar().setUnitIncrement(20);
        scroller.getHorizontalScrollBar().setUnitIncrement(20);

        final JPanel leftToolbar = new JPanel();
        final GeneiousActionToolbar toolbar = new GeneiousActionToolbar(Preferences.userNodeForPackage(this.getClass()), false, true, true);
        //toolbar.setLayout(new FlowLayout(FlowLayout.LEFT));
        leftToolbar.setOpaque(false);

        Options nameOptions = new Options(this.getClass());
        nameField = nameOptions.addStringOption("plateName", "Name:", "");
        nameField.addChangeListener(new SimpleListener(){
            public void objectChanged() {
                plateView.getPlate().setName(nameField.getValue());
            }
        });
        leftToolbar.add(nameOptions.getPanel());

        final GeneiousAction zoomInAction = new GeneiousAction("", "Zoom in", IconUtilities.getIcons("zoomin.png")) {
            public void actionPerformed(ActionEvent e) {
                plateView.increaseZoom();
                plateView.revalidate();
            }
        };
        final GeneiousAction fullZoomAction = new GeneiousAction("", "Full Zoom", IconUtilities.getIcons("fullzoom.png")) {
            public void actionPerformed(ActionEvent e) {
                plateView.setDefaultZoom();
                plateView.revalidate();
            }
        };
        final GeneiousAction zoomOutAction = new GeneiousAction("", "Zoom out", IconUtilities.getIcons("zoomout.png")) {
            public void actionPerformed(ActionEvent e) {
                plateView.decreaseZoom();
                plateView.revalidate();
            }
        };
        toolbar.addAction(zoomInAction);
        toolbar.addAction(fullZoomAction);
        toolbar.addAction(zoomOutAction);

        Reaction.Type reactionType = plateView.getPlate().getReactionType();
        if (reactionType.hasThermocycles()) {
            final DefaultComboBoxModel thermocycleModel = new DefaultComboBoxModel();
            for (Thermocycle cycle : getThermocycles()) {
                thermocycleModel.addElement(cycle);
            }

            final JComboBox thermocycleCombo = new GComboBox(thermocycleModel);
            ItemListener thermocycleComboListener = new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    plateView.getPlate().setThermocycle((Thermocycle)thermocycleCombo.getSelectedItem());
                }
            };
            thermocycleCombo.addItemListener(thermocycleComboListener);
            thermocycleComboListener.itemStateChanged(null);
            leftToolbar.add(new JLabel("Thermocycle: "));
            leftToolbar.add(thermocycleCombo);

            final GeneiousAction thermocycleAction = new GeneiousAction("View/Add Thermocycles", "Create or view Thermocycles", BiocodePlugin.getIcons("thermocycle_16.png")) {
                public void actionPerformed(ActionEvent e) {
                   ThermocycleEditor editor = new ThermocycleEditor();
                    if(!editor.editThermocycles(getThermocycles(), selfReference)) {
                        return;
                    }
                    final List<Thermocycle> newThermocycles = editor.getNewThermocycles();
                    final List<Thermocycle> deletedThermocycles = editor.getDeletedThermocycles();
                    if (newThermocycles.size() > 0 || deletedThermocycles.size() > 0) {
                        Runnable runnable = new Runnable() {
                            public void run() {
                                ProgressFrame progressFrame = BiocodeUtilities.getBlockingProgressFrame("Saving Thermocycles", selfReference);
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


                                Runnable runnable = new Runnable() { //reset the list of thermocycles (so that we know if the user adds more)
                                    public void run() {
                                        thermocycleModel.removeAllElements();
                                        for (Thermocycle cycle : getThermocycles()) {
                                            thermocycleModel.addElement(cycle);
                                        }
                                    }
                                };
                                ThreadUtilities.invokeNowOrWait(runnable);
                                progressFrame.setComplete();
                            }
                        };
                        new Thread(runnable).start();
                    }
                }
            };
            toolbar.addAction(thermocycleAction);

        }
        final GeneiousAction gelAction = new GeneiousAction("Attach GEL Image", "Attach GEL images to this plate", BiocodePlugin.getIcons("addImage_16.png")) {
            public void actionPerformed(ActionEvent e) {
                Runnable updateTask = new Runnable() {
                    public void run() {
                        List<GelImage> gelimages = GelEditor.editGels(plateView.getPlate(), selfReference);
                        plateView.getPlate().setImages(gelimages);
                    }
                };
                if(!plateView.getPlate().gelImagesHaveBeenDownloaded()) {
                    BiocodeService.block("Downloading existing GEL images", selfReference, new Runnable() {
                        public void run() {
                            try {
                                BiocodeService.getInstance().getActiveLIMSConnection().getGelImagesForPlates(Arrays.asList(plateView.getPlate()));
                            } catch (DatabaseServiceException e1) {
                                Dialogs.showMessageDialog(e1.getMessage());
                            }
                        }
                    }, updateTask);
                } else {
                    updateTask.run();
                }
            }
        };
        toolbar.addAction(gelAction);

        //toolbar.addSeparator(new Dimension(1, 24));
        toolbar.addAction(new GeneiousAction.Divider());

        final GeneiousAction bulkEditAction = new GeneiousAction("Bulk Edit", "Paste data into the wells from a spreadsheet", BiocodePlugin.getIcons("bulkEdit_16.png")) {
            public void actionPerformed(ActionEvent e) {
                if (!BiocodeService.getInstance().isLoggedIn()) {
                    Dialogs.showMessageDialog(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
                    return;
                }
                final PlateBulkEditor editor = new PlateBulkEditor(getPlate());
                final List<Reaction> allReactionsOnPlate = Arrays.asList(getPlate().getReactions());
                if (editor.editPlate(selfReference)) {
                    final AtomicReference<String> referenceToReactionsCheckResult = new AtomicReference<String>();

                    Runnable reactionsCheckRunnable = new Runnable() {
                        public void run() {
                            referenceToReactionsCheckResult.set(allReactionsOnPlate.get(0).areReactionsValid(allReactionsOnPlate, plateView, true));
                        }
                    };

                    Runnable postReactionsCheckRunnable = new Runnable() {
                        public void run() {
                            String reactionsCheckResult = referenceToReactionsCheckResult.get();
                            if (!reactionsCheckResult.isEmpty()) {
                                Dialogs.showMessageDialog(reactionsCheckResult);
                            }

                            plateView.checkForPlateSpecificErrors();

                            updatePanelAndReactions(allReactionsOnPlate);

                            hasCheckedPlateForErrorsAtLeastOnce = true;
                        }
                    };

                    BiocodeService.block("Checking reactions...", plateView, reactionsCheckRunnable, postReactionsCheckRunnable);
                }
            }
        };
        toolbar.addAction(bulkEditAction);

        final GeneiousAction bulkChromatAction = new GeneiousAction("Bulk Add Traces", "Import trace files, and attach them to wells", StandardIcons.nucleotide.getIcons()) {
            public void actionPerformed(ActionEvent e) {
                String error = ReactionUtilities.bulkLoadChromatograms(plateView.getPlate(), plateView);
                if (error != null) {
                    Dialogs.showMessageDialog(error);
                    actionPerformed(e);
                }
            }
        };
        if (plateView.getPlate().getReactionType() == Reaction.Type.CycleSequencing) {
            toolbar.addAction(bulkChromatAction);
        }

        final GeneiousAction editAction = new GeneiousAction("Edit All Wells", "", StandardIcons.edit.getIcons()) {
            public void actionPerformed(ActionEvent e) {
                final List<Reaction> reactionsToEdit = plateView.getSelectedReactions();

                if (reactionsToEdit.isEmpty()) {
                    reactionsToEdit.addAll(Arrays.asList(plateView.getPlate().getReactions()));
                }

                final AtomicBoolean okSelected = new AtomicBoolean();

                Runnable editReactionsRunnable = new Runnable() {
                    public void run() {
                        okSelected.set(ReactionUtilities.editReactions(reactionsToEdit, plateView, true, true));
                    }
                };

                Runnable postEditReactionsRunnable = new Runnable() {
                    public void run() {
                        if (okSelected.get()) {
                            plateView.checkForPlateSpecificErrors();

                            updatePanelAndReactions(reactionsToEdit);

                            hasCheckedPlateForErrorsAtLeastOnce = true;
                        }
                    }
                };

                BiocodeService.block("Editing reactions...", plateView, editReactionsRunnable, postEditReactionsRunnable);
            }
        };
        toolbar.addAction(editAction);
        ListSelectionListener toolbarListener = new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                editAction.setName(plateView.getSelectedReactions().size() > 0 ? "Edit Selected Wells" : "Edit All Wells");
            }
        };
        plateView.addSelectionListener(toolbarListener);
        toolbarListener.valueChanged(null);

        GeneiousAction displayAction = new GeneiousAction("Display Options", null, IconUtilities.getIcons("monitor16.png")) {
            public void actionPerformed(ActionEvent e) {
                ReactionUtilities.showDisplayDialog(plateView.getPlate(), plateView);
                updatePanel();
            }
        };
        toolbar.addAction(displayAction);

        //toolbar.addSeparator();
        toolbar.addAction(new GeneiousAction.Divider());
        if(plateView.getPlate().getReactionType() == Reaction.Type.CycleSequencing) {
            final GeneiousAction exportPlateAction = new GeneiousAction("Export Sequencer File") {
                public void actionPerformed(ActionEvent e) {
                    ReactionUtilities.saveAbiFileFromPlate(plateView.getPlate(), plateView);
                }
            };
            toolbar.addAction(exportPlateAction);
        }

        add(scroller, BorderLayout.CENTER);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(leftToolbar, BorderLayout.WEST);
        topPanel.add(toolbar, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);

    }

    public Plate getPlate() {
        return plateView.getPlate();
    }

    private List<Thermocycle> getThermocycles() {
        switch (plateView.getPlate().getReactionType()) {
            case PCR:
                return BiocodeService.getInstance().getPCRThermocycles();
            case CycleSequencing:
                return BiocodeService.getInstance().getCycleSequencingThermocycles();
            default:
                assert false : "Extractions do not have thermocycles!";
                return Collections.EMPTY_LIST;
        }
    }

    public void displayInFrame(final boolean isNew, final Component owner) {
        Runnable runnable = new Runnable() {
            public void run() {
                final JFrame frame = new JFrame();
                frame.getContentPane().setLayout(new BorderLayout());
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        for (Reaction r : plateView.getPlate().getReactions()) {
                            if (!r.isEmpty()) {
                                if (Dialogs.showOkCancelDialog("Your plate has unsaved changes.  Are you sure you want to close this window?", "Unsaved Changes", plateView)) {
                                    break;
                                }
                                else {
                                    return;
                                }
                            }
                        }
                        frame.setVisible(false);
                    }

                });

                frame.setTitle((isNew ? "New " : " ") + plateView.getPlate().getReactionType());
                frame.getContentPane().add(selfReference, BorderLayout.CENTER);
                frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);


                JPanel closeButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                JButton okButton = new JButton("OK");
                JButton cancelButton = new JButton("Cancel");
                if(SystemUtilities.isMac()) {
                    closeButtonPanel.add(cancelButton);
                    closeButtonPanel.add(okButton);
                }
                else {
                    closeButtonPanel.add(okButton);
                    closeButtonPanel.add(cancelButton);
                }
                cancelButton.addActionListener(new ActionListener(){
                    public void actionPerformed(ActionEvent e) {
                        frame.setVisible(false);
                    }
                });
                okButton.addActionListener(new ActionListener(){
                    public void actionPerformed(ActionEvent e) {
                        final Plate plate = plateView.getPlate();
                        final List<Reaction> allReactionsOnPlate = Arrays.asList(plate.getReactions());
                        final ProgressFrame progress = new ProgressFrame("Creating Plate", "", frame);
                        final AtomicBoolean errorDetected = new AtomicBoolean(false);
                        progress.setCancelable(false);
                        progress.setIndeterminateProgress();

                        Runnable runnable = new Runnable() {
                            public void run() {
                                try {
                                    if (!hasCheckedPlateForErrorsAtLeastOnce && !plateView.isEditted()) {
                                        String reactionValidityCheckResult = allReactionsOnPlate.get(0).areReactionsValid(allReactionsOnPlate, plateView, true);

                                        if (!reactionValidityCheckResult.isEmpty()) {
                                            Dialogs.showMessageDialog(reactionValidityCheckResult);

                                            errorDetected.set(true);
                                        }

                                        errorDetected.set(errorDetected.get() || plateView.checkForPlateSpecificErrors());

                                        hasCheckedPlateForErrorsAtLeastOnce = true;
                                    }

                                    if (!errorDetected.get()) {
                                        BiocodeService.getInstance().savePlate(plate, progress);
                                    }
                                } catch(BadDataException ex) {
                                    progress.setComplete();
                                    Dialogs.showMessageDialog("You have some errors in your plate:\n\n" + ex.getMessage(), "Plate Error", frame, Dialogs.DialogIcon.INFORMATION);
                                    errorDetected.set(true);
                                } catch(DatabaseServiceException ex){
                                    ex.printStackTrace();
                                    progress.setComplete();
                                    Dialogs.showMessageDialog("There was an error saving your plate: " + ex.getMessage(), "Plate Error", frame, Dialogs.DialogIcon.INFORMATION);
                                    errorDetected.set(true);
                                } finally {
                                    progress.setComplete();
                                }

                                nameField.getParentOptions().savePreferences();

                                if (!errorDetected.get()) {
                                    frame.dispose();
                                }
                            }
                        };

                        Runnable updatePanelRunnable = new Runnable() {
                            public void run() {
                                updatePanelAndReactions(allReactionsOnPlate);
                            }
                        };

                        BiocodeService.block("Creating plate...", plateView, runnable, updatePanelRunnable);
                    }
                });
                frame.getContentPane().add(closeButtonPanel, BorderLayout.SOUTH);

                frame.pack();
                if(owner != null) {
                    frame.setLocationRelativeTo(owner);
                }
                GuiUtilities.ensureWindowIsOnScreen(frame);
                frame.setVisible(true);
                frame.createBufferStrategy(2);
            }
        };
        ThreadUtilities.invokeNowOrWait(runnable);
    }

    public static void main(String[] args) {
        TestGeneious.initialize();
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException e1) {
            e1.printStackTrace();
        } catch (InstantiationException e1) {
            e1.printStackTrace();
        } catch (IllegalAccessException e1) {
            e1.printStackTrace();
        } catch (UnsupportedLookAndFeelException e1) {
            e1.printStackTrace();
        }

        PlateViewer pView = new PlateViewer(Plate.Size.w96, Reaction.Type.PCR);

        JFrame frame1 = new JFrame();
        frame1.setTitle("Plate View");
        frame1.getContentPane().add(pView);
        frame1.pack();
        frame1.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame1.setVisible(true);
    }


    public void setPlate(Plate plate) {
        plateView.setPlate(plate);
    }

    private void updatePanelAndReactions(Collection<Reaction> reactionsUpdated) {
        ReactionUtilities.clearPreferredSizeForReactions(reactionsUpdated);

        updatePanel();
    }

    private void updatePanel() {
        plateView.invalidate();

        scroller.getViewport().validate();

        plateView.repaint();
    }
}