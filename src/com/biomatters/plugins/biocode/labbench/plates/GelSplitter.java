package com.biomatters.plugins.biocode.labbench.plates;

import com.biomatters.plugins.biocode.labbench.ImagePanel;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.sun.image.codec.jpeg.JPEGCodec;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.HashMap;
import java.io.*;

import org.virion.jam.util.SimpleListener;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 7/07/2010 3:36:37 PM
 */

public class GelSplitter {
    public static void splitGel(Plate plate, GelImage image) {
        final SplitGelImagePanel imagePanel = new SplitGelImagePanel(image.getImage(), plate);
        JScrollPane scroller = new JScrollPane(imagePanel);

        Options options = new Options(GelSplitter.class);
        final Options.IntegerOption numberOfRowsOption = options.addIntegerOption("numberOfRows", "Number of Rows", 8, 1, plate.getReactions().length);
        final Options.IntegerOption numberOfColsOption = options.addIntegerOption("numberOfCols", "Number of Columns", 12, 1, plate.getReactions().length);
        final Options.StringOption startLetter = options.addStringOption("startLetter", "Start", "A");
        startLetter.setNumberOfColumns(1);
        final Options.IntegerOption startNumber = options.addIntegerOption("startNumber", "Start", 1, 1, plate.getCols());
        final AtomicInteger direction = new AtomicInteger(0);
        Options.ButtonOption toggleDirection = options.addButtonOption("toggleDirection", "", "Toggle Direction");

        toggleDirection.setSpanningComponent(true);

        final SimpleListener updateListener = new SimpleListener() {
            public void objectChanged() {
                imagePanel.setRows(numberOfRowsOption.getValue());
                imagePanel.setCols(numberOfColsOption.getValue());
                imagePanel.setStartLetter(startLetter.getValue());
                imagePanel.setStartNumber(startNumber.getValue());
                imagePanel.setDirection(direction.get());
            }
        };
        toggleDirection.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                int value = direction.get()+1;
                if(value > 3) {
                    value = 0;
                }
                direction.set(value);
                updateListener.objectChanged();              
            }
        });

        numberOfRowsOption.addChangeListener(updateListener);
        numberOfColsOption.addChangeListener(updateListener);
        startLetter.addChangeListener(updateListener);
        startNumber.addChangeListener(updateListener);
        toggleDirection.addChangeListener(updateListener);

        updateListener.objectChanged();


        JPanel holder = new JPanel(new BorderLayout());
        holder.add(scroller, BorderLayout.CENTER);
        holder.add(options.getPanel(), BorderLayout.EAST);



        Dialogs.showDialog(new Dialogs.DialogOptions(new String[] {"ok"}, "test"), holder);
        final AtomicReference<Map<BiocodeUtilities.Well, BufferedImage>> imageMap = new AtomicReference<Map<BiocodeUtilities.Well, BufferedImage>>();
        BiocodeService.block("Splitting your GEL", null, new Runnable() {
            public void run() {
                imageMap.set(imagePanel.splitImage());
            }
        });
        File folder = new File(System.getProperty("user.home")+File.separator+"images");
        for(Map.Entry<BiocodeUtilities.Well, BufferedImage> entry : imageMap.get().entrySet()) {
            BufferedImage entryImage = entry.getValue();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                com.sun.image.codec.jpeg.JPEGImageEncoder jpegImageEncoder = JPEGCodec.createJPEGEncoder(out);
                jpegImageEncoder.encode(entryImage);
                out.close();
                GelImage gelImage = new GelImage(out.toByteArray(), entry.getKey().toString());
                Reaction reaction = plate.getReaction(entry.getKey());
                if(reaction != null) {
                    reaction.setGelImage(gelImage);
                }
            } catch (IOException e) {
                e.printStackTrace();
                //todo: error handling
            }
        }
    }



    private static class SplitGelImagePanel extends ImagePanel{
        private int rows = 5;
        private int cols = 5;
        private Point mouseDownPoint = null;
        private Point mouseUpPoint = null;
        private boolean mouseDown = false;
        private Rectangle dragRectangle;
        private int direction = 0;
        private int startRow;
        private int startCol;
        private Plate plate;

        public SplitGelImagePanel(Image i, Plate plate) {
            super(i);
            this.plate = plate;
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    mouseDownPoint = e.getPoint();
                    mouseDown = true;
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    mouseDown = false;
                }
            });
            addMouseMotionListener(new MouseMotionAdapter(){
                @Override
                public void mouseDragged(MouseEvent e) {
                    mouseChanged(e);
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    mouseChanged(e);
                }

                public void mouseChanged(MouseEvent e) {
                    if(mouseDown) {
                        mouseUpPoint = e.getPoint();
                        dragRectangle = getRectangle(mouseDownPoint, mouseUpPoint);
                        repaint();
                    }
                }
            });
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);

            if(rows > 0 && cols > 0 && dragRectangle != null) {
                g.setColor(new Color(0,192,0));
                for(int i=0; i < cols; i++) {
                    for(int j=0; j < rows; j++) {
                        int x1 = dragRectangle.x + i * (dragRectangle.width / cols);
                        int y1 = dragRectangle.y + j * (dragRectangle.height / rows);
                        int width = dragRectangle.width / cols;
                        int height = dragRectangle.height / rows;

                        Point p = getRowAndCol(i, j);

                        String wellName = width > 15 && height > 15 ? Plate.getWellName(p.x, p.y) : "";
                        g.drawString(wellName,x1+3,y1+11);
                        g.drawRect(x1, y1, width, height);
                    }
                }

            }
        }

        private Point getRowAndCol(int i, int j) {
            Point p;
            switch(direction) {
                case 0:
                    p = new Point(j + startRow, i + startCol);
                    break;
                case 1:
                    p = new Point(i+startRow, j+startCol);
                    break;
                case 2:
                    p = new Point((rows-j-1) + startRow, (cols-i-1)+startCol);
                    break;
                case 3:
                    p = new Point((cols-i-1)+startRow, (rows-j-1)+startCol);
                    break;
                default :
                    assert false;
                    p = new Point(0,0);
            }
            return p;
        }

        private static Rectangle getRectangle(Point p1, Point p2) {
            int x = Math.min(p1.x, p2.x)-PADDING;
            int y = Math.min(p1.y, p2.y)-PADDING;
            int width = Math.abs(p1.x-p2.x);
            int height = Math.abs(p1.y-p2.y);
            return new Rectangle(x,y,width,height);


        }

        public void setRows(int rows) {
            this.rows = rows;
            repaint();
        }

        public void setCols(int cols) {
            this.cols = cols;
            repaint();
        }

        public Rectangle getDragRectangle() {
            return dragRectangle;
        }

        public void setDirection(int direction) {
            this.direction = direction;
            repaint();
        }

        public void setStartLetter(String startLetter) {
            String tidiedLetter = startLetter.toUpperCase().trim();
            if(tidiedLetter.length() != 1) {
                startRow = 0;
                repaint();
                return;
            }
            int startColTemp = tidiedLetter.charAt(0)-65;
            if(startColTemp < 0 || startColTemp > plate.getCols()) {
                startRow = 0;
                repaint();
                return;
            }
            startRow = startColTemp;
            repaint();
        }

        public void setStartNumber(int startNumber) {
            this.startCol = startNumber-1;
            repaint();
        }

        public Map<BiocodeUtilities.Well, BufferedImage> splitImage() {
            Map<BiocodeUtilities.Well, BufferedImage> imageMap = new HashMap<BiocodeUtilities.Well, BufferedImage>();
            if(rows > 0 && cols > 0 && dragRectangle != null) {
                for(int i=0; i < cols; i++) {
                    for(int j=0; j < rows; j++) {
                        int x1 = dragRectangle.x + i * ((dragRectangle.width / cols));
                        int y1 = dragRectangle.y + j * ((dragRectangle.height / rows));
                        int width = dragRectangle.width / cols;
                        int height = dragRectangle.height / rows;

                        Point p = getRowAndCol(i,j);

                        String wellName = width > 15 && height > 15 ? Plate.getWellName(p.x, p.y) : "";
                        double scaleFactor = 40.0/Math.max(width,height);
                        BufferedImage image = new BufferedImage((int)(scaleFactor*width), (int)(scaleFactor*height), BufferedImage.TYPE_INT_RGB);
                        Graphics2D graphics = image.createGraphics();
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                        graphics.scale(scaleFactor, scaleFactor);
                        graphics.drawImage(images[0], -x1, -y1, this);

                        imageMap.put(new BiocodeUtilities.Well(wellName), image);
                    }
                }

            }
            return imageMap;
        }
    }
}
