package io.jeannyil.camel.routes;

import jakarta.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

/* Routes to handle common HTTP errors

/!\ The @ApplicationScoped annotation is required for @Inject and @ConfigProperty to work in a RouteBuilder. 
	Note that the @ApplicationScoped beans are managed by the CDI container and their life cycle is thus a bit 
	more complex than the one of the plain RouteBuilder. 
	In other words, using @ApplicationScoped in RouteBuilder comes with some boot time penalty and you should 
	therefore only annotate your RouteBuilder with @ApplicationScoped when you really need it. */
public class HttpErrorRoute extends RouteBuilder {
	
	private static String logName = HttpErrorRoute.class.getName();

	@Override
	public void configure() throws Exception {
		
		/**
		 * Route that returns the common 500-Internal-Server-Error response in JSON format
		 */
		from("direct:common-500")
			.routeId("common-500-http-code-route")
			.log(LoggingLevel.INFO, logName, ">>> IN: headers:[${headers}] - body:[${body}]")
			.setHeader(Exchange.HTTP_RESPONSE_CODE, constant(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()))
			.setHeader(Exchange.HTTP_RESPONSE_TEXT, simple(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase()))
			.setHeader(Exchange.CONTENT_TYPE, simple("application/json"))
			.setBody()
				.method("errorResponseHelper", 
						"generateErrorResponse(${headers.CamelHttpResponseCode}, ${headers.CamelHttpResponseText}, ${exception})")
			.end()
			.marshal().json(JsonLibrary.Jackson, true)
			.convertBodyTo(String.class) // Stream caching is enabled on the CamelContext
			.log(LoggingLevel.INFO, logName, ">>> OUT: headers:[${headers}] - body:[${body}]")
		;
		
		/**
		 * Route that returns a custom error response in JSON format
		 * The following properties are expected to be set on the incoming Camel Exchange:
		 * <br>- errorId ({@link  io.jeannyil.camel.constants.APIConstants#ERROR_ID})
		 * <br>- errorDescription ({@link  io.jeannyil.camel.constants.APIConstants#ERROR_DESCRIPTION })
		 * <br>- errorMessage ({@link  io.jeannyil.camel.constants.APIConstants#ERROR_MESSAGE })
		 */
		from("direct:custom-http-error")
			.routeId("custom-http-error-route")
			.log(LoggingLevel.INFO, logName, ">>> IN: headers:[${headers}] - body:[${body}]")
			.setHeader(Exchange.CONTENT_TYPE, simple("application/json"))
			.setBody()
				.method("errorResponseHelper", 
						"generateErrorResponse(${exchangeProperty.errorId}, ${exchangeProperty.errorDescription}, ${exchangeProperty.errorMessage})")
			.end()
			.marshal().json(JsonLibrary.Jackson, true)
			.convertBodyTo(String.class) // Stream caching is enabled on the CamelContext
			.log(LoggingLevel.INFO, logName, ">>> OUT: headers:[${headers}] - body:[${body}]")
		;
	}

}