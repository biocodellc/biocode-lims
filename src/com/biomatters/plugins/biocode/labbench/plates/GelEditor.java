package com.biomatters.plugins.biocode.labbench.plates;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.OptionsPanel;
import com.biomatters.geneious.publicapi.components.GeneiousActionToolbar;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.geneious.publicapi.plugin.GeneiousAction;
import com.biomatters.geneious.publicapi.plugin.Icons;
import com.biomatters.geneious.publicapi.utilities.StandardIcons;
import com.biomatters.plugins.biocode.labbench.ImagePanel;
import com.biomatters.plugins.biocode.BiocodePlugin;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 12/06/2009 12:09:01 PM
 */
public class GelEditor {
    static Preferences preferences = Preferences.userNodeForPackage(GelEditor.class);

    public static List<GelImage> editGels(final Plate plate, Component owner) {
        final List<GelImage> gelimages = new ArrayList<GelImage>(plate.getImages());
        final JPanel editPanel = new JPanel(new BorderLayout());
        editPanel.setPreferredSize(new Dimension(700, 450));
        final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);
        editPanel.add(splitPane, BorderLayout.CENTER);
        final JList gelimageList = new JList();
        gelimageList.setPrototypeCellValue("Image.jpg");
        final AbstractListModel listModel = new AbstractListModel() {
            public int getSize() {
                return gelimages.size();
            }

            public Object getElementAt(int index) {
                if(index >= gelimages.size()) {
                    return null;
                }
                return gelimages.get(index);
            }
        };
        gelimageList.setCellRenderer(new DefaultListCellRenderer(){
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component superComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if(value instanceof GelImage) {
                    if(superComponent instanceof JLabel) {
                        JLabel label = (JLabel)superComponent;
                        GelImage image = (GelImage)value;
                        label.setText(image.getFilename());
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
        final JButton removeButton = new JButton("Remove");
        buttonPanel.add(removeButton);
        JPanel addRemovePanel = new JPanel(new GridLayout(1,2));
        addRemovePanel.add(addButton);
        addRemovePanel.add(removeButton);
        leftPanel.add(addRemovePanel, BorderLayout.SOUTH);
        leftPanel.add(scroller, BorderLayout.CENTER);
        addButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                FilenameFilter fileFilter = new FilenameFilter(){
                    public boolean accept(File pathname, String name) {
                        name = name.toLowerCase();
                        return name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".tiff") || name.endsWith(".jpeg");
                    }
                };
                File inputFile = FileUtilities.getUserSelectedFile("Open GEL", fileFilter, JFileChooser.FILES_ONLY);
                if(inputFile != null) {
                    try {
                        GelImage newGelimage = new GelImage(-1, inputFile, "");
                        gelimages.add(newGelimage);
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

        final ListSelectionListener selectionListener = new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                GelImage selectedGelimage = (GelImage) gelimageList.getSelectedValue();
                //todo
                setRightComponent(splitPane, getGelViewerPanel(selectedGelimage, plate));
                removeButton.setEnabled(gelimageList.getSelectedIndex() >= 0);
            }
        };

        removeButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                int index = gelimageList.getSelectedIndex();
                if(index < 0) {
                    return;
                }
                File f = new File("c:/temp.jpg");
                try {
                BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(f));
                out.write(gelimages.get(index).getImageBytes());
                out.close();
                }
                catch(Exception ex){}
                gelimages.remove(index);
                for(ListDataListener listener : listModel.getListDataListeners()){
                    listener.contentsChanged(new ListDataEvent(listModel, ListDataEvent.CONTENTS_CHANGED, 0, listModel.getSize()-1));
                }
                selectionListener.valueChanged(null);
            }
        });

        splitPane.setLeftComponent(leftPanel);
        gelimageList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel holderPanel = new JPanel(new GridBagLayout());
        holderPanel.add(new JLabel("<html>No gel images<br>Click 'Add' button below to add gel images</html>"));
        holderPanel.setPreferredSize(new Dimension(250,250));

        setRightComponent(splitPane, holderPanel);

        gelimageList.addListSelectionListener(selectionListener);

        gelimageList.setSelectedIndex(0);

        Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(new String[]{"OK", "Cancel"}, "Edit Gel Images", owner);
        dialogOptions.setMaxWidth(800);
        if(Dialogs.showDialog(dialogOptions, editPanel).equals("OK")) {
            return gelimages;
        }
        return plate.getImages();
    }

    private static JComponent getGelViewerPanel(final GelImage image, final Plate plate) {
        if(image == null) {
            return new JPanel();
        }
        ImagePanel imagePanel = new ImagePanel(image.getImage());
        JScrollPane imageScroller = new JScrollPane(imagePanel);
        final JTextArea notesArea = new JTextArea(image.getNotes());
        notesArea.addKeyListener(new KeyListener() {
            public void keyTyped(KeyEvent e) {
                image.setNotes(notesArea.getText());
            }

            public void keyPressed(KeyEvent e) {
                image.setNotes(notesArea.getText());
            }

            public void keyReleased(KeyEvent e) {
                image.setNotes(notesArea.getText());
            }
        });
        JScrollPane notesScroller = new JScrollPane(notesArea);
        notesScroller.setPreferredSize(new Dimension(100,100));
        notesScroller.setBorder(new OptionsPanel.RoundedLineBorder("Notes", false));
        final JPanel panel = new JPanel(new BorderLayout());
        panel.add(imageScroller, BorderLayout.CENTER);
        panel.add(notesScroller, BorderLayout.SOUTH);
        GeneiousActionToolbar toolbar = new GeneiousActionToolbar(Preferences.userNodeForPackage(GelEditor.class), false, true);
        Icons icon = BiocodePlugin.getIcons("splitgel16.png");
        toolbar.addAction(new GeneiousAction("Split GEL", "Split the GEL into wells, and attach them to your reactions.", icon){
            public void actionPerformed(ActionEvent e) {
                GelSplitter.splitGel(plate, image, panel);
            }
        }).setText("Split GEL");
        toolbar.addAction(new GeneiousAction("Save to disk", "Save the selected GEL image to your computer", StandardIcons.save.getIcons()){
            public void actionPerformed(ActionEvent e) {
                String extension = "";
                int extensionIndex=image.getFilename().lastIndexOf('.');
                if (extensionIndex>0 && extensionIndex < image.getFilename().length())
                    extension = image.getFilename().substring(extensionIndex+1);
                File saveFile = FileUtilities.getUserSelectedSaveFile("Save", "Save GEL", image.getFilename(), extension);
                if(saveFile != null) {
                    OutputStream out = null;
                    try {
                        out = new BufferedOutputStream(new FileOutputStream(saveFile));
                        out.write(image.getImageBytes());
                    } catch(IOException ex) {
                        Dialogs.showMessageDialog("There was a problem saving your GEL: "+ex.getMessage());
                    } finally {
                        if (out!=null) try {
                            out.close();
                        } catch (IOException e1) {
                            //we can't do anything more...
                        }
                    }
                }
            }
        });
        panel.add(toolbar, BorderLayout.NORTH);
        return panel;
    }

    private static void setRightComponent(JSplitPane sp, Component component) {
        int location = sp.getDividerLocation();
        sp.setRightComponent(component);
        sp.setDividerLocation(location);
    }
    
}
