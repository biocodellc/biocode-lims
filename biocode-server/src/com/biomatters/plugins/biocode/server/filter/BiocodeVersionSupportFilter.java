package com.biomatters.plugins.biocode.server.filter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

/**
 * @author Frank Lee
 *         Created on 3/02/15 4:28 PM
 */
public class BiocodeVersionSupportFilter implements Filter {

    public static final String GENEIOUS_VERSION_STRING = "geneious_version";
    public static final String IGNORE_VERSION_CHECK = "ignore_version_check";

    private String minVersion;
    private String maxVersion;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        minVersion = filterConfig.getInitParameter("minVersion");
        maxVersion = filterConfig.getInitParameter("maxVersion");
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String version = request.getHeader(GENEIOUS_VERSION_STRING);
        String errorMsg = null;

        if (needCheckPath(request.getPathInfo()) && request.getHeader(IGNORE_VERSION_CHECK) == null) {
            if (version == null) {
                errorMsg = "Please submit a version number";
            } else {
                try {
                    if (compareVersion(version, minVersion) < 0 || compareVersion(version, maxVersion) > 0) {
                        errorMsg = "Your version is " + version + ", but we only support " + minVersion + "-" + maxVersion;
                    }
                } catch (NumberFormatException e) {
                    errorMsg = "Version format invalid";
                }
            }

            if (errorMsg != null) {
                reportError(errorMsg, response);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    public int compareVersion(String version, String version1) throws NumberFormatException {
        if (version.equals(version1)) {
            return 0;
        }

        String[] versions = version.split("\\.");
        String[] version1s = version1.split("\\.");

        for (int i = 0; i < versions.length; i++) {
            if (version1s.length <= i) {
                return 1;
            }

            if (versions[i].equals(version1s[i])) {
                continue;
            }

            return Integer.parseInt(versions[i]) - Integer.parseInt(version1s[i]);
        }

        if (version1s.length > versions.length) {
            return -1;
        } else {
            return 0;
        }
    }

    private boolean needCheckPath(String pathInfo) {
        //now check all REST API
        return pathInfo != null;
    }

    public void reportError(String msg, HttpServletResponse response) throws IOException {
        response.setStatus(400);
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(response.getOutputStream()));
            writer.write(msg);
            writer.flush();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void destroy() {
    }
}
