package com.biomatters.plugins.moorea.plates;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.OptionsPanel;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.plugins.moorea.reaction.Reaction;
import com.biomatters.plugins.moorea.ButtonOption;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.virion.jam.util.SimpleListener;


public class PlateView extends JPanel {

    private PlateView selfReference = this;
    private Plate plate;
    private boolean colorBackground = true;



    public PlateView(int numberOfWells, Reaction.Type type) {
        plate = new Plate(numberOfWells, type);
        init();
    }


    public PlateView(Plate.Size size, Reaction.Type type) {
        plate = new Plate(size, type);
        init();
    }

    public PlateView(Plate plate) {
        this.plate = plate;
        init();
    }

    public Plate getPlate() {
        return plate;
    }


    @Override
    protected void paintComponent(Graphics g1) {
        int cols = plate.getCols();
        int rows = plate.getRows();
        Reaction[] reactions = plate.getReactions();

        Graphics2D g = (Graphics2D)g1;
        int cellWidth = (getWidth()+1)/cols;
        int cellHeight = (getHeight()+1)/rows;

        g.setColor(Color.white);
        g.fillRect(0,0,getWidth(),getHeight());

        g.setColor(getBackground());
        g.fillRect(0,0,cellWidth*cols+1,cellHeight*rows+1);
        Shape clip = g.getClip();


        for(int i=0; i < rows; i++) {
            for(int j = 0; j < cols; j++) {
                final Reaction reaction = reactions[cols*i + j];
                Rectangle reactionBounds = new Rectangle(1+cellWidth * j, 1+cellHeight * i, cellWidth - 1, cellHeight - 1);
                reaction.setBounds(reactionBounds);
                g.clip(reactionBounds);
                reaction.paint(g, colorBackground);
                g.setClip(clip);
            }
        }
        //System.out.println("paintin: "+(System.currentTimeMillis()-time));
    }

    public boolean isColorBackground() {
        return colorBackground;
    }

    public void setColorBackground(boolean colorBackground) {
        this.colorBackground = colorBackground;
    }

    @Override
    public Dimension getPreferredSize() {
        int width = 0;
        int height = 0;

        Reaction[] reactions = plate.getReactions();

        for(int i=0; i < plate.getRows(); i++) {
            for(int j = 0; j < plate.getCols(); j++) {
                height = Math.max(height, reactions[j*i + j].getPreferredSize().height);
                width = Math.max(width, reactions[j*i + j].getPreferredSize().width);
            }
        }


        return new Dimension(1+(width+1)*plate.getCols(), 1+(height+1)*plate.getRows());
    }

    private Point mousePos = new Point(0,0);
    private Boolean[] wasSelected;

    private void init() {

        final Reaction[] reactions = plate.getReactions();
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
                //todo: do we want double clicking?
//                if(e.getClickCount() == 2) {
//                    for(int i=0; i < reactions.length; i++) {
//                        if(reactions[i].isSelected()) {
//                            editReactions(Arrays.asList(reactions[i]), justEditDisplayableFields);
//                            revalidate();
//                            repaint();
//                        }
//                    }
//
//                }
                fireSelectionListeners();
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                Reaction[] reactions = plate.getReactions();
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




    }


    public List<Reaction> getSelectedReactions() {
        List<Reaction> selectedReactions = new ArrayList<Reaction>();
        for(Reaction reaction : plate.getReactions()) {
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
            listener.valueChanged(new ListSelectionEvent(this, 0,plate.getReactions().length-1,false));
        }
    }

    private void selectRectangle(MouseEvent e) {
        Rectangle selectionRect = createRect(mousePos, e.getPoint());
        boolean ctrlIsDown = (e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) == MouseEvent.CTRL_DOWN_MASK;
        Reaction[] reactions = plate.getReactions();

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
