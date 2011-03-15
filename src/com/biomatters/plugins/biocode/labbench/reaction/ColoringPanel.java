package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.components.GComboBox;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;

import javax.swing.*;
import java.util.*;
import java.util.List;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;

/**
 * @author Steve
 * @version $Id: 14/01/2010 1:57:43 PM steve $
 */
public class ColoringPanel extends JPanel {
    final Vector<DocumentField> availableFieldsVector;
    final List<Reaction> reactions;
    private List<ColorPanel> colorPanels;
    private DocumentField selectedDocumentField;
    private Reaction.BackgroundColorer originalColorer;
    private static final int MAX_PREFERRED_HEIGHT = 220;
    GComboBox fieldToColor;

    public ColoringPanel(Vector<DocumentField> availableFieldsVector, List<Reaction> reactions1) {
        super(new BorderLayout());
        this.availableFieldsVector = availableFieldsVector;
        this.reactions = reactions1;
        setOpaque(false);
        Vector<ReactionUtilities.DocumentFieldWrapper> cbValues = getDocumentFields();
        fieldToColor = new GComboBox(cbValues);

        
        final Reaction.BackgroundColorer defaultColorer = reactions1.get(0).getDefaultBackgroundColorer();
        originalColorer = getBackgroundColorerForReactions(reactions);

        JPanel cbPanel = new JPanel();
        cbPanel.setOpaque(false);
        JLabel jLabel = new JLabel("Color wells based on");
        jLabel.setOpaque(false);
        cbPanel.add(jLabel);
        cbPanel.add(fieldToColor);
        add(cbPanel, BorderLayout.NORTH);

        ItemListener comboBoxListener = new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                ReactionUtilities.DocumentFieldWrapper wrapper = (ReactionUtilities.DocumentFieldWrapper)fieldToColor.getSelectedItem();
                selectedDocumentField = wrapper.getDocumentField();
                Collection allValues;
                allValues = ReactionUtilities.getAllValues(selectedDocumentField, reactions);
                JPanel valuesPanel = new JPanel(new GridLayout(allValues.size(),1,5,5));
                valuesPanel.setOpaque(false);
                colorPanels = new ArrayList<ColorPanel>();
                for(Object o : allValues) {
                    Color color = Reaction.BackgroundColorer.getRandomColor(o);
                    if(defaultColorer.getDocumentField() != null && selectedDocumentField != null && selectedDocumentField.getCode().equals(defaultColorer.getDocumentField().getCode())) {
                        color = defaultColorer.getColor(o);
                    }
                    ColorPanel cp = new ColorPanel(o, color, true);
                    cp.setOpaque(false);
                    valuesPanel.add(cp);
                    colorPanels.add(cp);
                }
                if(originalColorer != null && selectedDocumentField != null && originalColorer.getDocumentField() != null && originalColorer.getDocumentField().getCode().equals(selectedDocumentField.getCode())) {
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
            }
        };
        fieldToColor.addItemListener(comboBoxListener);
        for (int i = 0; i < availableFieldsVector.size(); i++) {
            DocumentField field = availableFieldsVector.get(i);
            if(originalColorer.getDocumentField() != null && field.getCode().equals(originalColorer.getDocumentField().getCode())) {
                fieldToColor.setSelectedIndex(i+1);
                break;
            }
        }
        comboBoxListener.itemStateChanged(null);

    }

    private Vector<ReactionUtilities.DocumentFieldWrapper> getDocumentFields() {
        Vector<ReactionUtilities.DocumentFieldWrapper> cbValues = new Vector<ReactionUtilities.DocumentFieldWrapper>();
        cbValues.add(new ReactionUtilities.DocumentFieldWrapper(null));
        for(DocumentField field : availableFieldsVector) {
            cbValues.add(new ReactionUtilities.DocumentFieldWrapper(field));
        }
        return cbValues;
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
        final Vector<ReactionUtilities.DocumentFieldWrapper> documentFields = getDocumentFields();
        for(int i=0; i < documentFields.size(); i++) {
            if(colorer.getDocumentField() != null && documentFields.get(i).getDocumentField() != null && colorer.getDocumentField().getCode().equals(documentFields.get(i).getDocumentField().getCode())) {
                fieldToColor.setSelectedIndex(i);
                for(ColorPanel panel : colorPanels) {
                    Color color = colorer.getColorMap().get(panel.getValue().toString());
                    if(color != null) {
                        panel.setColor(color);
                    }
                }
                repaint();
                return;
            }
        }
    }


    public static class ColorPanel extends JPanel {
        private Object value;
        private Color color;

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
                        }
                    }
                });
            }
            colorPanel.setPreferredSize(new Dimension(40, label.getPreferredSize().height));

            add(label, BorderLayout.CENTER);
            add(colorPanel, BorderLayout.EAST);
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
