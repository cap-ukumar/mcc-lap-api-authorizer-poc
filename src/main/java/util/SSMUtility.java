package util;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterResult;
import com.amazonaws.services.simplesystemsmanagement.model.Parameter;
//import org.slf4j.LoggerFactory;
//import org.slf4j.Logger;

public final class SSMUtility {
    //static Logger logger = LoggerFactory.getLogger(SSMUtility.class);
    static AWSSimpleSystemsManagement ssmClient = getSSMClient();

    private static AWSSimpleSystemsManagement getSSMClient(){

        String awsRegion = System.getenv().get("AWS_DEFAULT_REGION");

        //use the default region explicitly if know, makes java Lambdas faster per AWS documentation
        if(awsRegion != null){
            //logger.debug("Loaded SSM client region from environment: {}", awsRegion);
            System.out.println("Loaded SSM client region from environment: " +  awsRegion);
            return AWSSimpleSystemsManagementClientBuilder.standard()
                    .withCredentials(new EnvironmentVariableCredentialsProvider()) //According to AWS, this is fastest
                    .withRegion(awsRegion).build();
        }

        return AWSSimpleSystemsManagementClientBuilder.defaultClient();
    }

    private SSMUtility() {}

    public static String getParameter(String paramKey) {
        //logger.debug("paramkey: " + paramKey);
        System.out.println("paramkey: " + paramKey);
        GetParameterRequest parameterRequest = new GetParameterRequest().withName(paramKey);//.withWithDecryption(true);

        GetParameterResult parameterResult = ssmClient.getParameter(parameterRequest);

        Parameter parameter = parameterResult.getParameter();

        if (parameter == null) {
            //logger.error("Could not find parameter from SSM: {}", paramKey);
            System.err.println("Could not find parameter from SSM: " + paramKey);
            return null;
        }

        String result = parameter.getValue();

        if (result == null) {
            //logger.error("Parameter value is null or empty: {}", paramKey);
            System.err.println("Parameter value is null or empty: " +  paramKey);
            return null;
        }

        return result;
    }

    public static Integer getParameterInt(String paramKey) throws Exception{

        String initialResult = getParameter(paramKey);

        if (initialResult == null) {
            //logger.error("Parameter value is null or empty: {}", paramKey);
            System.err.println("Parameter value is null or empty: " + paramKey);
            return null;
        }

        try{
            return Integer.decode(initialResult);
        }
        catch (Exception ex){
            //logger.error("Expected numeric value for key: {}", paramKey);
            System.err.println("Expected numeric value for key: " +  paramKey);
            throw ex;
        }
    }
}