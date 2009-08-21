package com.biomatters.plugins.moorea.labbench.fims.tapir;

import com.biomatters.geneious.publicapi.databaseservice.AdvancedSearchQueryTerm;
import com.biomatters.geneious.publicapi.databaseservice.CompoundSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Geneious;
import com.biomatters.plugins.moorea.labbench.fims.MicrosatGraphPanel;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import javax.swing.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 19/08/2009
 * Time: 12:01:40 PM
 * To change this template use File | Settings | File Templates.
 */
public class TAPIRClient {
    private URL accessPoint;

    public TAPIRClient(String accessPoint) {
        try {
            this.accessPoint = new URL(accessPoint);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("You must enter a valid URL", e);
        }
    }

     public List<DocumentField> getSearchAttributes2() throws JDOMException, IOException {
         return Arrays.asList(
                 //new DocumentField("@catnum", "", "http://rs.tdwg.org/dwc/dwcore/CatalogNumber", String.class, true, true)  //,
                 new DocumentField("Collector", "", "http://rs.tdwg.org/dwc/dwcore/Collector", String.class, true, true)
         );

     }


    public List<DocumentField> getSearchAttributes() throws JDOMException, IOException {
        Element query = padQueryXML(new Element("capabilities"));
        Element response = queryServer(query);
        Element capabilities = response.getChild("capabilities", response.getNamespace());
        Element concepts = capabilities.getChild("concepts", capabilities.getNamespace());
        List<Element> conceptSchema = concepts.getChildren("schema", concepts.getNamespace());
        List<DocumentField> fields = new ArrayList<DocumentField>();
        for(Element schema : conceptSchema) {
            List<Element> fieldElements = schema.getChildren("mappedConcept", schema.getNamespace());
            for(Element fieldElement : fieldElements) {
                String code = fieldElement.getAttributeValue("id");
                String name;
                if(code.indexOf("/") >= 0) {
                    name = code.substring(code.lastIndexOf("/")+1);
                }
                else {
                    name = code;
                }
                fields.add(new DocumentField(name, "", code, getFieldClass(fieldElement), true, false));
            }
        }
        return fields;
    }

    private Class getFieldClass(Element field) {
        String attribute = field.getAttributeValue("datatype");
        if(attribute.endsWith("date")) {
            return Date.class;
        }
        else if(attribute.endsWith("decimal")) {
            return Double.class;
        }

        return String.class;
    }


    /**
     * surrounds the query with the tapir XML that goes around every request...
     * @param queryToSurround
     */
    private Element padQueryXML(Element queryToSurround) {
        Element request = new Element("request");
        Namespace namespace = Namespace.getNamespace("http://rs.tdwg.org/tapir/1.0");
        request.setNamespace(namespace);
        Element headerElement = new Element("header", namespace);
        Element source = new Element("source", namespace);
        source.setAttribute("sendtime", new Date().toString());
        source.addContent(new Element("software").setAttribute("name", "Geneious").setAttribute("version", Geneious.getVersion()));
        headerElement.addContent(source);
        request.addContent(headerElement);
        if(queryToSurround != null) {
            queryToSurround.setNamespace(namespace);
            request.addContent(queryToSurround);
        }
        return request;
    }



    private Element queryServer(Element query) throws IOException, JDOMException {
        URLConnection connection = accessPoint.openConnection();

        XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
        StringWriter stringWriter = new StringWriter();
        outputter.output(query, stringWriter);
        String xmlOut = stringWriter.toString();

        HttpURLConnection httpConnection = (HttpURLConnection)connection;

        httpConnection.setRequestMethod("POST");

        httpConnection.setDoInput(true);
        httpConnection.setDoOutput(true);
        httpConnection.setRequestMethod("POST");
        httpConnection.setRequestProperty("Content-Type", "text/xml; charset=UTF-8");
        httpConnection.setRequestProperty( "Content-Length", String.valueOf( xmlOut.length() ) );
        BufferedOutputStream out = new BufferedOutputStream(connection.getOutputStream());
        out.write(xmlOut.getBytes("UTF-8"));
        out.flush();
        out.close();

        //read in the response...
        BufferedReader in = new BufferedReader(new InputStreamReader((connection.getInputStream())));
        String xml = "";
        String buffer = null;
        while((buffer = in.readLine()) != null){
            xml += buffer+"\n";
        }
        in.close();
       // System.out.println(xml);

        //parse the response to a JDOM element
        SAXBuilder sbuilder = new SAXBuilder();
        Document doc = sbuilder.build(new StringReader(xml));
        return doc.detachRootElement();
    }

    public Element getStructure(List<DocumentField> fieldsToSearch) throws JDOMException, IOException {
        Element structure = new Element("structure");

        Namespace namespace = Namespace.getNamespace("xs", "http://www.w3.org/2001/XMLSchema");
        Element schema = new Element("schema", namespace);
        schema.setAttribute("targetNamespace", "http://example.net/simple_specimen");
        //schema.setAttribute("xsi:schemaLocation", "http://www.w3.org/2001/XMLSchema http://www.w3.org/2001/XMLSchema.xsd");
        structure.addContent(schema);

        Element records = new Element("element", namespace).setAttribute("name", "records");
        Element element = new Element("element", namespace).setAttribute("name", "record").setAttribute("minOccurs", "0").setAttribute("maxOccurs", "unbounded").setAttribute("type", "unitType");
        records.addContent(new Element("complexType", namespace).addContent(new Element("sequence", namespace).addContent(element)));
        schema.addContent(records);

        Element unitType = new Element("complexType", namespace).setAttribute("name", "unitType");
        unitType.addContent(new Element("attribute", namespace).setAttribute("name", "catnum").setAttribute("type", "xs:int").setAttribute("use", "required"));
        Element sequence = new Element("sequence", namespace);
        unitType.addContent(sequence);
        for(DocumentField field : fieldsToSearch) {
            Element unitElement = new Element("element", namespace);
            unitElement.setAttribute("name", field.getName());
            unitElement.setAttribute("type", getElementType(field.getValueType()));
            unitElement.setAttribute("minOccurs", "0");
            sequence.addContent(unitElement);
        }
        schema.addContent(unitType);


        return structure;
    }

    public String getElementType(Class dataType) {
        if(Double.class.equals(dataType)) {
            return "xs:decimal";
        }
        else if(Integer.class.equals(dataType)) {
            return "xs:int";
        }
        else if(Date.class.equals(dataType)) {
            return "xs:date";
        }
        return "xs:string";
    }

    public Element searchTapirServer(CompoundSearchQuery query, List<DocumentField> fieldsToSearch) throws JDOMException, IOException {
        Element searchXML = generateSearchXML(query, fieldsToSearch);
        searchXML = padQueryXML(searchXML);
        XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
        out.output(searchXML, System.out);
        System.out.println("---------------------------------");
        Element result = queryServer(searchXML);
        return result;
    }

    public static void clearNamespace(Element e) {
        e.setNamespace(null);
        for(Element el : e.getChildren()) {
            clearNamespace(el);
        }
    }



    public Element generateSearchXML(CompoundSearchQuery query, List<DocumentField> fieldsToSearch) throws JDOMException, IOException {
        Element searchElement = new Element("search");
        searchElement.setAttribute("count", "true").setAttribute("start", "0").setAttribute("limit", "1000").setAttribute("envelope", "true");

        Element outputModel = new Element("outputModel");
        searchElement.addContent(outputModel);


        outputModel.addContent(getStructure(fieldsToSearch));

        outputModel.addContent(new Element("indexingElement").setAttribute("path", "/records/record"));

        Element mappingElement = new Element("mapping");

        Element catNumElement = new Element("node").setAttribute("path", "/records/record/@catnum");
        catNumElement.addContent(new Element("concept").setAttribute("id", "http://rs.tdwg.org/dwc/dwcore/CatalogNumber"));
        mappingElement.addContent(catNumElement);

        for(DocumentField field : fieldsToSearch) {
            Element unitElement = new Element("node").setAttribute("path", "/records/record/"+field.getName());
            unitElement.addContent(new Element("concept").setAttribute("id", field.getCode()));
            mappingElement.addContent(unitElement);
        }
        outputModel.addContent(mappingElement);

        Element filterElement = new Element("filter");
        if(query.getChildren().size() == 1) {
            Element equals = new Element("equals");
            AdvancedSearchQueryTerm advancedQuery = (AdvancedSearchQueryTerm) query.getChildren().get(0);
            equals.addContent(new Element("concept").setAttribute("id", advancedQuery.getField().getCode()));
            equals.addContent(new Element("literal").setAttribute("value", ""+advancedQuery.getValues()[0]));
            filterElement.addContent(equals);
        }
        else {
            Element filterParent = new Element(query.getOperator() == CompoundSearchQuery.Operator.AND ? "and" : "or");
            filterElement.addContent(filterParent);
            for(Query q : query.getChildren()) {
                Element equals = new Element("equals");
                AdvancedSearchQueryTerm advancedQuery = (AdvancedSearchQueryTerm) q;
                equals.addContent(new Element("concept").setAttribute("id", advancedQuery.getField().getCode()));
                equals.addContent(new Element("literal").setAttribute("value", ""+advancedQuery.getValues()[0]));
                filterParent.addContent(equals);
            }
        }
        searchElement.addContent(filterElement);
        return searchElement;
    }

    public static void main(String[] args) throws Exception{
        List<int[]> valueList = new ArrayList<int[]>();
        BufferedReader reader = new BufferedReader(new FileReader(new File("C:\\Documents and Settings\\steve\\My Documents\\batchextract\\189_PO68_H11_2009-05-07_RawData.dat")));
        String line = null;
        while((line = reader.readLine()) != null) {
            String[] parts = line.split("\t");
            int[] partsArray = new int[parts.length];
            for(int i=0; i < parts.length; i++) {
                partsArray[i] = Integer.parseInt(parts[i]);
            }
            valueList.add(partsArray);
        }
        int[][] values = valueList.toArray(new int[valueList.size()][valueList.get(0).length]);
        MicrosatGraphPanel panel = new MicrosatGraphPanel(values);
        JFrame frame = new JFrame("Graphs");
        frame.setSize(640,480);
        frame.getContentPane().add(new JScrollPane(panel));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }



}
