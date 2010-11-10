package com.biomatters.plugins.biocode.labbench.plates;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.GComboBox;
import com.biomatters.geneious.publicapi.components.GeneiousActionToolbar;
import com.biomatters.geneious.publicapi.plugin.GeneiousAction;
import com.biomatters.geneious.publicapi.plugin.TestGeneious;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.*;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.labbench.BadDataException;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.TransactionException;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.reaction.ReactionUtilities;
import com.biomatters.plugins.biocode.labbench.reaction.Thermocycle;
import com.biomatters.plugins.biocode.labbench.reaction.ThermocycleEditor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;

import org.virion.jam.util.SimpleListener;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 21/05/2009
 * Time: 6:36:46 AM
 * To change this template use File | Settings | File Templates.
 */
public class PlateViewer extends JPanel {

    private PlateView plateView;
    private PlateViewer selfReference = this;
    private Options.StringOption nameField;

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

        final JScrollPane scroller = new JScrollPane(plateView);
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

        if (plateView.getPlate().getReactionType() != Reaction.Type.Extraction) {
            final DefaultComboBoxModel thermocycleModel = new DefaultComboBoxModel();
            for (Thermocycle cycle : getThermocycles()) {
                thermocycleModel.addElement(cycle);
            }

            final JComboBox thermocycleCombo = new GComboBox(thermocycleModel);
            ItemListener thermocycleComboListener = new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    plateView.getPlate().setThermocycle((Thermocycle) thermocycleCombo.getSelectedItem());
                }
            };
            thermocycleCombo.addItemListener(thermocycleComboListener);
            thermocycleComboListener.itemStateChanged(null);
            leftToolbar.add(new JLabel("Thermocycle: "));
            leftToolbar.add(thermocycleCombo);

            final GeneiousAction thermocycleAction = new GeneiousAction("View/Add Thermocycles", "Create new Thermocycles, or view existing ones", BiocodePlugin.getIcons("thermocycle_16.png")) {
                public void actionPerformed(ActionEvent e) {
                    final List<Thermocycle> newThermocycles = ThermocycleEditor.editThermocycles(getThermocycles(), selfReference);
                    if (newThermocycles.size() > 0) {
                        Runnable runnable = new Runnable() {
                            public void run() {
                                BiocodeService.block("Saving Thermocycles", selfReference);
                                try {
                                    switch (plateView.getPlate().getReactionType()) {
                                        case PCR:
                                            BiocodeService.getInstance().addPCRThermoCycles(newThermocycles);
                                            break;
                                        case CycleSequencing:
                                            BiocodeService.getInstance().addCycleSequencingThermoCycles(newThermocycles);
                                            break;
                                        default:
                                            assert false : "Extractions do not have thermocycles!";
                                            break;
                                    }
                                    thermocycleModel.removeAllElements();
                                    for (Thermocycle cycle : getThermocycles()) {
                                        thermocycleModel.addElement(cycle);
                                    }
                                } catch (final TransactionException e1) {
                                    e1.printStackTrace();
                                    Runnable runnable = new Runnable() {
                                        public void run() {
                                            Dialogs.showMessageDialog("Could not save thermocycles to the database: " + e1.getMessage());
                                        }
                                    };
                                    ThreadUtilities.invokeNowOrLater(runnable);
                                }
                                BiocodeService.unBlock();
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
                if(!plateView.getPlate().gelImagesHaveBeenDownloaded()) {
                    BiocodeService.block("Downloading existing GEL images", selfReference, new Runnable() {
                        public void run() {
                            try {
                                BiocodeService.getInstance().getActiveLIMSConnection().getGelImagesForPlates(Arrays.asList(plateView.getPlate()));
                            } catch (SQLException e1) {
                                Dialogs.showMessageDialog(e1.getMessage());
                            }
                        }
                    });
                }
                List<GelImage> gelimages = GelEditor.editGels(plateView.getPlate(), selfReference);
                plateView.getPlate().setImages(gelimages);
            }
        };
        toolbar.addAction(gelAction);



        //toolbar.addSeparator(new Dimension(1, 24));
        toolbar.addAction(new GeneiousAction.Divider());

        final GeneiousAction bulkEditAction = new GeneiousAction("Bulk Edit", "Paste data into the wells from a spreadsheet", BiocodePlugin.getIcons("bulkEdit_16.png")) {
            public void actionPerformed(ActionEvent e) {
                PlateBulkEditor editor = new PlateBulkEditor(plateView.getPlate(), true);
                if(editor.editPlate(selfReference)) {
                    nameField.setValue(plateView.getPlate().getName());
                    Runnable runnable = new Runnable() {
                        public void run() {
                            String error = plateView.getPlate().getReactions()[0].areReactionsValid(Arrays.asList(plateView.getPlate().getReactions()), plateView, true);
                            if(error != null && error.length() > 0) {
                                Dialogs.showMessageDialog(error);
                            }
                        }
                    };
                    BiocodeService.block("Checking reactions", selfReference, runnable);
                    plateView.invalidate();
                    scroller.getViewport().validate();
                    plateView.repaint();
                }
            }
        };
        toolbar.addAction(bulkEditAction);

        final GeneiousAction bulkChromatAction = new GeneiousAction("Bulk Add Traces", "Import trace files, and attach them to wells", StandardIcons.nucleotide.getIcons()) {
            public void actionPerformed(ActionEvent e) {
                ReactionUtilities.bulkLoadChromatograms(plateView.getPlate(), plateView);
            }
        };
        if(plateView.getPlate().getReactionType() == Reaction.Type.CycleSequencing) {
            toolbar.addAction(bulkChromatAction);
        }

        final GeneiousAction editAction = new GeneiousAction("Edit All Wells", "", StandardIcons.edit.getIcons()) {
            public void actionPerformed(ActionEvent e) {
                List<Reaction> reactions = plateView.getSelectedReactions();
                if (reactions.isEmpty()) {
                    reactions = Arrays.asList(plateView.getPlate().getReactions());
                }
                ReactionUtilities.editReactions(reactions, plateView, true);
                plateView.invalidate();
                scroller.getViewport().validate();
                plateView.repaint();
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
                plateView.invalidate();
                scroller.getViewport().validate();
                plateView.repaint();
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

                frame.setTitle((isNew ? "New " : " ") + plateView.getPlate().getReactionType());
                frame.getContentPane().add(selfReference, BorderLayout.CENTER);
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);


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

                        final BiocodeService.BlockingDialog progress = BiocodeService.BlockingDialog.getDialog("Creating your plate", frame);

                        Runnable runnable = new Runnable() {
                            public void run() {
                                try {
                                    if(plate.getReactionType() == Reaction.Type.Extraction) {
                                        BiocodeService.getInstance().saveExtractions(progress, plate);
                                    }
                                    else {
                                        BiocodeService.getInstance().saveReactions(progress, plate);
                                    }
                                }
                                catch(BadDataException ex) {
                                    Dialogs.showMessageDialog("You have some errors in your plate:\n\n"+ex.getMessage());
                                    progress.setVisible(false);
                                    return;
                                } catch(SQLException ex){
                                    ex.printStackTrace();
                                    Dialogs.showMessageDialog("There was an error saving your plate: "+ex.getMessage());
                                    progress.setVisible(false);
                                    return;
                                }
                                progress.setVisible(false);
                                nameField.getParentOptions().savePreferences();
                                frame.dispose();
                            }
                        };
                        new Thread(runnable, "Biocode plate creation thread").start();
                        progress.setVisible(true);
                    }
                });
                frame.getContentPane().add(closeButtonPanel, BorderLayout.SOUTH);

                frame.pack();
                if(owner != null) {
                    frame.setLocationRelativeTo(owner);
                }
                GuiUtilities.ensureWindowIsOnScreen(frame);
                frame.setVisible(true);
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
}
