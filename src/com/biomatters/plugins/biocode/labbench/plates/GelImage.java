package com.biomatters.plugins.biocode.labbench.plates;

import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.utilities.Base64Coder;
import com.sun.media.jai.codec.ByteArraySeekableStream;
import com.sun.media.jai.codec.ImageCodec;
import com.sun.media.jai.codec.ImageDecoder;
import com.sun.media.jai.codec.SeekableStream;
import org.jdom.Element;

import javax.media.jai.RenderedImageAdapter;
import javax.xml.bind.annotation.XmlRootElement;
import java.awt.*;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Steven Stones-Havas
 *          <p/>
 *          Created on 12/06/2009 11:14:15 AM
 */
@XmlRootElement
public class GelImage implements XMLSerializable {
    private int id = -1;
    private int plate;
    private byte[] imageBytes;
    private Image image;
    private String notes;
    private String filename;

    public GelImage() {
    }

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
        assert imageFile.length() < Integer.MAX_VALUE;
        imageBytes = new byte[(int)imageFile.length()];
        int bytesRead = in.read(imageBytes);
        assert bytesRead == imageFile.length();
        createImage();
    }

    /**
     * this constructor is only to be used for split gel images attached to reactions.  not images attached to plates.
     * @param imageBytes
     * @param name
     */
    public GelImage(byte[] imageBytes, String name) {
        this.filename = name;
        this.imageBytes = imageBytes;
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
        try {
            SeekableStream ss = new ByteArraySeekableStream(imageBytes);
            String[] codecNames = ImageCodec.getDecoderNames(ss);
            if(codecNames.length == 0) {
                assert false;
                return;
            }
            ImageDecoder decoder = ImageCodec.createImageDecoder(codecNames[0], ss, null);
            RenderedImage renderedImage;
            //noinspection ProhibitedExceptionCaught
            try {
                renderedImage = decoder.decodeAsRenderedImage();
            } catch (NullPointerException e) { //GEN-11933
                e.printStackTrace();
                throw new RuntimeException("Decoder could not create rendered image for "+filename, e);
            }
            RenderedImageAdapter planarImage = new RenderedImageAdapter(renderedImage);
            image = planarImage.getAsBufferedImage();
        } catch (IOException e) {
            e.printStackTrace();
        }

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

    public void setId(int id) {
        this.id = id;
    }

    public int getPlate() {
        return plate;
    }

    public byte[] getImageBytes() {
        return imageBytes;
    }

    public void setImageBytes(byte[] imageBytes) {
        this.imageBytes = imageBytes;
        createImage();
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

    public void setFilename(String filename) {
        this.filename = filename;
    }
}
