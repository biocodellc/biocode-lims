package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListDataEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import org.jdom.Element;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 9/06/2009 11:17:43 AM
 */
public abstract class Cocktail implements XMLSerializable {

    public abstract int getId();

    public abstract String getName();

    public abstract Options getOptions();

    protected abstract void setOptions(Options options);

    protected abstract void setId(int id);

    protected abstract void setName(String name);

    public int getReactionVolume(Options options) {
        int sum = 0;
        for (Options.Option o : options.getOptions()) {
            if (o instanceof Options.IntegerOption) {
                sum += (Integer) o.getValue();
            }
        }
        return sum;
    }

    public abstract List<? extends Cocktail> getAllCocktailsOfType();

    public abstract Cocktail createNewCocktail();

    public abstract String getSQLString();

    public static List<? extends Cocktail> editCocktails(final List<? extends Cocktail> cocktails, final Class<? extends Cocktail> cocktailClass, Component owner) {
        JPanel editPanel = new JPanel(new BorderLayout());
        final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        editPanel.add(splitPane, BorderLayout.CENTER);
        final JList cocktailList = new JList();
        cocktailList.setPrototypeCellValue("ACEGIKMOQSUWY13579");
        final List<Cocktail> newCocktails = new ArrayList<Cocktail>();
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
        JButton addButton = new JButton("Add");
        leftPanel.add(addButton, BorderLayout.SOUTH);
        leftPanel.add(scroller, BorderLayout.CENTER);
        addButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                Cocktail newCocktail = null;
                try {
                    newCocktail = cocktailClass.newInstance();
                } catch (InstantiationException e1) {
                    Dialogs.showMessageDialog("Could not create a new cocktail: "+e1.getMessage());
                } catch (IllegalAccessException e1) {
                    Dialogs.showMessageDialog("Could not create a new cocktail: "+e1.getMessage());
                }
                newCocktails.add(newCocktail);
                for(ListDataListener listener : listModel.getListDataListeners()){
                    listener.intervalAdded(new ListDataEvent(listModel, ListDataEvent.INTERVAL_ADDED, listModel.getSize(), listModel.getSize()-1));
                }
                cocktailList.setSelectedValue(newCocktail, true);
            }
        });

        splitPane.setLeftComponent(leftPanel);
        cocktailList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        cocktailList.addListSelectionListener(new ListSelectionListener(){
            public void valueChanged(ListSelectionEvent e) {
                Cocktail selectedCocktail = (Cocktail)cocktailList.getSelectedValue();
                JPanel optionsPanel = selectedCocktail.getOptions().getPanel();
                setRightComponent(splitPane, optionsPanel);
            }
        });

        cocktailList.setSelectedIndex(0);
                
        if(Dialogs.showDialog(new Dialogs.DialogOptions(Dialogs.OK_CANCEL, "Edit Cocktails", owner), editPanel).equals(Dialogs.OK)) {
            return newCocktails;
        }
        return Collections.EMPTY_LIST;

    }

    private static void setRightComponent(JSplitPane sp, Component component) {
        int location = sp.getDividerLocation();
        sp.setRightComponent(component);
        sp.setDividerLocation(location);
    }

    public boolean Equals(Object o) {
        if(o instanceof Cocktail) {
            return ((Cocktail)o).getId() == getId();
        }
        return false;
    }

    public int hashCode() {
        return getId();
    }

    public Element toXML() {
        Element e = new Element("cocktail");
        e.addContent(new Element("name").setText(getName()));
        e.addContent(new Element("id").setText(""+getId()));
        e.addContent(XMLSerializer.classToXML("options", getOptions()));
        return e;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        setOptions(XMLSerializer.classFromXML(element.getChild("options"), Options.class));
        setName(element.getChildText("name"));
        setId(Integer.parseInt(element.getChildText("id")));
    }
}
