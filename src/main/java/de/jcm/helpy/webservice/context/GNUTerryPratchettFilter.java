package de.jcm.helpy.webservice.context;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import java.io.IOException;

public class GNUTerryPratchettFilter implements ContainerResponseFilter
{
	@Override
	public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
			throws IOException
	{
		responseContext.getHeaders().add("X-Clacks-Overhead", "GNU Terry Pratchett");
	}
}
