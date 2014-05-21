<%@ page import="com.biomatters.plugins.biocode.server.LIMSInitializationListener" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
  <head>
    <title>Moorea Biocode LIMS Server (Alpha)</title>
  </head>
  <body>
    <h1>Welcome to the Moorea Biocode LIMS Server</h1>
    <h2>Under Construction</h2>
    <p>The Moorea Biocode LIMS is still under heavy development.  This is an
        <a href="http://en.wiktionary.org/wiki/alpha_version">alpha release</a>,
    meaning all APIs provided by the server are subject to modification and should not be relied upon.</p>

    <p>The server configuration file is located at
        <%
            out.println(LIMSInitializationListener.getPropertiesFile().getAbsolutePath());
        %>
    </p>

    <p>Please report any errors to support@mooreabiocode.org</p>
    <%
        String errors = LIMSInitializationListener.getErrorText();
        if(errors != null && !errors.isEmpty()) {
            out.println("<h2>Initialization Errors</h2>");
            out.println(errors);
        }
    %>
  </body>
</html>
