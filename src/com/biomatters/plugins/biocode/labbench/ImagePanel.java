package com.biomatters.plugins.biocode.labbench;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;

public class ImagePanel extends JPanel {
    protected Image[] images;
    public static final int PADDING = 10;
    private double zoom = 1.0;
    private double rotation = 0;

    public ImagePanel(Image... i) {
        this.images = i;
    }



        public double getZoom() {
            return zoom;
        }

        public void setZoom(double zoom) {
            this.zoom = zoom;
            revalidate();
            repaint();
        }

        public double getRotation() {
            return rotation;
        }

        public void setRotation(double rotation) {
            this.rotation = rotation;
            revalidate();
            repaint();
        }

    public Dimension getPreferredSize() {
        int w = 0;
        int h = 0;
        for (Image i : images) {
            int imageWidth = (int) (zoom * i.getWidth(null));
            int imageHeight = (int) (zoom * i.getHeight(null)) + PADDING;
            Dimension boundingBox = getBoundingBox(new Dimension(imageWidth, imageHeight));
            w = Math.max(w, boundingBox.width);
            h += boundingBox.height;
        }
        w += 2*PADDING;//padding
        h += PADDING;

        return new Dimension(Math.max(250,w), Math.max(250,h));
    }

    protected Dimension getBoundingBox(Dimension bounds) {
        Area area = new Area(new Rectangle(0,0,bounds.width,bounds.height));
        area.transform(AffineTransform.getRotateInstance(rotation));
        return area.getBounds().getSize();
    }

    public void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D)g;
        g.setColor(SystemColor.control.darker().darker());
        g.fillRect(0, 0, getWidth(), getHeight());
        int y = PADDING;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        AffineTransform transform = g2.getTransform();
        for (Image i : images) {
            Dimension rotatedBounds = getBoundingBox(new Dimension((int) (zoom * i.getWidth(null)), (int) (zoom * i.getHeight(null))));
            int halfHeight =  i.getHeight(null) / 2;
            int halfWidth = i.getWidth(null) / 2;
            g.translate(PADDING + rotatedBounds.width/2, y+ rotatedBounds.height/2);
            g2.scale(zoom, zoom);
            g2.rotate(rotation);
            g.drawImage(i,  -halfWidth, -halfHeight, null);
            y += i.getHeight(null) + PADDING;
            g2.setTransform(transform);
        }

    }

}