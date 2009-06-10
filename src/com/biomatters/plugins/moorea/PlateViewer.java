package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.plugin.TestGeneious;
import com.biomatters.geneious.publicapi.plugin.GeneiousAction;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.plugins.moorea.reaction.Reaction;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.ActionEvent;

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

        final GeneiousAction editAction = new GeneiousAction("Edit selected") {
            public void actionPerformed(ActionEvent e) {
                try {
                    plateView.editReactions(plateView.getSelectedReactions());
                } catch (XMLSerializationException e1) {
                    e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
        };
        toolbar.add(editAction);
        ListSelectionListener toolbarListener = new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                JToolBar toolbar2 = toolbar;
                editAction.setEnabled(plateView.getSelectedReactions().size() > 0);
            }
        };
        plateView.addSelectionListener(toolbarListener);
        toolbarListener.valueChanged(null);

        JScrollPane scroller = new JScrollPane(plateView);

        add(scroller, BorderLayout.CENTER);

        add(toolbar, BorderLayout.NORTH);

    }

    public void displayInFrame(final boolean isNew) {
        final JFrame frame = new JFrame();
        Runnable runnable = new Runnable() {
            public void run() {
                frame.getContentPane().setLayout(new BorderLayout());

                frame.setTitle((isNew?"New " : " ")+plateView.getPlate().getReactionType());
                frame.getContentPane().add(selfReference, BorderLayout.CENTER);
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);


                JPanel closeButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                JButton okButton = new JButton("OK");
                JButton cancelButton = new JButton("Cancel");
                closeButtonPanel.add(cancelButton);
                closeButtonPanel.add(okButton);
                frame.getContentPane().add(closeButtonPanel, BorderLayout.SOUTH);

                frame.pack();
                frame.setVisible(true);
            }
        };
        ThreadUtilities.invokeNowOrWait(runnable);

        while(frame.isVisible()){
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
