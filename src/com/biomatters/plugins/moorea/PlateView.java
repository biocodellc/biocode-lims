package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.plugins.moorea.reaction.Reaction;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;


public class PlateView extends JPanel {
    private int rows;
    private int cols;
    private Reaction[] reactions;
    private Reaction.Type type;


    public enum PlateSize {
        w48,
        w96,
        w384
    }


    public PlateView(PlateSize size, Reaction.Type type) {
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
                            Dialogs.showOptionsDialog(reactions[i].getOptions(), "Well Options", false);
                            revalidate();
                        }
                    }

                }
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
