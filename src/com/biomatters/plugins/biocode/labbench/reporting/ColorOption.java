package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import org.jdom.Element;

/**
 * An option for use with {@link com.biomatters.geneious.publicapi.plugin.Options} that lets the user choose a Color.
 * stolen from PrivateAPI
 * @author Matt Kearse
 */
public class ColorOption extends Options.Option<Color, JPanel> {
    protected ColorOption(Element element) throws XMLSerializationException {
        super(element);
    }

    public ColorOption(String name, String label, Color defaultValue) {
        super(name, label, defaultValue);
    }


    public Color getValueFromString(String value) {
        return getColorFromString(value);
    }

    /**
     * Performs the opposite of Color.toString().
     * This would break if Color.toString() changes, but it look like it won't: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6352385
     * @param value a value returned from {@link java.awt.Color#toString()}
     * @return a color, or black if value was invalid.
     */
    public static Color getColorFromString(String value) {
        int r=getField(value,'r');
        int g=getField(value,'g');
        int b=getField(value,'b');
        final Color c = new Color(r, g, b);
//        Logs.temp("decode "+value+" to="+c);
        return c;
    }

    private static int getField(String value, char field) {
        int p=value.indexOf(field+"=");
        if (p<0) return 0;
        value=value.substring(p+2);
        int p2=value.indexOf(',');
        if (p2<0) p2=value.indexOf(']');
        if (p2<0) return 0;
        value=value.substring(0,p2);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    protected void setValueOnComponent(JPanel component, Color value) {
        component.setBackground(value);
    }

    protected JPanel createComponent() {
        final JPanel p = new JPanel();
        final LineBorder standardBorder = new LineBorder(Color.GRAY);
        final LineBorder mouseOverBorder = new LineBorder(Color.BLACK);
        p.setBorder(standardBorder);
        p.setPreferredSize(new Dimension(50,10));
        p.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                p.setBorder(standardBorder);
                Color newColor = GuiUtilities.getUserSelectedColor(getValue(), null, "Choose Color");
                if(newColor != null){
                    setValue(newColor);
                }
            }

            public void mouseEntered(MouseEvent e) {
                p.setBorder(mouseOverBorder);
                super.mouseEntered(e);
            }

            public void mouseExited(MouseEvent e) {
                p.setBorder(standardBorder);
                super.mouseExited(e);
            }
        });
        setValueOnComponent(p,getValue());
        return p;
    }
}

