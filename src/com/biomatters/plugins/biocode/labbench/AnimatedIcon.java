package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 * Created on Aug 11, 2008 2:58:11 PM by Steve
 */
public class AnimatedIcon implements Icon {
    private static final java.util.logging.Logger logger=java.util.logging.Logger.getLogger(AnimatedIcon.class.getName());

    private static Image[] images;
    static {
        images = new Image[12];
        for(int i = 0; i < 12; i++){
            images[i] = IconUtilities.getImage("activity"+(i+1)+".png");
        }
        MediaTracker m = new MediaTracker(new JPanel());
        for(Image img : images)
            m.addImage(img, 0);

        try{
            m.waitForAll(1);
        }
        catch(InterruptedException ex){/*ignore*/}
    }
    /**
     * @return a 13 by 13 pixel AnimatedIcon that is used to indicate something is in progress.
     */
    public static AnimatedIcon getActivityIcon() {
        return new AnimatedIcon(images, 60, false);
    }

    private Component component;
    private Graphics g;
    private Image[] frames;
    private int pointer = 0;
    private int delay;
    private AtomicBoolean threadIsRunning = new AtomicBoolean(false);
    private boolean automaticallyStartAnimation = false;

    /**
     * An animated icon made from the specified images. startAnimation must be called to start the animation
     *
     * @param images the frames of the animation
     * @param delay the delay between frames
     */
    public AnimatedIcon(Image[] images, final int delay){
        this(images,delay,true);
    }

    public AnimatedIcon(Image[] images, final int delay, boolean createMediaTracker){
        this.delay = delay;
        if (createMediaTracker) {
            MediaTracker m = new MediaTracker(new JPanel());
            for(Image img : images)
                m.addImage(img, 0);

            try{
                m.waitForAll(1);
            }
            catch(InterruptedException ex){/*ignore*/}
        }
        this.frames = images;
    }

    public void startAnimation(){
        if(threadIsRunning.getAndSet(true))
            return;

        Thread animationThread = new Thread("AnimatedIcon repaint thread in Moorea Plugin") {
            public void run() {
                final AtomicBoolean isDisplayable = new AtomicBoolean(true);
                while (isDisplayable.get()) { //isDisplayable should be false once this icon's parent is disposed
                    if(component != null){
                        Runnable runnable = new Runnable() {
                            public void run() {
                                isDisplayable.set(component.isDisplayable());
                            }
                        };
                        ThreadUtilities.invokeNowOrWait(runnable);
                    }
                    if (g != null)
                        repaint(g);
                    try {
                        Thread.sleep(delay);
                    }
                    catch (InterruptedException ex) {
                        logger.info("Animated icon sleep thread was interrupted: "+ex.toString()); //we don't need to take any more action than this
                    }
                }
                threadIsRunning.set(false);
            }
        };
        animationThread.start();
    }


    private void repaint(Graphics g){
        if(g == null)
            return;
        component.repaint();

        //the bounds check must happen before the incriment to prevent a concurrency related ArrayIndexOutOfBoundsException
        if(pointer >= frames.length-1)
            pointer = 0;
        else
            pointer++;
    }



    public void paintIcon(Component c, Graphics g, int x, int y) {
        assert component == null || component == c; //we shouldn't really be setting this icon on more than one component
        if(component == null) {
            component = c;
            component.addHierarchyListener(new HierarchyListener(){ //restart the animation if the icon becomes visible again...
                public void hierarchyChanged(HierarchyEvent e) {
                    if(component.isDisplayable()) {
                        startAnimation();
                    }
                }
            });
        }
        this.g = g;
        if (component!=null && automaticallyStartAnimation) {
            automaticallyStartAnimation = false;
            startAnimation();
        }

        g.drawImage(frames[pointer], x, y, null);
    }

    public int getIconWidth() {
        return frames != null && frames.length > 0 ? frames[0].getWidth(null) : 0;
    }

    public int getIconHeight() {
        return frames != null && frames.length > 0 ? frames[0].getHeight(null) : 0;
    }

    /**
     * @return a stand-alone JComponent suitable for displaying this icon.
     */
    public JComponent createComponent() {
        automaticallyStartAnimation = true;
        return new JLabel(this);
    }
}

