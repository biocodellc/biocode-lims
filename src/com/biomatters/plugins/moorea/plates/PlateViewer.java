package com.biomatters.plugins.moorea.plates;

import com.biomatters.geneious.publicapi.plugin.TestGeneious;
import com.biomatters.geneious.publicapi.plugin.GeneiousAction;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.geneious.publicapi.utilities.SystemUtilities;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.plugins.moorea.reaction.Reaction;
import com.biomatters.plugins.moorea.reaction.Thermocycle;
import com.biomatters.plugins.moorea.reaction.ThermocycleEditor;
import com.biomatters.plugins.moorea.MooreaLabBenchService;
import com.biomatters.plugins.moorea.TransactionException;
import com.biomatters.plugins.moorea.Workflow;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Collections;
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
            }
        };
        toolbar.add(bulkEditAction);

        final GeneiousAction editAction = new GeneiousAction("Edit selected wells") {
            public void actionPerformed(ActionEvent e) {
                plateView.editReactions(plateView.getSelectedReactions());
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

    public void displayInFrame(final boolean isNew) {
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
                        frame.setVisible(false);
                        Plate plate = plateView.getPlate();


                        try {
                            //create workflows if necessary
                            int workflowCount = 0;
                            for(Reaction reaction : plate.getReactions()) {
                                if(!reaction.isEmpty() && (reaction.getWorkflow() == null || reaction.getWorkflow().getId() < 0)) {
                                    workflowCount++;
                                }
                            }
                            if(workflowCount > 0) {
                                List<Workflow> workflowList = MooreaLabBenchService.getInstance().createWorkflows(workflowCount);
                                int workflowIndex = 0;
                                for(Reaction reaction : plate.getReactions()) {
                                    if(!reaction.isEmpty() && (reaction.getWorkflow() == null || reaction.getWorkflow().getId() < 0)) {
                                        reaction.setWorkflow(workflowList.get(workflowIndex));
                                        workflowIndex++;
                                    }
                                }
                            }


                            if(plate.getId() < 0) { //we need to create the plate
                                MooreaLabBenchService.getInstance().createPlate(plate);
                            }
                            else {
                                MooreaLabBenchService.getInstance().updatePlate(plate);
                            }

                        }
                        catch(SQLException ex){
                            Dialogs.showMessageDialog("There was an error saving your plate: "+ex.getMessage());
                            frame.setVisible(true);
                        }
                        frame.dispose();
                    }
                });
                frame.getContentPane().add(closeButtonPanel, BorderLayout.SOUTH);

                frame.pack();
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
