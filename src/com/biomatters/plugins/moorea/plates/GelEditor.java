package com.biomatters.plugins.moorea.plates;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.OptionsPanel;
import com.biomatters.plugins.moorea.ImagePanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileFilter;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.prefs.Preferences;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 12/06/2009 12:09:01 PM
 */
public class GelEditor {
    static Preferences preferences = Preferences.userNodeForPackage(GelEditor.class);
    
    public static List<GelImage> editGels(List<GelImage> gels, Component owner) {
        final List<GelImage> gelImages = new ArrayList<GelImage>(gels);
        final JPanel editPanel = new JPanel(new BorderLayout());
        final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        editPanel.add(splitPane, BorderLayout.CENTER);
        final JList gelimageList = new JList();
        gelimageList.setPrototypeCellValue("Image");
        final AbstractListModel listModel = new AbstractListModel() {
            public int getSize() {
                return gelImages.size();
            }

            public Object getElementAt(int index) {
                return gelImages.get(index);
            }
        };
        gelimageList.setCellRenderer(new DefaultListCellRenderer(){
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component superComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if(value instanceof GelImage) {
                    if(superComponent instanceof JLabel) {
                        JLabel label = (JLabel)superComponent;
                        label.setText("Image");
                    }
                }
                return superComponent;
            }
        });
        gelimageList.setModel(listModel);
        JScrollPane scroller = new JScrollPane(gelimageList);
        JPanel leftPanel = new JPanel(new BorderLayout());
        JButton addButton = new JButton("Add");
        JPanel buttonPanel = new JPanel(new GridLayout(1,2));
        buttonPanel.add(addButton);
        JButton removeButton = new JButton("Remove");
        buttonPanel.add(removeButton);
        leftPanel.add(addButton, BorderLayout.SOUTH);
        leftPanel.add(scroller, BorderLayout.CENTER);
        addButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser(preferences.get("fileLocation", System.getProperty("user.home")));
                chooser.setFileFilter(new FileFilter(){
                    public boolean accept(File pathname) {
                        String name = pathname.getName().toLowerCase();
                        return name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".gif");
                    }

                    public String getDescription() {
                        return "Image files (*.png, *.jpg, *.gif)";
                    }
                });
                if(chooser.showDialog(editPanel, "Import") == JFileChooser.APPROVE_OPTION) {
                    preferences.put("filelocation", chooser.getSelectedFile().getParent());
                    try {
                        GelImage newGelimage = new GelImage(-1, chooser.getSelectedFile(), "");
                        gelImages.add(newGelimage);
                        for(ListDataListener listener : listModel.getListDataListeners()){
                            listener.intervalAdded(new ListDataEvent(listModel, ListDataEvent.INTERVAL_ADDED, listModel.getSize(), listModel.getSize()-1));
                        }
                        gelimageList.setSelectedValue(newGelimage, true);
                    } catch (IOException e1) {
                        Dialogs.showMessageDialog("Could not read the image: "+e1.getMessage());
                    }
                }
            }
        });

        removeButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                int index = gelimageList.getSelectedIndex();
                gelImages.remove(index);
                for(ListDataListener listener : listModel.getListDataListeners()){
                    listener.contentsChanged(new ListDataEvent(listModel, ListDataEvent.CONTENTS_CHANGED, 0, listModel.getSize()-1));
                }
            }
        });

        splitPane.setLeftComponent(leftPanel);
        gelimageList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel holderPanel = new JPanel();
        holderPanel.setPreferredSize(new Dimension(250,250));
        
        setRightComponent(splitPane, holderPanel);

        gelimageList.addListSelectionListener(new ListSelectionListener(){
            public void valueChanged(ListSelectionEvent e) {
                GelImage selectedGelimage = (GelImage)gelimageList.getSelectedValue();
                //todo
                setRightComponent(splitPane, getGelViewerPanel(selectedGelimage));
            }
        });

        gelimageList.setSelectedIndex(0);
                
        if(Dialogs.showDialog(new Dialogs.DialogOptions(Dialogs.OK_CANCEL, "Edit Gelimages", owner), editPanel).equals(Dialogs.OK)) {
            return gelImages;
        }
        return gels;
    }

    private static JComponent getGelViewerPanel(final GelImage image) {
        ImagePanel imagePanel = new ImagePanel(image.getImage());
        JScrollPane imageScroller = new JScrollPane(imagePanel);
        final JTextArea notesArea = new JTextArea(image.getNotes());
        notesArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                image.setNotes(notesArea.getText());
            }
        });
        JScrollPane notesScroller = new JScrollPane(notesArea);
        notesScroller.setPreferredSize(new Dimension(100,100));
        notesScroller.setBorder(new OptionsPanel.RoundedLineBorder("Notes", false));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(imageScroller, BorderLayout.CENTER);
        panel.add(notesScroller, BorderLayout.SOUTH);
        return panel;
    }

    private static void setRightComponent(JSplitPane sp, Component component) {
        int location = sp.getDividerLocation();
        sp.setRightComponent(component);
        sp.setDividerLocation(location);
    }
    
}
