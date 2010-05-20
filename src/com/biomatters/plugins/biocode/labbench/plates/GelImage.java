package com.biomatters.plugins.biocode.labbench.plates;

import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.utilities.Base64Coder;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;

import org.jdom.Element;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 12/06/2009 11:14:15 AM
 */
public class GelImage implements XMLSerializable {
    private int id = -1;
    private int plate;
    private byte[] imageBytes;
    private Image image;
    private String notes;
    private String filename;

    public GelImage(Element xml) throws XMLSerializationException {
        fromXML(xml);
    }

    public GelImage(int plate, File imageFile, String notes) throws IOException {
        this.notes = notes;
        this.plate = plate;
        this.filename = imageFile.getName();
        FileInputStream in = new FileInputStream(imageFile);
        if(imageFile.length() > Integer.MAX_VALUE) {
            throw new IOException("The file "+imageFile.getName()+" is too large");
        }
        imageBytes = new byte[(int)imageFile.length()];
        in.read(imageBytes);
        createImage();
    }

    public GelImage(ResultSet resultSet) throws SQLException{
        this.notes = resultSet.getString("gelimages.notes");
        this.plate = resultSet.getInt("gelimages.plate");
        this.id = resultSet.getInt("gelimages.id");
        this.imageBytes = resultSet.getBytes("gelimages.imageData");
        this.filename = resultSet.getString("gelImages.name");
        createImage();
    }

    private void createImage() {
        if(imageBytes == null || imageBytes.length == 0) {
            throw new IllegalStateException("The image data buffer is empty!");
        }
        image = Toolkit.getDefaultToolkit().createImage(imageBytes);
        MediaTracker mt = new MediaTracker(new JLabel());
        mt.addImage(image,0);
        try {
            mt.waitForAll();
        }
        catch(InterruptedException ex){}
    }

    public PreparedStatement toSql(Connection conn) throws SQLException {
        PreparedStatement statement;
        statement = conn.prepareStatement("INSERT INTO gelimages (plate, imageData, notes, name) VALUES (?, ?, ?, ?)");
        statement.setInt(1, plate);
        statement.setObject(2, imageBytes);
        statement.setString(3, notes);
        statement.setString(4, filename);
        return statement;
    }

    public Element toXML() {
        Element xml = new Element("GelImage");
        xml.addContent(new Element("id").setText(""+getId()));
        xml.addContent(new Element("name").setText(filename));
        xml.addContent(new Element("plate").setText(""+getPlate()));
        xml.addContent(new Element("notes").setText(getNotes()));
        String imageBase64 = new String(Base64Coder.encode(imageBytes));
        xml.addContent(new Element("imageData").setText(imageBase64));

        return xml;
    }

    public void fromXML(Element xml) throws XMLSerializationException {
        id = Integer.parseInt(xml.getChildText("id"));
        filename = xml.getChildText("name");
        plate = Integer.parseInt(xml.getChildText("plate"));
        notes = xml.getChildText("notes");
        imageBytes = Base64Coder.decode(xml.getChildText("imageData").toCharArray());
        createImage();
    }

    public void setPlate(int plate) {
        this.plate = plate;
    }

    public int getId() {
        return id;
    }

    public int getPlate() {
        return plate;
    }

    public byte[] getImageBytes() {
        return imageBytes;
    }

    public Image getImage() {
        return image;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getFilename() {
        return filename;
    }

    public void setImageBytes(byte[] imageBytes) {
        this.imageBytes = imageBytes;
    }
}
