package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.components.GComboBox;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.awt.*;

/**
 * @author Steve
 */
public class ColoringPanel extends DocumentFieldSelectorPanel {
    final List<Reaction> reactions;
    private List<ColorPanel> colorPanels;
    private Reaction.BackgroundColorer originalColorer;
    private static final int MAX_PREFERRED_HEIGHT = 220;
    private List<SimpleListener> changeListeners = new ArrayList<SimpleListener>();

    public ColoringPanel(Vector<DocumentField> availableFieldsVector, List<Reaction> reactions1) {
        super("Color wells based on:", availableFieldsVector);
        this.reactions = reactions1;
        setOpaque(false);

        final Reaction.BackgroundColorer defaultColorer = reactions1.get(0).getDefaultBackgroundColorer();
        originalColorer = getBackgroundColorerForReactions(reactions);

        ItemListener comboBoxListener = new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                DocumentField selectedDocumentField = getDocumentField();
                Collection allValues;
                allValues = ReactionUtilities.getAllValues(selectedDocumentField, reactions);
                JPanel valuesPanel = new JPanel(new GridLayout(allValues.size(),1,5,5));
                valuesPanel.setOpaque(false);
                colorPanels = new ArrayList<ColorPanel>();
                for(Object o : allValues) {
                    Color color = Reaction.BackgroundColorer.getRandomColor(o);
                    if(selectedDocumentField == null) {
                        color = Color.white;
                    }
                    else if(defaultColorer.getDocumentField() != null && selectedDocumentField.getCode().equals(defaultColorer.getDocumentField().getCode())) {
                        color = defaultColorer.getColor(o);
                    }
                    ColorPanel cp = new ColorPanel(o, color, true);
                    cp.addChangeListener(new SimpleListener() {
                        public void objectChanged() {
                            fireChangeListeners();
                        }
                    });
                    cp.setOpaque(false);
                    valuesPanel.add(cp);
                    colorPanels.add(cp);
                }
                boolean originalColorerHasDocumentField = originalColorer != null  && originalColorer.getDocumentField() != null;
                if(selectedDocumentField != null && originalColorerHasDocumentField && originalColorer.getDocumentField().getCode().equals(selectedDocumentField.getCode())) {
                    for(ColorPanel panel : colorPanels) {
                        Color newValue = originalColorer.getColorMap().get(panel.getValue().toString());
                        if(newValue == null) newValue = Color.white;
                        panel.setColor(newValue);
                    }
                }
                if(getComponentCount() > 1) {
                    remove(1);
                }
                JPanel holderPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
                holderPanel.setOpaque(false);
                holderPanel.add(valuesPanel);
                JScrollPane scrollPane = new JScrollPane(holderPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                scrollPane.getVerticalScrollBar().setUnitIncrement(20);
                scrollPane.getViewport().setOpaque(false);
                scrollPane.setOpaque(false);
                if(allValues.size() > 0) {
                    add(scrollPane, BorderLayout.CENTER);
                }
                revalidate();
                fireChangeListeners();
            }
        };
        fieldCombo.addItemListener(comboBoxListener);
        for (int i = 0; i < availableFieldsVector.size(); i++) {
            DocumentField field = availableFieldsVector.get(i);
            if(originalColorer.getDocumentField() != null && field.getCode().equals(originalColorer.getDocumentField().getCode())) {
                fieldCombo.setSelectedIndex(i+1);
                break;
            }
        }
        comboBoxListener.itemStateChanged(null);

    }

    public void addChangeListener(SimpleListener changeListener) {
        changeListeners.add(changeListener);
    }

    public void removeChangeListener(SimpleListener changeListener) {
        changeListeners.remove(changeListener);
    }

    private void fireChangeListeners() {
        for(SimpleListener listener : changeListeners) {
            listener.objectChanged();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension superPreferredSize = super.getPreferredSize();
        if (superPreferredSize.height > MAX_PREFERRED_HEIGHT) {
            return new Dimension(superPreferredSize.width, MAX_PREFERRED_HEIGHT);
        }
        return superPreferredSize;
    }

    public Map<Object, Color> getObjectToColorMap() {
        Map<Object, Color> map = new HashMap<Object, Color>();

        if(colorPanels != null) {
            for(ColorPanel panel : colorPanels) {
                map.put(panel.getValue(), panel.getColor());
            }
        }

        return map;
    }

    public static Reaction.BackgroundColorer getBackgroundColorerForReactions(List<Reaction> reactions) {
        Reaction.BackgroundColorer colorer = null;
        for(Reaction r : reactions) {
            Reaction.BackgroundColorer colorer1 = r.getBackgroundColorer();
            if(colorer != null && !colorer1.equals(colorer)) {
                return r.getDefaultBackgroundColorer();
            }
            colorer = colorer1;
        }
        return colorer;
    }

    public Reaction.BackgroundColorer getColorer() {
        Map<String, Color> colors = new HashMap<String, Color>();
        if(colorPanels != null) {
            for(ColorPanel panel : colorPanels) {
                colors.put(panel.getValue().toString(), panel.getColor());
            }
        }

        DocumentField selectedDocumentField = getDocumentField();
        Reaction.BackgroundColorer newColorer = new Reaction.BackgroundColorer(selectedDocumentField, colors);

        //if we're using the same document field as the default colourer, make sure all possible values are accounted for...
        Reaction.BackgroundColorer defaultColorer = reactions.get(0).getBackgroundColorer();
        if(defaultColorer.getDocumentField() != null && selectedDocumentField != null && selectedDocumentField.getCode().equals(defaultColorer.getDocumentField().getCode())) {
            for(Map.Entry<String, Color> entry : defaultColorer.getColorMap().entrySet()) {
                if(newColorer.getColorMap().get(entry.getKey()) == null) {
                    newColorer.getColorMap().put(entry.getKey(), entry.getValue());
                }
            }
        }

        return newColorer;
    }

    public void setColorer(Reaction.BackgroundColorer colorer) {
        setDocumentField(colorer.getDocumentField());
        Map<String, Color> colorMap = colorer.getColorMap();
        for(ColorPanel panel : colorPanels) {
            Color color = colorMap.get(panel.getValue().toString());
            if(color != null) {
                panel.setColor(color);
            }
        }
        repaint();
    }


    public static class ColorPanel extends JPanel {
        private Object value;
        private Color color;
        private List<SimpleListener> changeListeners = new ArrayList<SimpleListener>();

        public ColorPanel(Object value1, Color color1, boolean editable) {
            this.value = value1;
            this.color = color1;

            setLayout(new BorderLayout());
            setOpaque(false);
            String valueString = value.toString();
            if(valueString.length() == 0) {
                valueString = "<html><i>No value...</i></html>";
            }
            JLabel label = new JLabel(valueString);
            label.setPreferredSize(new Dimension(200, label.getPreferredSize().height));

            final JPanel colorPanel = new JPanel(){
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    g.setColor(color);
                    g.fillRect(0,0,getWidth(),getHeight());
                    g.setColor(SystemColor.control.darker());
                    g.drawRect(0,0,getWidth()-1, getHeight()-1);
                }
            };
            if(editable) {
                colorPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));

                colorPanel.addMouseListener(new MouseAdapter(){
                    @Override
                    public void mouseReleased(MouseEvent e) {
                        Color selectedColor = GuiUtilities.getUserSelectedColor(color, null, "Choose color");
                        if(selectedColor != null) {
                            color = selectedColor;
                            colorPanel.repaint();
                            fireChangeListeners();
                        }
                    }
                });
            }
            colorPanel.setPreferredSize(new Dimension(40, label.getPreferredSize().height));

            add(label, BorderLayout.CENTER);
            add(colorPanel, BorderLayout.EAST);
        }

        public void addChangeListener(SimpleListener listener) {
            changeListeners.add(listener);
        }

        public void removeChangeListener(SimpleListener listener) {
            changeListeners.remove(listener);
        }

        private void fireChangeListeners() {
            for(SimpleListener listener : changeListeners) {
                listener.objectChanged();
            }
        }

        public Object getValue() {
            return value;
        }

        public Color getColor() {
            return color;
        }

        public void setColor(Color c) {
            this.color = c;
        }
    }





}
