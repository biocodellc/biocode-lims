package com.biomatters.plugins.biocode.labbench.plates;

import com.biomatters.geneious.publicapi.plugin.TestGeneious;
import com.biomatters.geneious.publicapi.plugin.GeneiousAction;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.geneious.publicapi.utilities.SystemUtilities;
import com.biomatters.geneious.publicapi.utilities.StandardIcons;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.GTextField;
import com.biomatters.geneious.publicapi.components.GeneiousActionToolbar;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.TransactionException;
import com.biomatters.plugins.biocode.labbench.BadDataException;
import com.biomatters.plugins.biocode.BiocodePlugin;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;
import java.sql.SQLException;

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
    private GTextField nameField;

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

        final JPanel leftToolbar = new JPanel();
        final GeneiousActionToolbar toolbar = new GeneiousActionToolbar(Preferences.userNodeForPackage(this.getClass()), false, true, true);
        //toolbar.setLayout(new FlowLayout(FlowLayout.LEFT));
        leftToolbar.setOpaque(false);

        leftToolbar.add(new JLabel("Name:"));
        nameField = new GTextField(20);
        nameField.addKeyListener(new KeyListener(){
            public void keyTyped(KeyEvent e) {
                plateView.getPlate().setName(nameField.getText());
            }

            public void keyPressed(KeyEvent e) {
                plateView.getPlate().setName(nameField.getText());
            }

            public void keyReleased(KeyEvent e) {
                plateView.getPlate().setName(nameField.getText());
            }
        });
        leftToolbar.add(nameField);

        if (plateView.getPlate().getReactionType() != Reaction.Type.Extraction) {
            final DefaultComboBoxModel thermocycleModel = new DefaultComboBoxModel();
            for (Thermocycle cycle : getThermocycles()) {
                thermocycleModel.addElement(cycle);
            }

            final JComboBox thermocycleCombo = new JComboBox(thermocycleModel);
            ItemListener thermocycleComboListener = new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    plateView.getPlate().setThermocycle((Thermocycle) thermocycleCombo.getSelectedItem());
                }
            };
            thermocycleCombo.addItemListener(thermocycleComboListener);
            thermocycleComboListener.itemStateChanged(null);
            leftToolbar.add(new JLabel("Thermocycle: "));
            leftToolbar.add(thermocycleCombo);

            final GeneiousAction thermocycleAction = new GeneiousAction("View/Edit Thermocycles", "Create new Thermocycles, or view existing ones", BiocodePlugin.getIcons("thermocycle_16.png")) {
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

            final GeneiousAction gelAction = new GeneiousAction("Attach GEL image", "Attach GEL images to this plate", BiocodePlugin.getIcons("addImage_16.png")) {
            public void actionPerformed(ActionEvent e) {
                    List<GelImage> gelimages = GelEditor.editGels(plateView.getPlate().getImages(), selfReference);
                    plateView.getPlate().setImages(gelimages);
                }
            };
            toolbar.addAction(gelAction);
        }



        //toolbar.addSeparator(new Dimension(1, 24));
        toolbar.addAction(new GeneiousAction.Divider());

        final GeneiousAction bulkEditAction = new GeneiousAction("Bulk-edit wells", "Paste data into the wells from a spreadsheet", BiocodePlugin.getIcons("bulkEdit_16.png")) {
            public void actionPerformed(ActionEvent e) {
                PlateBulkEditor.editPlate(plateView.getPlate(), selfReference);
                nameField.setText(plateView.getPlate().getName());
                Runnable runnable = new Runnable() {
                    public void run() {
                        String error = plateView.getPlate().getReactions()[0].areReactionsValid(Arrays.asList(plateView.getPlate().getReactions()));
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
        };
        toolbar.addAction(bulkEditAction);

        final GeneiousAction bulkChromatAction = new GeneiousAction("Bulk-add traces", "Import trace files, and attach them to wells", StandardIcons.nucleotide.getIcons()) {
            public void actionPerformed(ActionEvent e) {
                ReactionUtilities.bulkLoadChromatograms(plateView.getPlate(), plateView);
            }
        };
        if(plateView.getPlate().getReactionType() == Reaction.Type.CycleSequencing) {
            toolbar.addAction(bulkChromatAction);
        }

        final GeneiousAction editAction = new GeneiousAction("Edit selected wells", "", StandardIcons.edit.getIcons()) {
            public void actionPerformed(ActionEvent e) {
                ReactionUtilities.editReactions(plateView.getSelectedReactions(), false, plateView, false, true);
                plateView.invalidate();
                scroller.getViewport().validate();
                plateView.repaint();
            }
        };
        toolbar.addAction(editAction);
        ListSelectionListener toolbarListener = new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                editAction.setEnabled(plateView.getSelectedReactions().size() > 0);
            }
        };
        plateView.addSelectionListener(toolbarListener);
        toolbarListener.valueChanged(null);

        //toolbar.addSeparator();
        toolbar.addAction(new GeneiousAction.Divider());
        if(plateView.getPlate().getReactionType() == Reaction.Type.CycleSequencing) {
            final GeneiousAction exportPlateAction = new GeneiousAction("Generate ABI sequencer file") {
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
        final JFrame frame = new JFrame();
        Runnable runnable = new Runnable() {
            public void run() {
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
                                    Dialogs.showMessageDialog("There was an error saving your plate: "+ex.getMessage());
                                    progress.setVisible(false);
                                    return;
                                }
                                progress.setVisible(false);
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

        while (frame.isVisible()) {
            ThreadUtilities.sleep(100);
        }
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
