package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 21/08/2009
 * Time: 8:45:09 AM
 * To change this template use File | Settings | File Templates.
 */
public class MicrosatGraphPanel extends JPanel {
    private int[][] values;
    double SCALE = 0.05;
    Color[] colors = new Color[] {Color.blue, Color.green, Color.yellow, Color.red, Color.orange};

    public MicrosatGraphPanel(int[][] values) {
        this.values = values;
        Thread t = new Thread() {
            @Override
            public void run() {
                double t = 0;
                while(true) {
                    SCALE = Math.sin(t);
                    t += 0.01;
                    if(t > 2*Math.PI) {
                        t -= 2*Math.PI;
                    }
                    repaint();
                    ThreadUtilities.sleep(30);
                }
            }
        };
        //t.start();
    }     //

    public void paintComponent(Graphics g) {
        g.setColor(Color.white);
        g.fillRect(0,0,getWidth(),getHeight());
        Rectangle bounds = g.getClipBounds();
        //((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for(int i=1; i < values.length; i++) {
            int x1 = values[i-1][0];
            int x2 = values[i][0];
            if(x2 < bounds.x || x1 > bounds.x+bounds.width) {
                continue;
            }
            for(int j=1; j < values[0].length; j++) {
                int y1 = (int)(getHeight()-values[i-1][j]*SCALE)-getHeight()/2;
                int y2 = (int)(getHeight()-values[i][j]*SCALE)-getHeight()/2;
                g.setColor(colors[j-1]);
                g.drawLine(x1,y1,x2,y2);
            }
        }
    }

    public Dimension getPreferredSize() {
        return new Dimension(values.length, 300);
    }

}
