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
 * Created by IntelliJ IDEA.
 * User: Steve
 * Date: 14/01/2010
 * Time: 1:57:43 PM
 * To change this template use File | Settings | File Templates.
 */
public class ColoringPanel extends JPanel {
    final Vector<DocumentField> availableFieldsVector;
    final List<Reaction> reactions;
    private List<ColorPanel> colorPanels;
    private DocumentField selectedDocumentField;
    private static final int MAX_PREFERRED_HEIGHT = 220;

    public ColoringPanel(Vector<DocumentField> availableFieldsVector, List<Reaction> reactions1) {
        super(new BorderLayout());
        this.availableFieldsVector = availableFieldsVector;
        this.reactions = reactions1;
        setOpaque(false);
        Vector<ReactionUtilities.DocumentFieldWrapper> cbValues = new Vector<ReactionUtilities.DocumentFieldWrapper>();
        cbValues.add(new ReactionUtilities.DocumentFieldWrapper(null));
        for(DocumentField field : availableFieldsVector) {
            cbValues.add(new ReactionUtilities.DocumentFieldWrapper(field));
        }
        final GComboBox comboBox = new GComboBox(cbValues);
        

        JPanel cbPanel = new JPanel();
        cbPanel.setOpaque(false);
        JLabel jLabel = new JLabel("Color wells based on");
        jLabel.setOpaque(false);
        cbPanel.add(jLabel);
        cbPanel.add(comboBox);
        add(cbPanel, BorderLayout.NORTH);

        ItemListener comboBoxListener = new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                ReactionUtilities.DocumentFieldWrapper wrapper = (ReactionUtilities.DocumentFieldWrapper)comboBox.getSelectedItem();
                selectedDocumentField = wrapper.getDocumentField();
                Collection allValues = ReactionUtilities.getAllValues(selectedDocumentField, reactions);
                JPanel valuesPanel = new JPanel(new GridLayout(allValues.size(),1,5,5));
                valuesPanel.setOpaque(false);
                colorPanels = new ArrayList<ColorPanel>();
                for(Object o : allValues) {
                    ColorPanel cp = new ColorPanel(o, Reaction.BackgroundColorer.getRandomColor());
                    cp.setOpaque(false);
                    valuesPanel.add(cp);
                    colorPanels.add(cp);
                }
                if(getComponentCount() > 1) {
                    remove(1);
                }
                JPanel holderPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
                holderPanel.setOpaque(false);
                holderPanel.add(valuesPanel);
                JScrollPane scrollPane = new JScrollPane(holderPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                scrollPane.getVerticalScrollBar().setUnitIncrement(20);
                add(scrollPane, BorderLayout.CENTER);
                validate();
                invalidate();
                validate();
            }
        };
        comboBox.addItemListener(comboBoxListener);
        Reaction.BackgroundColorer colorer = getBackgroundColorerForReactions(reactions);
        for (int i = 0; i < availableFieldsVector.size(); i++) {
            DocumentField field = availableFieldsVector.get(i);
            if(colorer.getDocumentField() != null && field.getCode().equals(colorer.getDocumentField().getCode())) {
                comboBox.setSelectedIndex(i+1);
                break;
            }
        }
        comboBoxListener.itemStateChanged(null);
        if(colorPanels != null) {
            for(ColorPanel panel : colorPanels) {
                Color newValue = colorer.getColorMap().get(panel.getValue().toString());
                if(newValue == null) newValue = Color.white;
                panel.setColor(newValue);
            }
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

        return new Reaction.BackgroundColorer(selectedDocumentField, colors);
    }


    private static class ColorPanel extends JPanel {
        private Object value;
        private Color color;

        public ColorPanel(Object value1, Color color1) {
            this.value = value1;
            this.color = color1;

            setLayout(new BorderLayout());
            setOpaque(false);
            JLabel label = new JLabel(value.toString());
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
            colorPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            colorPanel.setPreferredSize(new Dimension(40, label.getPreferredSize().height));

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
