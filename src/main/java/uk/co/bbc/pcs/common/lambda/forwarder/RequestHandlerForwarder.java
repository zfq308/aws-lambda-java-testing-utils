package uk.co.bbc.pcs.common.lambda.forwarder;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.bbc.pcs.common.lambda.jackson.AwsLambdaEventsObjectMapper;
import uk.co.bbc.pcs.common.lambda.mock.MockContextBuilder;

import java.io.IOException;
import java.util.Optional;

/**
 * Forwards requests on to the given RequestHandler.
 */
public class RequestHandlerForwarder implements RequestForwarder {

    private static final int MOCK_EXECUTION_TIME_LIMIT = 60000;
    private static Logger logger = LoggerFactory.getLogger(RequestHandlerForwarder.class);

    private final RequestHandler requestHandler;
    private final Class<?> eventClass;
    private final String lambdaFunctionName;
    private final ObjectMapper objectMapper;

    public RequestHandlerForwarder(RequestHandler requestHandler, Class<?> eventClass, String lambdaFunctionName) {
        this.requestHandler = requestHandler;
        this.eventClass = eventClass;
        this.lambdaFunctionName = lambdaFunctionName;
        logger.info("Got {} event type for request handler {} with function name {}",
                    eventClass, requestHandler, lambdaFunctionName);
        objectMapper = new AwsLambdaEventsObjectMapper();
    }

    @Override
    public Optional<String> forwardRequest(String requestBody) {
        try {
            Object request = parseRequestBody(requestBody);
            String response = forwardToRequestHandler(request);
            return Optional.of(response);
        } catch (Exception e) {
            logger.error("Exception thrown when handling request", e);
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private String forwardToRequestHandler(Object request) throws JsonProcessingException {
        final String functionName = (this.lambdaFunctionName == null || this.lambdaFunctionName.isEmpty()) ?
                                    requestHandler.getClass().getName() :
                                    this.lambdaFunctionName;
        Context context = new MockContextBuilder(functionName, logger::info)
                .withRemainingTimeInMillis(MOCK_EXECUTION_TIME_LIMIT)
                .withRemainingTimeCountdown(true)
                .createMockContext();
        Object response = requestHandler.handleRequest(request, context);
        return objectMapper.writeValueAsString(response);
    }

    private Object parseRequestBody(String rawRequestBody) throws IOException {
        return objectMapper.readValue(rawRequestBody, objectMapper.getTypeFactory().constructType(eventClass));
    }

}
