package com.biomatters.plugins.moorea;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Blob;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 12/06/2009 11:14:15 AM
 */
public class GelImage {
    private int id = -1;
    private int plate;
    private byte[] imageBytes;
    private Image image;
    private String notes;

    public GelImage(int plate, File imageFile, String notes) throws IOException {
        this.notes = notes;
        this.plate = plate;
        FileInputStream in = new FileInputStream(imageFile);
        imageBytes = new byte[(int)imageFile.length()];
        in.read(imageBytes);
        createImage();
    }

    private void createImage() {
        if(imageBytes == null || imageBytes.length == 0) {
            throw new IllegalStateException("The image data buffer is empty!");
        }
        image = Toolkit.getDefaultToolkit().createImage(imageBytes);
    }

    public PreparedStatement toSql(Connection conn) throws SQLException {
        PreparedStatement statement;
        if(id < 0) {
            statement = conn.prepareStatement("INSERT INTO gelImages (plate, imageData, notes) VALUES (?, ?, ?)");
            statement.setInt(1, plate);
            statement.setObject(2, imageBytes);
            statement.setString(3, notes);
        }
        else {
            statement = conn.prepareStatement("UPDATE gelImages WHERE id=? SET plate=?, imageData=?, notes=?");
            statement.setInt(1, id);
            statement.setInt(2, plate);
            statement.setObject(3, imageBytes);
            statement.setString(4, notes);
        }
        return statement;
    }

    public void setPlate(int plate) {
        this.plate = plate;
    }
}
