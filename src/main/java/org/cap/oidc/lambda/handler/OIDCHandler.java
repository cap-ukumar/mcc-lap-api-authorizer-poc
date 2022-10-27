package org.cap.oidc.lambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import org.json.simple.parser.ParseException;
import service.OESResponseHelper;
import service.OneLoginSigningKeyResolver;
import util.SSMUtility;

import java.util.ArrayList;
import java.util.List;

public class OIDCHandler implements RequestHandler<UserRequest, JSONObject> {

	private static OneLoginSigningKeyResolver signingKeyResolver = new OneLoginSigningKeyResolver();
	private static LambdaLogger globalLogger;
	private static String oesUrl = SSMUtility.getParameter("/cap/systems/oes/authservice/restful-url");
	private static CloseableHttpClient httpClient = HttpClients.createDefault();
	private static JwtParser jwtParser = Jwts.parserBuilder().setSigningKeyResolver(signingKeyResolver).build();
	private static String TOKEN_STATUS_API_END_POINT = SSMUtility
			.getParameter("/cap/systems/authservice/token-status-endpoint");
	private static ServiceHelper serviceHelper = null;

	public OIDCHandler() {
		signingKeyResolver.readOneLoginOIDCKeysInfo();
		serviceHelper = new ServiceHelper();
	}

	public JSONObject handleRequest(UserRequest userRequest, Context context) {

		LambdaLogger logger = context.getLogger();
		globalLogger = context.getLogger();
		boolean validToken = false;
		String capNumber = "";
		String userType = "";
		List<String> resourceArns = null;
		JSONObject policy = null;

		try {
			String methodArn = userRequest.getMethodArn();
			JSONObject headers = userRequest.getHeaders();
			logger.log("Inside OIDCHandler:handleRequest");
			logger.log("methodArn = " + methodArn);
			logger.log("headers = " + headers.toJSONString());

			String oidcToken = (String) headers.get("Authorization");
			logger.log("oidcToken = " + oidcToken);

			String orgId = "";
			if (headers.get("orgId") != null) {
				orgId = (String) headers.get("orgId");
				logger.log("orgId = " + orgId);
			} else if (headers.get("OrgId") != null) {
				orgId = (String) headers.get("OrgId");
				logger.log("OrgId = " + orgId);
			} else if (headers.get("orgid") != null) {
				orgId = (String) headers.get("orgid");
				logger.log("orgid = " + orgId);
			} else if (headers.get("Orgid") != null) {
				orgId = (String) headers.get("Orgid");
				logger.log("Orgid = " + orgId);
			} else {
				logger.log("orgId is null, no valid combination found: orgId, OrgId, orgid, Orgid");
				policy = createAuthPolicy("Deny", resourceArns, capNumber, userType);
				return policy;
			}

			resourceArns = createResourceArns(methodArn);

			if (oidcToken.startsWith("Bearer ")) {
				oidcToken = oidcToken.substring(7);
			}

			Jws<Claims> jws = jwtParser.parseClaimsJws(oidcToken);

			//This is specific for single logout - starts
			// Token is valid based on the signature. Make sure it is not invalidated.
			if (serviceHelper.isTokenInvalidated(TOKEN_STATUS_API_END_POINT, oidcToken, logger)) {
				logger.log("Token is invalidated. Throwing Unauthorized exception...");
				throw new RuntimeException("Unauthorized");
			}
			//This is specific for single logout - ends

			logger.log("Parsing cap num, user type and org id from token: " + jws.getBody());
			capNumber = jws.getBody().get("capNumber") + ""; // persons cap number/member id
			logger.log("capNumber: " + capNumber);
			userType = jws.getBody().get("userType") + "";
			logger.log("userType: " + userType);
			logger.log("orgId: " + orgId);
			String action = "isAllowed/cloudapps/ccs/evaluations/download";

			// Check for "External User.
			if (userType.equalsIgnoreCase("External User")) {
				logger.log("userType is external, so we must check OES");
				OESResponseHelper oesResponseHelper = new OESResponseHelper(oesUrl, httpClient);
				String isAllowed = oesResponseHelper.isAllowed(capNumber, orgId, "resources", action);
				logger.log("isAllowed: " + isAllowed);
				boolean allowed = parseResult(isAllowed);
				if (!allowed) {
					validToken = false;
					logger.log("User not allowed access by OES");
				} else {
					logger.log("user allowed access by OES");
					validToken = true;
				}
			} else {
				logger.log("userType is internal, skipping OES call");
				validToken = true;
			}
		} catch (Exception e) {
			logger.log("caught exception, token is invalid " + e.getMessage());
			validToken = false;
			throw new RuntimeException("Unauthorized");
		}

		logger.log("Creating auth policy");
		if (validToken) {
			policy = createAuthPolicy("Allow", resourceArns, capNumber, userType);
		} else {
			policy = createAuthPolicy("Deny", resourceArns, capNumber, userType);
		}

		logger.log("policy " + policy.toJSONString());

		return policy;
	}

	private List<String> createResourceArns(String methodArn) {

		List<String> resourceArns = new ArrayList<String>();
		resourceArns.add(methodArn);
		globalLogger.log("end of creating arns");

		return resourceArns;
	}

	@SuppressWarnings("unchecked")
	private JSONObject createAuthPolicy(String effect, List<String> resources, String capNumber, String userType) {
		globalLogger.log("start of creating auth policy");
		JSONObject statement = new JSONObject();
		statement.put("Action", "execute-api:Invoke");
		statement.put("Effect", effect);
		JSONArray resourcesList = new JSONArray();
		if (resources != null) {
			for (String res : resources) {
				resourcesList.add(res);
			}
		}
		statement.put("Resource", resourcesList);

		JSONObject policyDocument = new JSONObject();
		policyDocument.put("Version", "2012-10-17");
		JSONArray statementList = new JSONArray();
		statementList.add(statement);
		policyDocument.put("Statement", statementList);

		JSONObject contextObj = new JSONObject();
		contextObj.put("capnumber", capNumber);
		contextObj.put("usertype", userType);

		JSONObject authPolicy = new JSONObject();
		authPolicy.put("principalId", "user:" + capNumber);
		authPolicy.put("policyDocument", policyDocument);
		authPolicy.put("context", contextObj);
		globalLogger.log("end of creating auth policy");
		return authPolicy;
	}

	public boolean parseResult(String result) throws ParseException {
		globalLogger.log("trying to parse result from: " + result);
		if (result != null && result.contains("true")) {
			return true;
		}

		return false;
	}
}
