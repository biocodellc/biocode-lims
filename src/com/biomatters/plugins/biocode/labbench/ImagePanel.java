package com.biomatters.plugins.biocode.labbench;

import javax.swing.*;
import java.awt.*;

public class ImagePanel extends JPanel {
    protected Image[] images;
    public static final int PADDING = 10;

    public ImagePanel(Image... i) {
        this.images = i;
    }

    public Dimension getPreferredSize() {
        int w = 0;
        int h = 0;
        for (Image i : images) {
            w = Math.max(w, i.getWidth(null));
            h += i.getHeight(null) + PADDING;
        }
        w += 2*PADDING;//padding
        h += PADDING;

        return new Dimension(Math.max(250,w), Math.max(250,h));
    }

    public void paintComponent(Graphics g) {
        g.setColor(SystemColor.control.darker().darker());
        g.fillRect(0, 0, getWidth(), getHeight());
        int y = PADDING;
        for (Image i : images) {
            g.drawImage(i, PADDING, y, null);
            y += i.getHeight(null) + PADDING;
        }

    }

}