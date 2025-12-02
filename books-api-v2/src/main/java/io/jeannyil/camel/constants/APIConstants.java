package io.jeannyil.camel.constants;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Common constants
 */
@RegisterForReflection // Lets Quarkus register this class for reflection during the native build
public class APIConstants {

	public static final String ERROR_ID = "errorId";
	public static final String ERROR_DESCRIPTION = "errorDescription";
	public static final String ERROR_MESSAGE = "errorMessage";
	
}
