package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Steve
 * @version $Id$
 */
public class CocktailEditor<T extends Cocktail> {
    private List<T> newCocktails = new ArrayList<T>();
    private List<T> deletedCocktails = new ArrayList<T>();



    public boolean editCocktails(final List<T> cocktails, final Class<T> cocktailClass, Component owner) {
        JPanel editPanel = new JPanel(new BorderLayout());
        final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        editPanel.add(splitPane, BorderLayout.CENTER);
        final JList cocktailList = new JList();
        cocktailList.setPrototypeCellValue("ACEGIKMOQSUWY13579");
        newCocktails.clear();
        deletedCocktails.clear();
        for(Cocktail ct : cocktails) {
            ct.getOptions().setEnabled(false);
        }
        final AbstractListModel listModel = new AbstractListModel() {
            public int getSize() {
                return cocktails.size()+newCocktails.size();
            }

            public Object getElementAt(int index) {
                if(index < cocktails.size())
                    return cocktails.get(index);
                return newCocktails.get(index-cocktails.size());
            }
        };
        cocktailList.setCellRenderer(new DefaultListCellRenderer(){
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component superComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if(value instanceof Cocktail) {
                    Cocktail valueCocktail = (Cocktail)value;
                    if(superComponent instanceof JLabel) {
                        JLabel label = (JLabel)superComponent;
                        label.setText(valueCocktail.getName());
                    }
                }
                return superComponent;
            }
        });
        cocktailList.setModel(listModel);
        JScrollPane scroller = new JScrollPane(cocktailList);
        JPanel leftPanel = new JPanel(new BorderLayout());
        JButton addButton = new JButton("+");
        final JButton removeButton = new JButton("-");
        GPanel bottomHolder = new GPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        bottomHolder.add(addButton);
        bottomHolder.add(removeButton);
        leftPanel.add(bottomHolder, BorderLayout.SOUTH);
        leftPanel.add(scroller, BorderLayout.CENTER);
        addButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                T newCocktail;
                try {
                    newCocktail = cocktailClass.newInstance();
                } catch (InstantiationException e1) {
                    Dialogs.showMessageDialog("Could not create a new cocktail: "+e1.getMessage());
                    return;
                } catch (IllegalAccessException e1) {
                    Dialogs.showMessageDialog("Could not create a new cocktail: "+e1.getMessage());
                    return;
                }
                if(cocktailList.getSelectedValue() != null) {
                    newCocktail.getOptions().valuesFromXML(((Cocktail)cocktailList.getSelectedValue()).getOptions().valuesToXML("options"));
                    newCocktail.getOptions().setValue("name", "Untitled");
                }
                newCocktails.add(newCocktail);
                for(ListDataListener listener : listModel.getListDataListeners()){
                    listener.intervalAdded(new ListDataEvent(listModel, ListDataEvent.INTERVAL_ADDED, listModel.getSize(), listModel.getSize()-1));
                }
                cocktailList.setSelectedValue(newCocktail, true);
            }
        });

        removeButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                final T selectedValue = (T)cocktailList.getSelectedValue();
                if(selectedValue == null) {
                    return;
                }
                if(listModel.getSize() == 1) {
                    Dialogs.showMessageDialog("You must have at least one cocktail in the database", "Cannot delete thermocycle", removeButton, Dialogs.DialogIcon.NO_ICON);
                    return;
                }
                final AtomicReference<Collection<String>> platesUsing = new AtomicReference<Collection<String>>();
                Runnable backgroundTask = new Runnable() {
                    public void run() {
                        try {
                            platesUsing.set(BiocodeService.getInstance().getPlatesUsingCocktail((T)cocktailList.getSelectedValue()));
                        } catch (SQLException e1) {
                            e1.printStackTrace();
                            Dialogs.showMessageDialog("Could not query database: "+e1.getMessage(), "Could not query database", removeButton, Dialogs.DialogIcon.ERROR);
                        }
                    }
                };
                Runnable updateTask = new Runnable() {
                    public void run() {
                        if(platesUsing.get() == null) { //if an exception was thrown in the above runnable
                            return;
                        }
                        if(platesUsing.get().size() > 0) {
                            if(platesUsing.get().size() > 20) {
                                Dialogs.showMessageDialog("The selected cocktail is in use by reactions on "+platesUsing.get().size()+" plates.  Please remove the reactions/plates or change their cocktails.", "Cannot delete cocktail", removeButton, Dialogs.DialogIcon.NO_ICON);
                                return;
                            }
                            final StringBuilder message = new StringBuilder("The selected cocktail is in use by reactions on the following plates.  Please remove the reactions/plates or change their cocktail.\n\n");

                            for(String name : platesUsing.get()) {
                                message.append("<b>");
                                message.append(name);
                                message.append("</b>\n");
                            }

                            Dialogs.showMessageDialog(message.toString(), "Cannot delete cocktail", removeButton, Dialogs.DialogIcon.NO_ICON);
                            return;
                        }

                        if(selectedValue.getId() >= 0) {
                            deletedCocktails.add(selectedValue);
                            cocktails.remove(selectedValue);
                        }
                        else {
                            newCocktails.remove(selectedValue);
                        }
                        int selectedIndex = cocktailList.getSelectedIndex();
                        for(ListDataListener listener : listModel.getListDataListeners()){
                            listener.intervalRemoved(new ListDataEvent(listModel, ListDataEvent.INTERVAL_REMOVED, selectedIndex, selectedIndex));
                        }
                        cocktailList.setSelectedIndex(Math.max(0, selectedIndex-1));
                    }
                };
                BiocodeService.block("Getting plates from the database", removeButton, backgroundTask, updateTask);
            }
        });

        splitPane.setLeftComponent(leftPanel);
        cocktailList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        cocktailList.addListSelectionListener(new ListSelectionListener(){
            Options previousSelectedOptions = null;
            SimpleListener listener = new SimpleListener(){
                public void objectChanged() {
                    for(ListDataListener listener : listModel.getListDataListeners()){
                        listener.contentsChanged(new ListDataEvent(listModel, ListDataEvent.CONTENTS_CHANGED, 0, listModel.getSize()-1));
                    }
                }
            };
            public void valueChanged(ListSelectionEvent e) {
                if(previousSelectedOptions != null) {
                    previousSelectedOptions.removeChangeListener(listener);
                }
                Cocktail selectedCocktail = (Cocktail)cocktailList.getSelectedValue();
                if(selectedCocktail == null) {
                    setRightComponent(splitPane, new JPanel());    
                }
                else {
                    previousSelectedOptions = selectedCocktail.getOptions();
                    previousSelectedOptions.addChangeListener(listener);
                    JPanel optionsPanel = previousSelectedOptions.getPanel();
                    setRightComponent(splitPane, optionsPanel);
                }
            }
        });

        cocktailList.setSelectedIndex(0);
        Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(new String[] {"OK", "Cancel"}, "Edit Cocktails", owner);
        dialogOptions.setMaxWidth(Integer.MAX_VALUE);
        dialogOptions.setMaxHeight(Integer.MAX_VALUE);

        return Dialogs.showDialog(dialogOptions, editPanel).equals("OK");
    }


    private static void setRightComponent(JSplitPane sp, Component component) {
        int location = sp.getDividerLocation();
        sp.setRightComponent(component);
        sp.setDividerLocation(location);
    }

    public List<T> getNewCocktails() {
        return newCocktails;
    }

    public List<T> getDeletedCocktails() {
        return deletedCocktails;
    }
}
