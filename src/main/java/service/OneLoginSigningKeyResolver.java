package service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class OneLoginSigningKeyResolver extends SigningKeyResolverAdapter{

    private String defaultOneLoginJwksUri = "https://onelogin.cap.org/oidc/2/certs";
    private Map<String, PublicKey> oneLoginSigningKeysMap = new HashMap<String, PublicKey>();

    public void readOneLoginOIDCKeysInfo() {

        String oneLoginJwksUri = System.getenv("ONE_LOGIN_JWKS_URI");
        if( (oneLoginJwksUri == null) || oneLoginJwksUri.equals("")) {
            oneLoginJwksUri = defaultOneLoginJwksUri;
        }

        System.out.println("START - Reading OneLogin signing keys from URL: " + oneLoginJwksUri + "...");

        try {

            String responseStr = "";
            URL oneLoginCerts = new URL(oneLoginJwksUri);
            URLConnection urlConn = oneLoginCerts.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                responseStr += inputLine;
            }
            in.close();

            System.out.println("OneLogin Keys Info:  " + responseStr);

            JSONObject mainObj = (JSONObject) (new JSONParser()).parse(responseStr);

            JSONArray keys = (JSONArray) mainObj.get("keys");
            Iterator keysItr = keys.iterator();
            JSONObject keyObj;
            String kid;
            String n;
            String e;
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey publicKey = null;
            BigInteger modulus;
            BigInteger exponent;
            while (keysItr.hasNext()) {

                keyObj = (JSONObject) keysItr.next();

                kid = keyObj.get("kid").toString();
                n = keyObj.get("n").toString();
                e = keyObj.get("e").toString();

                modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
                exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));
                publicKey = kf.generatePublic(new RSAPublicKeySpec(modulus, exponent));

                oneLoginSigningKeysMap.put(kid, publicKey);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("END - Reading of OneLogin signing keys");
    }

    @Override
    public Key resolveSigningKey(JwsHeader jwsHeader, Claims claims) {

        String keyId = jwsHeader.getKeyId();
        PublicKey publicKey = oneLoginSigningKeysMap.get(keyId);

        if(publicKey == null) {
            System.out.println("kid: " + keyId + " not found. May be kid has changed at OneLogin. Try to fetch new signing key information...");
            readOneLoginOIDCKeysInfo();
            publicKey = oneLoginSigningKeysMap.get(keyId);

            if(publicKey == null) {
                System.out.println("Even after reading new signing keys from OneLogin, kid is still null");
            }
        }

        return publicKey;

    }

}
