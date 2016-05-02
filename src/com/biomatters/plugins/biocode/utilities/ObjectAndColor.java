package com.biomatters.plugins.biocode.utilities;

import java.awt.*;

/**
* @author Steve (Matt moved this out of the public api where it lived as an inner class of GuiUtilities during 5.1 development)
*          <p/>
*          Created on 6/09/2010 12:43:30 PM
*/
public class ObjectAndColor implements Comparable {
    private Object object;
    private Color color;
    private Color selectedColor;

    public ObjectAndColor(Object object, Color color) {
        this.object = object;
        this.color = color;
        this.selectedColor = color;
    }

    public ObjectAndColor(Object object, Color color, Color selectedColor) {
        this.object = object;
        this.color = color;
        this.selectedColor = selectedColor;
    }

    public Object getObject() {
        return object;
    }

    public Color getColor() {
        return getColor(false);
    }

    public Color getColor(boolean isSelected) {
        return isSelected ? selectedColor : color;
    }

    @Override
    public String toString() {
        return getObject() == null ? null : getObject().toString();
    }

    public int compareTo(Object o) {
        if(o == null || !(o instanceof ObjectAndColor) || getObject() == null || !(getObject() instanceof Comparable)) {
            return Integer.MAX_VALUE;
        }
        ObjectAndColor object = (ObjectAndColor)o;
        //noinspection unchecked
        return ((Comparable)getObject()).compareTo(object.getObject());
    }
}
