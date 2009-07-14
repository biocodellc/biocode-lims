package com.biomatters.plugins.moorea.plates;

import com.biomatters.geneious.publicapi.plugin.TestGeneious;
import com.biomatters.geneious.publicapi.plugin.GeneiousAction;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.geneious.publicapi.utilities.SystemUtilities;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.GTextField;
import com.biomatters.plugins.moorea.reaction.Reaction;
import com.biomatters.plugins.moorea.reaction.Thermocycle;
import com.biomatters.plugins.moorea.reaction.ThermocycleEditor;
import com.biomatters.plugins.moorea.reaction.ReactionUtilities;
import com.biomatters.plugins.moorea.MooreaLabBenchService;
import com.biomatters.plugins.moorea.TransactionException;
import com.biomatters.plugins.moorea.BadDataException;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
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

    public PlateViewer(int numberOfReactions, Reaction.Type type) {
        plateView = new PlateView(numberOfReactions, type);
        init();
    }

    public PlateViewer(Plate.Size size, Reaction.Type type) {
        plateView = new PlateView(size, type);
        init();
    }

    private void init() {
        setLayout(new BorderLayout());

        final JToolBar toolbar = new JToolBar();
        toolbar.setLayout(new FlowLayout(FlowLayout.LEFT));
        toolbar.setFloatable(false);

        toolbar.add(new JLabel("Name:"));
        final GTextField nameField = new GTextField(20);
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
        toolbar.add(nameField);

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
            toolbar.add(new JLabel("Thermocycle: "));
            toolbar.add(thermocycleCombo);

            final GeneiousAction thermocycleAction = new GeneiousAction("View/Edit Thermocycles") {
                public void actionPerformed(ActionEvent e) {
                    final List<Thermocycle> newThermocycles = ThermocycleEditor.editThermocycles(getThermocycles(), selfReference);
                    if (newThermocycles.size() > 0) {
                        Runnable runnable = new Runnable() {
                            public void run() {
                                MooreaLabBenchService.block("Saving Thermocycles", selfReference);
                                try {
                                    switch (plateView.getPlate().getReactionType()) {
                                        case PCR:
                                            MooreaLabBenchService.getInstance().addPCRThermoCycles(newThermocycles);
                                            break;
                                        case CycleSequencing:
                                            MooreaLabBenchService.getInstance().addCycleSequencingThermoCycles(newThermocycles);
                                            break;
                                        default:
                                            assert false : "Extractions do not have thermocycles!";
                                            break;
                                    }
                                    for (Thermocycle cycle : newThermocycles) {//we're assuming that thermocycles will never be deleted (they shouldn't be)
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
                                MooreaLabBenchService.unBlock();
                            }
                        };
                        new Thread(runnable).start();
                    }
                }
            };
            toolbar.add(thermocycleAction);
        }

        final GeneiousAction gelAction = new GeneiousAction("Attach GEL image") {
            public void actionPerformed(ActionEvent e) {
                List<GelImage> gelImages = GelEditor.editGels(plateView.getPlate().getImages(), selfReference);
                plateView.getPlate().setImages(gelImages);
            }
        };
        toolbar.add(gelAction);


        toolbar.addSeparator(new Dimension(1, 24));

        final GeneiousAction bulkEditAction = new GeneiousAction("Bulk-edit wells") {
            public void actionPerformed(ActionEvent e) {
                PlateBulkEditor.editPlate(plateView.getPlate(), selfReference);
                String error = plateView.getPlate().getReactions()[0].areReactionsValid(Arrays.asList(plateView.getPlate().getReactions()));
                if(error != null && error.length() > 0) {
                    Dialogs.showMessageDialog(error);
                }
            }
        };
        toolbar.add(bulkEditAction);

        final GeneiousAction editAction = new GeneiousAction("Edit selected wells") {
            public void actionPerformed(ActionEvent e) {
                ReactionUtilities.editReactions(plateView.getSelectedReactions(), false, plateView, false, true);
                plateView.revalidate();
                plateView.repaint();
            }
        };
        toolbar.add(editAction);
        ListSelectionListener toolbarListener = new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                editAction.setEnabled(plateView.getSelectedReactions().size() > 0);
            }
        };
        plateView.addSelectionListener(toolbarListener);
        toolbarListener.valueChanged(null);

        JScrollPane scroller = new JScrollPane(plateView);

        add(scroller, BorderLayout.CENTER);

        add(toolbar, BorderLayout.NORTH);

    }

    private List<Thermocycle> getThermocycles() {
        switch (plateView.getPlate().getReactionType()) {
            case PCR:
                return MooreaLabBenchService.getInstance().getPCRThermocycles();
            case CycleSequencing:
                return MooreaLabBenchService.getInstance().getCycleSequencingThermocycles();
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

                        final MooreaLabBenchService.BlockingDialog progress = MooreaLabBenchService.BlockingDialog.getDialog("Creating your plate", frame);

                        Runnable runnable = new Runnable() {
                            public void run() {
                                try {
                                    if(plate.getReactionType() == Reaction.Type.Extraction) {
                                        MooreaLabBenchService.getInstance().saveExtractions(progress, plate);
                                    }
                                    else {
                                        MooreaLabBenchService.getInstance().saveReactions(progress, plate);
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
                        new Thread(runnable, "Moorea plate creation thread").start();
                        progress.setVisible(true);
                    }
                });
                frame.getContentPane().add(closeButtonPanel, BorderLayout.SOUTH);

                frame.pack();
                if(owner != null) {
                    frame.setLocationRelativeTo(owner);
                }
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


}
