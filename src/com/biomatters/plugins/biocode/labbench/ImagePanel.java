package com.biomatters.plugins.biocode.labbench;

import javax.swing.*;
import java.awt.*;

public class ImagePanel extends JPanel {
    private Image[] images;

    public ImagePanel(Image... i) {
        this.images = i;
    }

    public Dimension getPreferredSize() {
        int w = 0;
        int h = 0;
        for (Image i : images) {
            w = Math.max(w, i.getWidth(null));
            h += i.getHeight(null) + 10;
        }
        w += 20;//padding
        h += 10;

        return new Dimension(Math.max(250,w), Math.max(250,h));
    }

    public void paintComponent(Graphics g) {
        g.setColor(SystemColor.control.darker().darker());
        g.fillRect(0, 0, getWidth(), getHeight());
        int y = 10;
        for (Image i : images) {
            g.drawImage(i, 10, y, null);
            y += i.getHeight(null) + 10;
        }

    }

}