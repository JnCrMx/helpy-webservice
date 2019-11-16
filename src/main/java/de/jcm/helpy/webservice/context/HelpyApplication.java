package de.jcm.helpy.webservice.context;

import de.jcm.helpy.webservice.authentication.AuthenticationFilter;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.server.ResourceConfig;

public class HelpyApplication extends ResourceConfig
{
	public HelpyApplication()
	{
		packages("de.jcm.helpy.webservice");
		register(LoggingFeature.class);

		//Register Auth Filter here
		register(AuthenticationFilter.class);
		register(GNUTerryPratchettFilter.class);
	}
}