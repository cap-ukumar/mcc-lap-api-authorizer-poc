package service;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;


public class OESResponseHelper {

    private static String BASE_URL=null;
    private static CloseableHttpClient httpClient = null;

    public OESResponseHelper(String oesUrl, CloseableHttpClient closeableHttpClient) {
        super();
        BASE_URL = oesUrl;
        httpClient = closeableHttpClient;
    }

    public String isAllowed(String user, String orgId, String resource, String action) {

        String response = null;

        action = action.replaceAll(" ","%20");
        StringBuffer sb = new StringBuffer();
        sb.append(BASE_URL + "/" + action + "?user=" + user);
        if(orgId != null && !orgId.trim().equals("")){
            sb.append("&selectedorg="+orgId.trim());
        }else{
            sb.append("&selectedorg=");
        }
        System.out.println("Calling Rest with url :" + sb);
        response = invokeRestServlet2(sb.toString());

        return (response);
    }

    public String invokeRestServlet2(String restURLString) {
        String retString = null;
        System.out.println("Rest url: " + restURLString);
        HttpGet request = new HttpGet(restURLString);
        try {
            CloseableHttpResponse response = httpClient.execute(request);

            // Get HttpResponse Status
            System.out.println(response.getProtocolVersion());
            System.out.println(response.getStatusLine().getStatusCode());
            System.out.println(response.getStatusLine().getReasonPhrase());
            System.out.println(response.getStatusLine().toString());
            org.apache.http.HttpEntity entity = response.getEntity();
            if (entity != null) {
                String result = EntityUtils.toString(entity);
                System.out.println(result);
                retString = result;
            }
    } catch (Exception e) {
        e.printStackTrace();
    }
        return retString;
    }
}
