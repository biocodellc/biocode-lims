package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.OptionsPanel;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.plugins.moorea.reaction.Reaction;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


public class PlateView extends JPanel {
    private int rows;
    private int cols;
    private Reaction[] reactions;
    private Reaction.Type type;
    private PlateSize plateSize;
    private PlateView selfReference = this;


    public enum PlateSize {
        w48,
        w96,
        w384
    }

    public PlateView(int numberOfWells, Reaction.Type type) {
        init(numberOfWells, 1, type);
    }


    public PlateView(PlateSize size, Reaction.Type type) {
        this.type = type;
        switch(size) {
            case w48 :
                init(8, 6, type);
                break;
            case w96 :
                init(8, 12, type);
                break;
            case w384 :
                init(16, 24, type);
        }
    }

    public Reaction.Type getReactionType() {
        return type;
    }

    public PlateSize getPlateSize() {
        return plateSize;
    }


    @Override
    protected void paintComponent(Graphics g1) {
        //long time = System.currentTimeMillis();
        Graphics2D g = (Graphics2D)g1;
        int cellWidth = (getWidth()+1)/cols;
        int cellHeight = (getHeight()+1)/rows;

        g.setColor(getBackground());
        g.fillRect(0,0,cellWidth*cols+1,cellHeight*rows+1);
        Shape clip = g.getClip();


        for(int i=0; i < rows; i++) {
            for(int j = 0; j < cols; j++) {
                final Reaction reaction = reactions[cols*i + j];
                Rectangle reactionBounds = new Rectangle(1+cellWidth * j, 1+cellHeight * i, cellWidth - 1, cellHeight - 1);
                reaction.setBounds(reactionBounds);
                g.clip(reactionBounds);
                reaction.paint(g);
                g.setClip(clip);
            }
        }
        //System.out.println("paintin: "+(System.currentTimeMillis()-time));
    }

    @Override
    public Dimension getPreferredSize() {
        int width = 0;
        int height = 0;


        for(int i=0; i < rows; i++) {
            for(int j = 0; j < cols; j++) {
                height = Math.max(height, reactions[j*i + j].getPreferredSize().height);
                width = Math.max(width, reactions[j*i + j].getPreferredSize().width);
            }
        }


        return new Dimension(1+(width+1)*cols, 1+(height+1)*rows);
    }

    private Point mousePos = new Point(0,0);
    private Boolean[] wasSelected;

    private void init(int rows, int cols, Reaction.Type type) {
        this.rows = rows;
        this.cols = cols;
        this.type = type;

        reactions = new Reaction[rows*cols];

        setBackground(Color.black);

        addMouseListener(new MouseAdapter(){
            @Override
            public void mouseClicked(MouseEvent e) {

                boolean ctrlIsDown = (e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) == MouseEvent.CTRL_DOWN_MASK;
                boolean shiftIsDown = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) == MouseEvent.SHIFT_DOWN_MASK;

                if(mousePos != null && shiftIsDown) {
                    selectRectangle(e);
                    repaint();
                    return;
                }


                if(e.getClickCount() == 1) {
                    //select just the cell the user clicked on
                    for(int i=0; i < reactions.length; i++) {
                        if(reactions[i].getBounds().contains(e.getPoint())) {
                            reactions[i].setSelected(ctrlIsDown ? !reactions[i].isSelected() : true);
                        }
                        else {
                            if(!ctrlIsDown){
                                reactions[i].setSelected(false);
                            }
                        }
                    }
                }
                if(e.getClickCount() == 2) {
                    for(int i=0; i < reactions.length; i++) {
                        if(reactions[i].isSelected()) {
                            try {
                                editReactions(Arrays.asList(reactions[i]));
                            } catch (XMLSerializationException e1) {
                                e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                            }
                            revalidate();
                        }
                    }

                }
                fireSelectionListeners();
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                boolean shiftIsDown = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) == MouseEvent.SHIFT_DOWN_MASK;
                if(!shiftIsDown) {
                    mousePos = e.getPoint();
                }

                wasSelected = new Boolean[reactions.length];
                for(int i=0; i < reactions.length; i++) {
                    wasSelected[i] = reactions[i].isSelected();
                }

            }

            @Override
            public void mouseReleased(MouseEvent e) {
                wasSelected = null;
            }
        });

        addMouseMotionListener(new MouseMotionAdapter(){
            @Override
            public void mouseDragged(MouseEvent e) {
                //long time = System.currentTimeMillis();
                selectRectangle(e);
                long time2 = System.currentTimeMillis();
                repaint();

                //System.out.println("selectin: "+(System.currentTimeMillis()-time));

            }
        });


        for(int i=0; i < rows; i++) {
            for(int j = 0; j < cols; j++) {
                final Reaction reaction = Reaction.getNewReaction(type);
                reaction.setLocationString(""+(char)(65+i)+(1+j));
                Dimension preferredSize = reaction.getPreferredSize();
                reaction.setBounds(new Rectangle(1+(preferredSize.width+1)*j, 1+(preferredSize.height+1)*i, preferredSize.width, preferredSize.height));

                reactions[cols*i + j] = reaction;
            }
        }


    }

    public void editReactions(List<Reaction> reactions) throws XMLSerializationException {
        if(reactions == null || reactions.size() == 0) {
            throw new IllegalArgumentException("reactions must be non-null and non-empty");
        }

        Options options = XMLSerializer.clone(reactions.get(0).getOptions());

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
        JPanel fieldsPanel = getFieldsPanel(reactions);
        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Reaction",displayPanel);
        tabs.add("Display", fieldsPanel);

        if(Dialogs.showOkCancelDialog(tabs, "Well Options", selfReference)) {
            for(final Options.Option option : options.getOptions()) {
                if(option.isEnabled() && !(option instanceof Options.LabelOption)) {
                    for(Reaction reaction : reactions) {
                        reaction.getOptions().setValue(option.getName(), option.getValue());
                    }
                }
            }
        }


    }

    private OptionsPanel getReactionPanel(Options options, Map<String, Boolean> haveAllSameValues) {
        OptionsPanel displayPanel = new OptionsPanel();
        final JCheckBox selectAllBox = new JCheckBox("<html><b>All</b></html>", false);
        final AtomicBoolean selectAllValue = new AtomicBoolean(selectAllBox.isSelected());
        displayPanel.addTwoComponents(selectAllBox, new JLabel(), false, false);
        final List<JCheckBox> checkboxes = new ArrayList<JCheckBox>();
        for(final Options.Option option : options.getOptions()) {
            JComponent leftComponent;
            if(!(option instanceof Options.LabelOption)) {
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
            selectAllBox.addChangeListener(new ChangeListener(){
                public void stateChanged(ChangeEvent e) {
                    if(selectAllBox.isSelected() != selectAllValue.getAndSet(selectAllBox.isSelected())) {
                        for(JCheckBox cb : checkboxes) {
                            cb.setSelected(selectAllBox.isSelected());
                        }
                    }
                }
            });
            displayPanel.addTwoComponents(leftComponent, option.getComponent(), true, false);

        }
        return displayPanel;
    }

    private JPanel getFieldsPanel(List<Reaction> reactions) {
        JPanel fieldsPanel = new JPanel();
        BoxLayout layout = new BoxLayout(fieldsPanel, BoxLayout.LINE_AXIS);
        fieldsPanel.setLayout(layout);

        
        List<DocumentField> displayableFields = reactions.get(0).getAllDisplayableFields();
        final Vector<DocumentField> displayableFieldsVector = new Vector(displayableFields);
        final JList availableListBox = new JList(displayableFieldsVector);
        availableListBox.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        final Vector<DocumentField> selectedFieldsVector = new Vector<DocumentField>();
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



        final JButton addButton = new JButton(" > ");
        final JButton removeButton = new JButton(" < ");

        ListSelectionListener selectionListener = new ListSelectionListener(){
            public void valueChanged(ListSelectionEvent e) {
                addButton.setEnabled(availableListBox.getSelectedIndices().length > 0);
                removeButton.setEnabled(selectedListBox.getSelectedIndices().length > 0);
            }
        };
        availableListBox.addListSelectionListener(selectionListener);
        selectedListBox.addListSelectionListener(selectionListener);

        addButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                int offset = 0;
                int[] indices = availableListBox.getSelectedIndices();
                for(int i=0; i < indices.length; i++) {
                    int index = indices[i - offset];
                    selectedFieldsVector.add(displayableFieldsVector.get(index));
                    displayableFieldsVector.remove(index);
                    offset++;
                }
                availableListBox.clearSelection();
                selectedListBox.clearSelection();
                for(ListDataListener listener : ((AbstractListModel)availableListBox.getModel()).getListDataListeners()) {
                    listener.contentsChanged(new ListDataEvent(availableListBox.getModel(), ListDataEvent.CONTENTS_CHANGED, 0, displayableFieldsVector.size()-1));
                }
                for(ListDataListener listener : ((AbstractListModel)selectedListBox.getModel()).getListDataListeners()) {
                    listener.contentsChanged(new ListDataEvent(selectedListBox.getModel(), ListDataEvent.CONTENTS_CHANGED, 0, selectedFieldsVector.size()-1));
                }
                availableListBox.revalidate();
                selectedListBox.revalidate();
            }
        });

        JPanel addRemovePanel = new JPanel(new GridLayout(2,1));
        addRemovePanel.add(addButton);
        addRemovePanel.add(removeButton);
        addRemovePanel.setMaximumSize(addRemovePanel.getPreferredSize());
        addRemovePanel.setMinimumSize(addRemovePanel.getPreferredSize());

        fieldsPanel.add(new JScrollPane(availableListBox));
        fieldsPanel.add(addRemovePanel);
        fieldsPanel.add(new JScrollPane(selectedListBox));



        return fieldsPanel;
    }

    public List<Reaction> getSelectedReactions() {
        List<Reaction> selectedReactions = new ArrayList<Reaction>();
        for(Reaction reaction : reactions) {
            if(reaction.isSelected()) {
                selectedReactions.add(reaction);
            }
        }
        return selectedReactions;
    }

    private List<ListSelectionListener> selectionListeners = new ArrayList<ListSelectionListener>();

    public void addSelectionListener(ListSelectionListener lsl) {
        selectionListeners.add(lsl);
    }

    private void fireSelectionListeners() {
        for(ListSelectionListener listener : selectionListeners) {
            listener.valueChanged(new ListSelectionEvent(this, 0,reactions.length-1,false));
        }
    }

    private void selectRectangle(MouseEvent e) {
        Rectangle selectionRect = createRect(mousePos, e.getPoint());
        boolean ctrlIsDown = (e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) == MouseEvent.CTRL_DOWN_MASK;

        //select all wells within the selection rectangle
        for(int i=0; i < reactions.length; i++) {
            Rectangle bounds = reactions[i].getBounds();
            if(selectionRect.intersects(bounds)) {
                reactions[i].setSelected(true);
            }
            else {
                reactions[i].setSelected(ctrlIsDown ? wasSelected[i] : false);
            }
        }
        fireSelectionListeners();
    }

    /**
     * creates a rectangle between the two points
     * @param p1 a point representing one corner of the rectangle
     * @param p2 a point representing the opposite corner of the rectangle
     * @return the finished rectangle
     */
    private Rectangle createRect(Point p1, Point p2) {
        int x,y,w,h;
        if(p1.x < p2.x) {
            x = p1.x;
            w = p2.x-p1.x;
        }
        else {
            x = p2.x;
            w = p1.x-p2.x;
        }

        if(p1.y < p2.y) {
            y = p1.y;
            h = p2.y-p1.y;
        }
        else {
            y = p2.y;
            h = p1.y-p2.y;
        }
        return new Rectangle(x,y,w,h);
    }


}
