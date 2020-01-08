package de.jcm.helpy.webservice.context;

import de.jcm.helpy.webservice.authentication.AuthenticationFilter;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import java.io.IOException;

public class HelpyApplication extends ResourceConfig implements ContainerResponseFilter
{
	public HelpyApplication()
	{
		packages("de.jcm.helpy.webservice");
		register(LoggingFeature.class);

		//Register Auth Filter here
		register(AuthenticationFilter.class);
		register(GNUTerryPratchettFilter.class);
		register(HelpyApplication.class);
	}

	@Override
	public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
			throws IOException
	{
		responseContext.getHeaders().add("Access-Control-Allow-Origin", "*");
	}
}