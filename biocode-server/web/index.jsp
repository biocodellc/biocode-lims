<%@ page import="com.biomatters.plugins.biocode.server.LIMSInitializationServlet" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
  <head>
    <title>Moorea Biocode LIMS Server</title>
  </head>
  <body>
    Under Construction
    <%
        String errors = LIMSInitializationServlet.getErrors();
        if(!errors.isEmpty()) {
            out.println(errors);
        }
    %>
  </body>
</html>
