<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@ taglib prefix = "c" uri = "http://java.sun.com/jsp/jstl/core" %>

<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8">
    <title>CCS config files</title>
    <c:set var="context" value="${pageContext.request.contextPath}" />
    <script type="module" src="${context}/file-browser.bundled.js"></script>
    <style>
      p {
        border: solid 1px blue;
        padding: 8px;
      }
    </style>
  </head>
  <body>
    <h1>CCS Config files</h1>
    <file-browser context="${context}" filePrefix="${context}" restURL="${context}/rest/"/>
  </body>
</html>
