package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.utilities.IconUtilities;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.border.EmptyBorder;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListDataEvent;
import java.util.*;
import java.util.List;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * User: Steve
 * Date: 17/03/2010
 * Time: 9:58:42 PM
 */
public class SplitPaneListSelector<T> extends JPanel {
    private java.util.List<ListSelectionListener> listSelectionListeners = new ArrayList<ListSelectionListener>();
    private Vector<T> allFieldsVector;
    private Vector<T> availableFieldsVector;
    private Vector<T> selectedFieldsVector;
    private JList availableListBox;
    private JList selectedListBox;

    public SplitPaneListSelector(final Vector<T> allFields, final int[] selectedIndicies, ListCellRenderer renderer) {
        this(allFields,  selectedIndicies, renderer, true);
    }

    public SplitPaneListSelector(final Vector<T> allFields, final int[] selectedIndicies, ListCellRenderer renderer, boolean orderMatters) {
        setLayout(new BorderLayout());
        setOpaque(false);
        allFieldsVector = new Vector<T>(allFields);
        availableFieldsVector = new Vector<T>(allFields);

        this.selectedFieldsVector = new Vector<T>();
        if(selectedIndicies != null) {
            for (int selectedIndicy : selectedIndicies) {
                selectedFieldsVector.add(allFieldsVector.get(selectedIndicy));
            }
        }
        for(T item : selectedFieldsVector) {
            availableFieldsVector.remove(item);
        }
        availableListBox = new JList(availableFieldsVector);
        availableListBox.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        selectedListBox = new JList(selectedFieldsVector);
        if(renderer != null) {
            availableListBox.setCellRenderer(renderer);
            selectedListBox.setCellRenderer(renderer);
        }

        final JButton addButton = new JButton(IconUtilities.getIcons("arrow_right.png").getIcon16());
        addButton.setOpaque(false);
        //noinspection SuspiciousNameCombination
        addButton.setPreferredSize(new Dimension(addButton.getPreferredSize().height, addButton.getPreferredSize().height));
        addButton.setCursor(Cursor.getDefaultCursor());
        final JButton removeButton = new JButton(IconUtilities.getIcons("arrow_left.png").getIcon16());
        removeButton.setOpaque(false);
        removeButton.setCursor(Cursor.getDefaultCursor());
        //noinspection SuspiciousNameCombination
        removeButton.setPreferredSize(new Dimension(removeButton.getPreferredSize().height, removeButton.getPreferredSize().height));

        final JButton moveUpButton = new JButton(IconUtilities.getIcons("arrow_up.png").getIcon16());
        moveUpButton.setOpaque(false);
        //noinspection SuspiciousNameCombination
        moveUpButton.setPreferredSize(new Dimension(moveUpButton.getPreferredSize().height, moveUpButton.getPreferredSize().height));
        final JButton moveDownButton = new JButton(IconUtilities.getIcons("arrow_down.png").getIcon16());
        moveDownButton.setOpaque(false);
        //noinspection SuspiciousNameCombination
        moveDownButton.setPreferredSize(new Dimension(moveDownButton.getPreferredSize().height, moveDownButton.getPreferredSize().height));

        ListSelectionListener selectionListener = new ListSelectionListener(){
            public void valueChanged(ListSelectionEvent e) {
                addButton.setEnabled(availableListBox.getSelectedIndices().length > 0);
                removeButton.setEnabled(selectedListBox.getSelectedIndices().length > 0);
                for(ListSelectionListener listener : listSelectionListeners) {
                    listener.valueChanged(e);
                }
            }
        };
        availableListBox.addListSelectionListener(selectionListener);
        selectedListBox.addListSelectionListener(selectionListener);

        final ActionListener addAction = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int offset = 0;
                int[] indices = availableListBox.getSelectedIndices();
                for (int indice : indices) {
                    int index = indice - offset;
                    selectedFieldsVector.add(availableFieldsVector.get(index));
                    availableFieldsVector.remove(index);
                    offset++;
                }
                availableListBox.clearSelection();
                selectedListBox.clearSelection();
                updateListComponents();
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
                updateListComponents();
            }
        };

        final ActionListener sortUpAction = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                List selectedValues = Arrays.asList(selectedListBox.getSelectedValues());
                Vector<T> newValues = new Vector<T>();
                T current = null;
                for (T currentField : selectedFieldsVector) {
                    if (selectedValues.contains(currentField)) {
                        newValues.add(currentField);
                    } else {
                        if (current != null) {
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
                Vector<T> newValues = new Vector<T>();
                T current = null;
                for(int i=selectedFieldsVector.size()-1; i >= 0; i--) {
                    T currentField = selectedFieldsVector.get(i);
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
        if(orderMatters) {
            movePanel.add(moveUpButton);
            movePanel.add(moveDownButton);
            movePanel.setLayout(new BoxLayout(movePanel, BoxLayout.Y_AXIS));
            movePanel.add(moveUpButton);
            movePanel.add(moveDownButton);
        }

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

        final JSplitPane fieldsSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, availableListBoxPanel, selectedListBoxPanel);
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
        add(fieldsSplit, BorderLayout.CENTER);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                //this doesn't have an effect unless the split pane is showing
                fieldsSplit.setDividerLocation(0.5);
            }
        });
        fieldsSplit.setResizeWeight(0.5);
    }

    private void updateListComponents() {
        for (ListDataListener listener : ((AbstractListModel) availableListBox.getModel()).getListDataListeners()) {
            listener.contentsChanged(new ListDataEvent(availableListBox.getModel(), ListDataEvent.CONTENTS_CHANGED, 0, availableFieldsVector.size() - 1));
        }
        for (ListDataListener listener : ((AbstractListModel) selectedListBox.getModel()).getListDataListeners()) {
            listener.contentsChanged(new ListDataEvent(selectedListBox.getModel(), ListDataEvent.CONTENTS_CHANGED, 0, selectedFieldsVector.size() - 1));
        }
        availableListBox.revalidate();
        selectedListBox.revalidate();
    }

    public Vector<T> getSelectedFields() {
        return new Vector<T>(selectedFieldsVector);
    }


    public void setSelectedFields(Collection<T> fields) {
        availableFieldsVector.removeAllElements();
        availableFieldsVector.addAll(allFieldsVector);
        availableFieldsVector.removeAll(fields);
        selectedFieldsVector.removeAllElements();
        selectedFieldsVector.addAll(fields);
        updateListComponents();
    }


    public void addListSelectionListener(ListSelectionListener listener) {
       listSelectionListeners.add(listener);
    }

    public void removeListSelectionListener(ListSelectionListener listener) {
        listSelectionListeners.remove(listener);
    }


}
