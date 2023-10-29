package io.jeannyil.camel.routes;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;

import io.jeannyil.models.Author;
import io.jeannyil.models.BookV2;

/* BooksApi route definition

/!\ The @ApplicationScoped annotation is required for @Inject and @ConfigProperty to work in a RouteBuilder. 
	Note that the @ApplicationScoped beans are managed by the CDI container and their life cycle is thus a bit 
	more complex than the one of the plain RouteBuilder. 
	In other words, using @ApplicationScoped in RouteBuilder comes with some boot time penalty and you should 
	therefore only annotate your RouteBuilder with @ApplicationScoped when you really need it. */
@ApplicationScoped
public class BooksApiRoute extends RouteBuilder {

    private static String logName = BooksApiRoute.class.getName();
    private final Set<BookV2> books = Collections.synchronizedSet(new LinkedHashSet<>());

    @Inject
	CamelContext camelctx;

    public BooksApiRoute() {

        /* Let's add some initial books */
        this.books.add(new BookV2(new Author("1797-08-30T00:00:00.000Z", "Mary Shelley"), 10, "Frankenstein", 1818));
        this.books.add(new BookV2(new Author("1812-02-07T00:00:00.000Z", "Charles Dickens"), 5, "A Christmas Carol", 1843));
        this.books.add(new BookV2(new Author("1775-12-16T00:00:00.000Z", "Jane Austen"), 3, "Pride and Prejudice", 1813));

    }
    
    @Override
    public void configure() throws Exception {

        // Enable Stream caching
        camelctx.setStreamCaching(true);
        // Enable use of breadcrumbId
        camelctx.setUseBreadcrumb(true);

        // Catch unexpected exceptions
		onException(Exception.class)
            .handled(true)
            .maximumRedeliveries(0)
            .log(LoggingLevel.ERROR, logName, ">>> Caught exception: ${exception.stacktrace}")
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()))
			.setHeader(Exchange.HTTP_RESPONSE_TEXT, constant(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase()))
			.setHeader(Exchange.CONTENT_TYPE, constant(MediaType.TEXT_PLAIN))
			.setBody(simple("${exception}"))
            .log(LoggingLevel.INFO, logName, ">>> OUT: headers:[${headers}] - body:[${body}]")
        ;
        
        //REST configuration with Camel Quarkus Platform HTTP component
        restConfiguration()
            .component("platform-http")
            .enableCORS(true)
            .bindingMode(RestBindingMode.off) // RESTful responses will be explicitly marshaled for logging purposes
            .dataFormatProperty("prettyPrint", "true")
            .contextPath("/api/v2")
        ;

        // REST endpoint for the FruitsAndLegumesApi OpenAPI specification
        rest()
            .produces(MediaType.APPLICATION_JSON)
            .get("/openapi.json")
                .id("get-oas-route")
                .description("Gets the OpenAPI specification for this service in JSON format")
                .to("direct:getOAS")
        ;

        // Returns the OAS
        from("direct:getOAS")
            .log(LoggingLevel.INFO, logName, ">>> IN: headers:[${headers}] - body:[${body}]")
            .setHeader(Exchange.CONTENT_TYPE, constant("application/vnd.oai.openapi+json"))
            .setBody().constant("resource:classpath:openapi/openapi.json")
            .log(LoggingLevel.INFO, logName, ">>> OUT: headers:[${headers}] - body:[${body}]")
        ;

        // REST endpoint for the Books API
        rest("/books")
            .get()
                .id("get-books-v2-route")
                .produces(MediaType.APPLICATION_JSON)
                .description("Gets a list of all `Books-v1` entities.")
                // Call the getFruits route
                .to("direct:getBooks-v2")
        ;
        
        // Implements the getFruits operation
        from("direct:getBooks-v2")
            .routeId("getBooks-v2")
            .log(LoggingLevel.INFO, logName, ">>> Processing GET books request...")
            .setBody()
                .constant(books)
            .marshal().json(JsonLibrary.Jackson, true)
            .log(LoggingLevel.INFO, logName, ">>> Sending GET books response: ${body}")
        ;

    }
}
