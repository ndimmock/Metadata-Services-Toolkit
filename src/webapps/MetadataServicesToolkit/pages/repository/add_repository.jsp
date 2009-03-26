<!--
  * Copyright (c) 2009 University of Rochester
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the  
  * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
  * website http://www.extensiblecatalog.org/. 
  *
  -->

<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core_rt"%>

<c:import url="/inc/doctype-frag.jsp"/>
<LINK href="page-resources/css/header.css" rel="stylesheet" type="text/css">
<html>
    
    <head>
        <title>Add Repository</title>
        <c:import url="/inc/meta-frag.jsp"/>

        <LINK href="page-resources/yui/reset-fonts-grids/reset-fonts-grids.css" rel="stylesheet" type="text/css" >
        <LINK href="page-resources/css/base-mst.css" rel="stylesheet" type="text/css" >
        <LINK href="page-resources/yui/menu/assets/skins/sam/menu.css"  rel="stylesheet" type="text/css" >
        <LINK href="page-resources/css/global.css" rel="stylesheet" type="text/css" >
        <LINK href="page-resources/css/main_menu.css" rel="stylesheet" type="text/css" >
        <LINK HREF="page-resources/css/bodylayout.css" REL="stylesheet" TYPE="text/css">
        <LINK href="page-resources/css/tables.css" rel="stylesheet" type="text/css" >

        <SCRIPT LANGUAGE="JavaScript" SRC="pages/js/base_path.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/js/utilities.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" src="page-resources/yui/yahoo-dom-event/yahoo-dom-event.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" src="page-resources/yui/connection/connection-min.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" src="page-resources/yui/container/container_core-min.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/yui/menu/menu-min.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/js/add_repository.js"></SCRIPT>
        <script type="javascript" src="http://yui.yahooapis.com/combo?2.5.2/build/yuiloader/yuiloader-beta-min.js"></script>
        <script type="text/javascript" src="page-resources/js/alert.js"></script>
        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/js/main_menu.js"></SCRIPT>
       
    </head>



</head>
<body class="yui-skin-sam">
   <%@ taglib prefix="s" uri="/struts-tags" %>
        <!--  yahoo doc 2 template creates a page 950 pixles wide -->
        <div id="doc2">

		<!-- page header - this uses the yahoo page styling -->
		<div id="hd">

            <!--  this is the header of the page -->
            <c:import url="/inc/header.jsp"/>

            <!--  this is the header of the page -->
            <c:import url="/inc/menu.jsp"/>
             <jsp:include page="/inc/breadcrumb.jsp">

                    <jsp:param name="bread" value="Repository , Add Repository" />

            </jsp:include>

            <div id="error_div"></div>
            <!-- Display of error message -->
                <c:if test="${errorType != null}">
                    <div class="${errorType}">
                        <img  src="${pageContext.request.contextPath}/page-resources/img/${errorType}.jpg">
                        <s:fielderror cssClass="errorMessage"/>
                    </div>
                 </c:if> 
 		</div>
		<!--  end header -->
         
		<!-- body -->
		<div id="bd">
       
        <form action="/MetadataServicesToolkit/addRepository.action" name="addRepositoryForm" method="post">

        <div style="margin-top:25px;margin-left:0px;font-family: verdana,sans-serif;font-size: 12px;">

                 OAI-PMH <B>Repository Name</B> <br>
                 <input type="text" id="repositoryName" name="repositoryName" value="${repositoryName}" style="height:25px;width:305px;" maxlength="255"> <br><br>
                 OAI-PMH <B>Repository URL</B> <br>
                 <input type="text" id="repositoryURL" name="repositoryURL" value="${repositoryURL}" style="height:25px;width:470px;" maxlength="255">
                
                 <div style="margin-top:20px;margin-left:0px;vertical-align:bottom;">
                   <button style="vertical-align:bottom;" class="xc_button_small" type="button" onclick="javascript:YAHOO.xc.mst.repository.cancel();" name="cancel">Cancel</button> &nbsp;&nbsp;&nbsp;
                   <button class="xc_button" type="button" name="add" onclick="javascript:YAHOO.xc.mst.repository.AddValidator();">Save</button>
                 </div>
        </div>
        
        </form>
        </div>
        </div>
    </body>
    </html>

