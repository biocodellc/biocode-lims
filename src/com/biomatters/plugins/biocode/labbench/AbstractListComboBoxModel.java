package com.biomatters.plugins.biocode.labbench;

import javax.swing.*;

/**
 * @author Steven Stones-Havas
 *          <p/>
 *          Created on 10/06/2009 7:47:30 PM
 */
public abstract class AbstractListComboBoxModel extends AbstractListModel implements ComboBoxModel{
    private Object selectedObject;

    public void setSelectedItem(Object anObject) {
        if ((selectedObject != null && !selectedObject.equals( anObject )) ||
	    selectedObject == null && anObject != null) {
	    selectedObject = anObject;
	    fireContentsChanged(this, -1, -1);
        }
    }

    // implements javax.swing.ComboBoxModel
    public Object getSelectedItem() {
        return selectedObject;
    }

    public void fireContentsChanged() {
        fireContentsChanged(this, 0, getSize()-1);
    }

    
}
