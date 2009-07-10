package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.components.OptionsPanel;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.plugins.moorea.ButtonOption;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.border.EmptyBorder;
import javax.swing.border.Border;
import javax.swing.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import org.virion.jam.util.SimpleListener;
import org.jdom.Element;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 9/07/2009 6:22:53 PM
 */
public class ReactionUtilities {

    public static void editReactions(List<Reaction> reactions, boolean justEditDisplayableFields, Component owner, boolean justEditOptions) {
        if(reactions == null || reactions.size() == 0) {
            throw new IllegalArgumentException("reactions must be non-null and non-empty");
        }

        Options options = null;
        try {
            options = XMLSerializer.clone(reactions.get(0).getOptions());
        } catch (XMLSerializationException e) {
            //assert false : e.getMessage(); //there's no way I can see that this would happen, so I'm making it an assert
            //e.printStackTrace();
            //options = reactions.get(0).getOptions();
            throw new RuntimeException(e);
        }

        Map<String, Boolean> haveAllSameValues = new HashMap<String, Boolean>();
        //fill in the master options based on the values in all the reactions
        for(Options.Option option : options.getOptions()) {
            haveAllSameValues.put(option.getName(), true);
            for(Reaction reaction : reactions) {
                if(!reaction.getOptions().getValue(option.getName()).equals(option.getValue())) {
                    haveAllSameValues.put(option.getName(), false);
                    continue;
                }
            }
        }

        OptionsPanel displayPanel = getReactionPanel(options, haveAllSameValues);
        Vector<DocumentField> selectedFieldsVector = new Vector<DocumentField>();
        Vector<DocumentField> availableFieldsVector = new Vector<DocumentField>();
        for(Reaction r : reactions) {//todo: may be slow
            List<DocumentField> displayableFields = r.getFieldsToDisplay();
            for(DocumentField df : displayableFields) {
                if(!selectedFieldsVector.contains(df)) {
                    selectedFieldsVector.add(df);
                }
            }
            List<DocumentField> availableFields = r.getAllDisplayableFields();
            for(DocumentField df : availableFields) {
                if(!selectedFieldsVector.contains(df) && !availableFieldsVector.contains(df)) {
                    availableFieldsVector.add(df);
                }
            }
        }


        JPanel fieldsPanel = getFieldsPanel(availableFieldsVector,selectedFieldsVector);
        JComponent componentToDisplay;
        if(justEditDisplayableFields) {
            componentToDisplay = fieldsPanel;
        }
        else if(justEditOptions) {
            componentToDisplay = displayPanel;
        }
        else {
            JTabbedPane tabs = new JTabbedPane();
            tabs.add("Reaction",displayPanel);
            tabs.add("Display", fieldsPanel);
            componentToDisplay = tabs;
        }

        if(Dialogs.showOkCancelDialog(componentToDisplay, "Well Options", owner, Dialogs.DialogIcon.NO_ICON)) {
            if(!justEditDisplayableFields || justEditOptions) {
                Element optionsElement = XMLSerializer.classToXML("options", options);
                        for(Reaction reaction : reactions) {
                            try {
                                reaction.setOptions(XMLSerializer.classFromXML(optionsElement, Options.class));
                            } catch (XMLSerializationException e) {
                                Dialogs.showMessageDialog("Could not save your options: "+e.getMessage());
                            }
                        }
            }
            for(Reaction r : reactions) {
                r.setFieldsToDisplay(new ArrayList<DocumentField>(selectedFieldsVector));
            }
            if(!justEditDisplayableFields || justEditOptions) {
                String error = reactions.get(0).areReactionsValid(reactions);
                if(error != null) {
                    Dialogs.showMessageDialog(error);
                }
            }
        }

    }

    private static OptionsPanel getReactionPanel(Options options, Map<String, Boolean> haveAllSameValues) {
        OptionsPanel displayPanel = new OptionsPanel();
        final JCheckBox selectAllBox = new JCheckBox("<html><b>All</b></html>", false);
        selectAllBox.setOpaque(false);
        final AtomicBoolean selectAllValue = new AtomicBoolean(selectAllBox.isSelected());
        displayPanel.addTwoComponents(selectAllBox, new JLabel(), true, false);
        final List<JCheckBox> checkboxes = new ArrayList<JCheckBox>();
        for(final Options.Option option : options.getOptions()) {
            JComponent leftComponent;
            if(!(option instanceof Options.LabelOption) && !(option instanceof ButtonOption)) {
                final JCheckBox checkbox = new JCheckBox(option.getLabel(), haveAllSameValues.get(option.getName()));
                checkbox.setAlignmentY(JCheckBox.RIGHT_ALIGNMENT);
                checkboxes.add(checkbox);
                ChangeListener listener = new ChangeListener() {
                    public void stateChanged(ChangeEvent e) {
                        option.setEnabled(checkbox.isSelected());
                    }
                };
                checkbox.addChangeListener(listener);
                listener.stateChanged(null);
                leftComponent = checkbox;
            }
            else {
                leftComponent = new JLabel(option.getLabel());
            }
            leftComponent.setOpaque(false);
            selectAllBox.addChangeListener(new ChangeListener(){
                public void stateChanged(ChangeEvent e) {
                    if(selectAllBox.isSelected() != selectAllValue.getAndSet(selectAllBox.isSelected())) {
                        for(JCheckBox cb : checkboxes) {
                            cb.setSelected(selectAllBox.isSelected());
                        }
                    }
                }
            });
            JComponent jComponent = getOptionComponent(option);
            displayPanel.addTwoComponents(leftComponent, jComponent, true, false);

        }
        return displayPanel;
    }

    private static JComponent getOptionComponent(final Options.Option option) {
        JComponent comp = option.getComponent();
        if(option instanceof Options.IntegerOption || option instanceof Options.DoubleOption) {
            String units;
            if(option instanceof Options.IntegerOption) {
                units = ((Options.IntegerOption)option).getUnits();
            }
            else {
                units = ((Options.DoubleOption)option).getUnits();
            }
            if(units.length() > 0) {
                JPanel panel = new JPanel(new BorderLayout(2,0));
                panel.setOpaque(false);
                panel.add(comp, BorderLayout.CENTER);
                final JLabel unitsLabel = new JLabel(units);
                panel.add(unitsLabel, BorderLayout.EAST);
                SimpleListener listener = new SimpleListener() {
                    public void objectChanged() {
                        unitsLabel.setEnabled(option.isEnabled());
                    }
                };
                option.addChangeListener(listener);
                listener.objectChanged();
                comp = panel;
            }
        }
        return comp;
    }

    private static JPanel getFieldsPanel(final Vector<DocumentField> availableFieldsVector, final Vector<DocumentField> selectedFieldsVector) {
        JPanel fieldsPanel = new JPanel(new BorderLayout());

        //List<DocumentField> displayableFields = reactions.get(0).getAllDisplayableFields();
        //final Vector<DocumentField> displayableFieldsVector = new Vector(displayableFields);
        final JList availableListBox = new JList(availableFieldsVector);
        availableListBox.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        final JList selectedListBox = new JList(selectedFieldsVector);

        DefaultListCellRenderer cellRenderer = new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component superComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);    //To change body of overridden methods use File | Settings | File Templates.
                if (superComponent instanceof JLabel) {
                    JLabel label = (JLabel) superComponent;
                    DocumentField field = (DocumentField) value;
                    label.setText(field.getName());
                }
                return superComponent;
            }
        };
        availableListBox.setCellRenderer(cellRenderer);
        selectedListBox.setCellRenderer(cellRenderer);



        final JButton addButton = new JButton(IconUtilities.getIcons("arrow_right.png").getIcon16());
        addButton.setOpaque(false);
        addButton.setPreferredSize(new Dimension(addButton.getPreferredSize().height, addButton.getPreferredSize().height));
        addButton.setCursor(Cursor.getDefaultCursor());
        final JButton removeButton = new JButton(IconUtilities.getIcons("arrow_left.png").getIcon16());
        removeButton.setOpaque(false);
        removeButton.setCursor(Cursor.getDefaultCursor());
        removeButton.setPreferredSize(new Dimension(removeButton.getPreferredSize().height, removeButton.getPreferredSize().height));

        final JButton moveUpButton = new JButton(IconUtilities.getIcons("arrow_up.png").getIcon16());
        moveUpButton.setOpaque(false);
        moveUpButton.setPreferredSize(new Dimension(moveUpButton.getPreferredSize().height, moveUpButton.getPreferredSize().height));
        final JButton moveDownButton = new JButton(IconUtilities.getIcons("arrow_down.png").getIcon16());
        moveDownButton.setOpaque(false);
        moveDownButton.setPreferredSize(new Dimension(moveDownButton.getPreferredSize().height, moveDownButton.getPreferredSize().height));

        ListSelectionListener selectionListener = new ListSelectionListener(){
            public void valueChanged(ListSelectionEvent e) {
                addButton.setEnabled(availableListBox.getSelectedIndices().length > 0);
                removeButton.setEnabled(selectedListBox.getSelectedIndices().length > 0);
            }
        };
        availableListBox.addListSelectionListener(selectionListener);
        selectedListBox.addListSelectionListener(selectionListener);

        final ActionListener addAction = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int offset = 0;
                int[] indices = availableListBox.getSelectedIndices();
                for (int i = 0; i < indices.length; i++) {
                    int index = indices[i]-offset;
                    selectedFieldsVector.add(availableFieldsVector.get(index));
                    availableFieldsVector.remove(index);
                    offset++;
                }
                availableListBox.clearSelection();
                selectedListBox.clearSelection();
                for (ListDataListener listener : ((AbstractListModel) availableListBox.getModel()).getListDataListeners()) {
                    listener.contentsChanged(new ListDataEvent(availableListBox.getModel(), ListDataEvent.CONTENTS_CHANGED, 0, availableFieldsVector.size() - 1));
                }
                for (ListDataListener listener : ((AbstractListModel) selectedListBox.getModel()).getListDataListeners()) {
                    listener.contentsChanged(new ListDataEvent(selectedListBox.getModel(), ListDataEvent.CONTENTS_CHANGED, 0, selectedFieldsVector.size() - 1));
                }
                availableListBox.revalidate();
                selectedListBox.revalidate();
            }
        };

        final ActionListener removeAction = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int offset = 0;
                int[] indices = selectedListBox.getSelectedIndices();
                for (int i = 0; i < indices.length; i++) {
                    int index = indices[i - offset];
                    availableFieldsVector.add(selectedFieldsVector.get(index));
                    selectedFieldsVector.remove(index);
                    offset++;
                }
                selectedListBox.clearSelection();
                availableListBox.clearSelection();
                for (ListDataListener listener : ((AbstractListModel) selectedListBox.getModel()).getListDataListeners()) {
                    listener.contentsChanged(new ListDataEvent(selectedListBox.getModel(), ListDataEvent.CONTENTS_CHANGED, 0, selectedFieldsVector.size() - 1));
                }
                for (ListDataListener listener : ((AbstractListModel) availableListBox.getModel()).getListDataListeners()) {
                    listener.contentsChanged(new ListDataEvent(availableListBox.getModel(), ListDataEvent.CONTENTS_CHANGED, 0, availableFieldsVector.size() - 1));
                }
                availableListBox.revalidate();
                selectedListBox.revalidate();
            }
        };

        final ActionListener sortUpAction = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                List selectedValues = Arrays.asList(selectedListBox.getSelectedValues());
                Vector<DocumentField> newValues = new Vector<DocumentField>();
                DocumentField current = null;
                for(int i=0; i < selectedFieldsVector.size(); i++) {
                    DocumentField currentField = selectedFieldsVector.get(i);
                    if(selectedValues.contains(currentField)) {
                        newValues.add(currentField);
                    }
                    else {
                        if(current != null) {
                            newValues.add(current);
                        }
                        current = currentField;
                    }
                }
                if(current != null) {
                    newValues.add(current);
                }
                selectedFieldsVector.clear();
                selectedFieldsVector.addAll(newValues);
                for (ListDataListener listener : ((AbstractListModel) selectedListBox.getModel()).getListDataListeners()) {
                    listener.contentsChanged(new ListDataEvent(selectedListBox.getModel(), ListDataEvent.CONTENTS_CHANGED, 0, selectedFieldsVector.size() - 1));
                }
                selectedListBox.revalidate();
                int[] indices = selectedListBox.getSelectedIndices();
                boolean contiguousFirstBlock = true;
                for(int i=0; i < indices.length; i++) {
                    if(contiguousFirstBlock && indices[i] == i) {
                        continue;
                    }
                    contiguousFirstBlock = false;
                    indices[i] --;
                }
                selectedListBox.setSelectedIndices(indices);

            }
        };

        final ActionListener sortDownAction = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                List selectedValues = Arrays.asList(selectedListBox.getSelectedValues());
                Vector<DocumentField> newValues = new Vector<DocumentField>();
                DocumentField current = null;
                for(int i=selectedFieldsVector.size()-1; i >= 0; i--) {
                    DocumentField currentField = selectedFieldsVector.get(i);
                    if(selectedValues.contains(currentField)) {
                        newValues.add(0,currentField);
                    }
                    else {
                        if(current != null) {
                            newValues.add(0,current);
                        }
                        current = currentField;
                    }
                }
                if(current != null) {
                    newValues.add(0,current);
                }
                selectedFieldsVector.clear();
                selectedFieldsVector.addAll(newValues);
                for (ListDataListener listener : ((AbstractListModel) selectedListBox.getModel()).getListDataListeners()) {
                    listener.contentsChanged(new ListDataEvent(selectedListBox.getModel(), ListDataEvent.CONTENTS_CHANGED, 0, selectedFieldsVector.size() - 1));
                }
                selectedListBox.revalidate();
                int[] indices = selectedListBox.getSelectedIndices();
                boolean contiguousFirstBlock = true;
                for(int i=0; i < indices.length; i++) {
                    if(contiguousFirstBlock && indices[indices.length-1-i] == selectedFieldsVector.size()-1-i) {
                        continue;
                    }
                    contiguousFirstBlock = false;
                    indices[indices.length-1-i] ++;
                }
                selectedListBox.setSelectedIndices(indices);

            }
        };

        removeButton.addActionListener(removeAction);
        addButton.addActionListener(addAction);

        moveUpButton.addActionListener(sortUpAction);
        moveDownButton.addActionListener(sortDownAction);

        availableListBox.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseClicked(MouseEvent e) {
                if(e.getClickCount() == 2) {
                    addAction.actionPerformed(null);
                }
            }
        });

        selectedListBox.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseClicked(MouseEvent e) {
                if(e.getClickCount() == 2) {
                    removeAction.actionPerformed(null);
                }
            }
        });

        final JPanel addRemovePanel = new JPanel(new GridLayout(2,1));
        addRemovePanel.add(addButton);
        addRemovePanel.add(removeButton);
        addRemovePanel.setMaximumSize(addRemovePanel.getPreferredSize());
        addRemovePanel.setMinimumSize(addRemovePanel.getPreferredSize());
        addRemovePanel.setOpaque(false);

        final JPanel movePanel = new JPanel();
        movePanel.setOpaque(false);
        movePanel.add(moveUpButton);
        movePanel.add(moveDownButton);
        movePanel.setLayout(new BoxLayout(movePanel, BoxLayout.Y_AXIS));
        movePanel.add(moveUpButton);
        movePanel.add(moveDownButton);

        JPanel availableListBoxPanel = new JPanel(new BorderLayout());
        availableListBoxPanel.add(new JScrollPane(availableListBox), BorderLayout.CENTER);
        JLabel label1 = new JLabel("Available");
        label1.setOpaque(false);
        availableListBoxPanel.add(label1, BorderLayout.NORTH);
        availableListBoxPanel.setOpaque(false);

        JPanel selectedListBoxPanel = new JPanel(new BorderLayout());
        selectedListBoxPanel.add(new JScrollPane(selectedListBox), BorderLayout.CENTER);
        selectedListBoxPanel.add(movePanel, BorderLayout.EAST);
        JLabel label2 = new JLabel("Selected");
        label2.setOpaque(false);
        selectedListBoxPanel.add(label2, BorderLayout.NORTH);
        selectedListBoxPanel.setOpaque(false);

        JSplitPane fieldsSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, availableListBoxPanel, selectedListBoxPanel);
        fieldsSplit.setBorder(new EmptyBorder(0,0,0,0));
        fieldsSplit.setUI(new BasicSplitPaneUI(){
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {

                BasicSplitPaneDivider divider = new BasicSplitPaneDivider(this){
                    @Override
                    public int getDividerSize() {
                        return addRemovePanel.getPreferredSize().width;
                    }

                    public void setBorder(Border b) {}
                };
                divider.setLayout(new BoxLayout(divider, BoxLayout.X_AXIS));
                divider.add(addRemovePanel);
                return divider;
            }
        });

        fieldsSplit.setOpaque(false);
        fieldsSplit.setContinuousLayout(true);
        fieldsPanel.add(fieldsSplit);
        fieldsPanel.setOpaque(false);
        fieldsPanel.setBorder(new EmptyBorder(10,10,10,10));

        return fieldsPanel;
    }

}
