package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.utilities.Base64Coder;
import com.biomatters.geneious.publicapi.plugin.DocumentImportException;
import com.biomatters.geneious.publicapi.plugin.PluginUtilities;
import org.jdom.Element;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;

import jebl.util.ProgressListener;

/**
 * @author Steve
 * @version $Id$
 */
public class Trace implements XMLSerializable {
    private List<NucleotideSequenceDocument> sequences;
    private MemoryFile file;
    private int id;

    public Trace(Element e) throws XMLSerializationException {
        fromXML(e);
    }

    public Trace(MemoryFile file) throws IOException, DocumentImportException{
        this(convertRawTracesToTraceDocuments(file), file, file.getDatabaseId());
    }

    public Trace(List<NucleotideSequenceDocument> sequences, MemoryFile file) {
        this(sequences, file, -1);
    }

    public Trace(List<NucleotideSequenceDocument> sequences, MemoryFile file, int id) {
        this.sequences = sequences;
        this.file = file;
        this.id = id;
    }

    public List<NucleotideSequenceDocument> getSequences() {
        return sequences;
    }

    public MemoryFile getFile() {
        return file;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Element toXML() {
        Element element = new Element("trace");

        if(sequences != null) {
            for(NucleotideSequenceDocument sequence : sequences) {
                element.addContent(XMLSerializer.classToXML("sequence", sequence));
            }
        }

        if(file != null) {
            Element rawTraceElement = new Element("rawTrace");
            rawTraceElement.setAttribute("name", file.getName());
            rawTraceElement.setText(new String(Base64Coder.encode(file.getData())));
            element.addContent(rawTraceElement);
        }

        if(id >= 0) {
            element.addContent(new Element("id").setText(""+id));
        }


        return element;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        List<Element> sequenceElements = element.getChildren("sequence");
        if(sequenceElements.size() > 0) {
            sequences = new ArrayList<NucleotideSequenceDocument>();
            for(Element sequenceElement : sequenceElements) {
                sequences.add(XMLSerializer.classFromXML(sequenceElement,  NucleotideSequenceDocument.class));
            }
        }
        Element rawTraceElement = element.getChild("rawTrace");
        if(rawTraceElement != null) {
            file = new MemoryFile(rawTraceElement.getAttributeValue("name"), Base64Coder.decode(rawTraceElement.getText().toCharArray()));
        }
        id = -1;
        if(element.getChild("id") != null) {
            id = Integer.parseInt(element.getChildText("id"));
        }
    }

    private static List<NucleotideSequenceDocument> convertRawTracesToTraceDocuments(MemoryFile mFile) throws IOException, DocumentImportException {
        List<AnnotatedPluginDocument> docs = new ArrayList<AnnotatedPluginDocument>();

        File tempFolder = File.createTempFile("biocode_sequence", "");
        if(tempFolder.exists()) {
            tempFolder.delete();
        }
        if(!tempFolder.mkdir()){
            throw new IOException("could not create the temp dir!");
        }

        //write the data to a temp file (because Geneious file importers can't read an in-memory stream
        File abiFile = new File(tempFolder, mFile.getName());
        FileOutputStream out = new FileOutputStream(abiFile);
        out.write(mFile.getData());
        out.close();

        //import the file
        List<AnnotatedPluginDocument> pluginDocuments = PluginUtilities.importDocuments(abiFile, ProgressListener.EMPTY);
        docs.addAll(pluginDocuments);
        if(!abiFile.delete()){
            abiFile.deleteOnExit();
        }

        if(!tempFolder.delete()){
            tempFolder.deleteOnExit();
        }

        List<NucleotideSequenceDocument> sequences = new ArrayList<NucleotideSequenceDocument>();
        for(AnnotatedPluginDocument adoc : docs) {
            PluginDocument doc = adoc.getDocumentOrNull();
            if(doc == null) {
                continue;
            }
            if(!(doc instanceof NucleotideSequenceDocument)) {
                continue;
            }
            sequences.add((NucleotideSequenceDocument)doc);
        }
        return sequences;
    }
}
