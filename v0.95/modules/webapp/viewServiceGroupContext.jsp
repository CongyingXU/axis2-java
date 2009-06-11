<%@ page import="org.apache.axis2.Constants"%>
<%@ page import="org.apache.axis2.context.ConfigurationContext"%>
<%@ page import="org.apache.axis2.context.ServiceGroupContext"%>
<%@ page import="java.util.Hashtable"%>
<%@ page import="java.util.Iterator"%>
<%@ page import="java.util.Map"%>
<%--
  Created by IntelliJ IDEA.
  User: Indika Deepal
  Date: Sep 21, 2005
  Time: 10:41:24 AM
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<jsp:include page="include/adminheader.jsp"></jsp:include>
<h1>Runing Context hierachy</h1>
<%
    ConfigurationContext configContext = (ConfigurationContext)request.getSession().getAttribute(Constants.CONFIG_CONTEXT);
    Hashtable serviceGroupContextsMap = configContext.getServiceGroupContexts();
    String type = request.getParameter("TYPE");
    String sgID = request.getParameter("ID");
    ServiceGroupContext sgContext = (ServiceGroupContext)serviceGroupContextsMap.get(sgID);
    if(sgID !=null && sgContext !=null){
        if(type != null){
            if("VIEW".equals(type)){
             Map perMap = sgContext.getProperties();
             if(perMap.size()>0){
             %>
             <h4>Persistance properties</h4><ul>
             <%
                 Iterator itr = perMap.keySet().iterator();
                 while (itr.hasNext()) {
                     String key = (String) itr.next();
                     Object property =  perMap.get(key);
              %>
                   <li><%=key%> : <%=property.toString()%></li>
              <%
                 }
                 %></ul>
                 <%
             } else {
            %>
             <h4>No persistance properties found in the context</h4>
            <%
             }
            }   else if("DELETE".equals(type)){
                Object obj = serviceGroupContextsMap.remove(sgID);
                if(obj != null){
                 %>Reomoved the context <%
            }else {
                %>Unable to reomove the context <%
            }
            }
        }
    } else {
%> <h4>No Service Group Context found</h4><%
    }
%>
<jsp:include page="include/adminfooter.jsp"></jsp:include>