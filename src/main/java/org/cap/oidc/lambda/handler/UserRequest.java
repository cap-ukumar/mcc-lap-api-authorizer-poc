package org.cap.oidc.lambda.handler;

import org.json.simple.JSONObject;

public class UserRequest {

    String methodArn;
    JSONObject headers;

    public UserRequest(String methodArn, JSONObject headers) {
        this.methodArn = methodArn;
        this.headers = headers;
    }

    public UserRequest() {}

	public String getMethodArn() {
		return methodArn;
	}

	public void setMethodArn(String methodArn) {
		this.methodArn = methodArn;
	}

	public JSONObject getHeaders() {
		return headers;
	}

	public void setHeaders(JSONObject headers) {
		this.headers = headers;
	}
	
}
