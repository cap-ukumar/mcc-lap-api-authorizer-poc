package org.cap.oidc.lambda.handler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import com.amazonaws.services.lambda.runtime.LambdaLogger;

public class ServiceHelper {

	/**
	 * This method will check the token validation using the token status API end
	 * point invoking DynamoDB table login_oidc_slo
	 * 
	 * @param user
	 * @param orgId
	 * @param inLogger
	 * @return
	 */
	public boolean isTokenInvalidated(String tokenStatusApiEndPoint, String token, LambdaLogger logger) {

		boolean tokenInvalidated = false;
		StringBuilder sb = new StringBuilder();
		long startTime = System.currentTimeMillis();

		try {
			URL url = new URL(tokenStatusApiEndPoint);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");

			conn.setRequestProperty("Accept", "*/*");
			conn.setRequestProperty("Connection", "keep-alive");

			conn.setRequestProperty("Authorization", token);
			logger.log("Token Status API End Point: " + tokenStatusApiEndPoint);

			if (conn.getResponseCode() != 200) {

				logger.log("ERROR::Failure in calling the token service. HTTP error code returned: "
						+ conn.getResponseCode());
				logger.log("Message = " + conn.getResponseMessage());

			} else {

				BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

				String output;
				while ((output = br.readLine()) != null) {
					sb.append(output);
				}
				logger.log("Token Service output is : " + sb.toString());

				if (sb.toString().contains("invalidated")) {
					tokenInvalidated = true;
				}
			}
			conn.disconnect();

		} catch (MalformedURLException e) {
			logger.log("ERROR::MalformedURLException in token service: " + e.getMessage());
		} catch (IOException e) {
			logger.log("ERROR::IOException in token service: " + e.getMessage());
		}

		long endTime = System.currentTimeMillis();
		logger.log("returning from isTokenInvalidated(): " + tokenInvalidated + " - took " + (endTime - startTime)
				+ " ms");

		return tokenInvalidated;
	}

}