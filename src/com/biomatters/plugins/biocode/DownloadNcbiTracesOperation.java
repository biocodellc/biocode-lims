package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;
//import org.apache.commons.httpclient.HttpClient;
//import org.apache.commons.httpclient.HttpConnection;
//import org.apache.commons.httpclient.HttpState;
//import org.apache.commons.httpclient.NameValuePair;
//import org.apache.commons.httpclient.methods.PostMethod;
//import org.apache.http.impl.DefaultHttpClientConnection;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 7/08/12 5:39 PM
 */


public class DownloadNcbiTracesOperation extends DocumentOperation {

    @Override
    public GeneiousActionOptions getActionOptions() {
        return new GeneiousActionOptions("Download from the NCBI Trace Archive").setMainMenuLocation(GeneiousActionOptions.MainMenu.Tools);
    }

    @Override
    public String getHelp() {
        return null;
    }

    @Override
    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[0];
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        Options options = new Options(this.getClass());
        options.addStringOption("query", "query", "PROJECT_NAME = \"BARCODE\"");
        return options;
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options) throws DocumentOperationException {
        int PAGE_SIZE = 2;
        try {
            String query = options.getValueAsString("query");
            int count = 0;
            InputStream countInput = queryServer("query count " + query);
            // Get the response
            BufferedReader rd = new BufferedReader(new InputStreamReader(countInput));
            String line;
            while ((line = rd.readLine()) != null) {
                count = Integer.parseInt(line.trim());
            }
            rd.close();
            
            int numberOfPages = (int)Math.ceil(count/PAGE_SIZE);

            CompositeProgressListener composite = new CompositeProgressListener(progressListener, numberOfPages);
            
            for(int page=0; page < numberOfPages; page++) {
                InputStream inputStream = queryServer("query page_size " + PAGE_SIZE + " page_number " + page + " text " + query);
                File outputFile = new File(System.getProperty("user.home")+File.separator+"traceData"+File.separator+"part"+page+".tgz");
                BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
                BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile));
                List<String> ids = new ArrayList<String>();
                String line2 = null;
                while((line2 = in.readLine()) != null) {
                    if(composite.isCanceled()) {
                        break;
                    }
                    System.out.println(line2);
                    //out.write(n);
                    ids.add(line2);
                }
                if(composite.isCanceled()) {
                    break;
                }
                in.close();
                InputStream tgzIn = queryServer("retrieve_gz all "+ StringUtilities.join(", ", ids));
                int n;
                int byteCount = 0;
                while((n = tgzIn.read()) >= 0) {
                    byteCount++;
                    composite.setMessage("Downloaded "+byteCount+" bytes");
                    if(composite.isCanceled()) {
                        break;
                    }
                    //out.write(n);
                }
                tgzIn.close();
                out.close();
                if(composite.isCanceled()) {
                    break;
                }
                composite.beginNextSubtask();
                break;
                //out.close();
            }


        }
        catch(IOException ex) {
            ex.toString();
            ex.printStackTrace();
            throw new DocumentOperationException(ex.getMessage(), ex);
        }
        return Collections.emptyList();
    }

    private InputStream queryServer(String countQuery) throws IOException {
        String data = URLEncoder.encode("query", "UTF-8") + "=" + URLEncoder.encode(countQuery, "UTF-8");
        URL url = new URL("http://trace.ncbi.nlm.nih.gov/Traces/trace.cgi?cmd=raw");
        URLConnection conn = url.openConnection();
        conn.setDoOutput(true);
        OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
        wr.write(data);
        wr.flush();

        return conn.getInputStream();
    }
    
//    private InputStream queryServer(String countQuery) throws IOException {
//        PostMethod post = new PostMethod("http://trace.ncbi.nlm.nih.gov/Traces/trace.cgi?cmd=raw");
//        NameValuePair[] data = {
//          new NameValuePair("query", countQuery)
//        };
//        post.setRequestBody(data);
//        HttpClient client = new HttpClient();
//        client.executeMethod(post);
//        return post.getResponseBodyAsStream();
//    }

}
