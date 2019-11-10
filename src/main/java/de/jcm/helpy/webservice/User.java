package de.jcm.helpy.webservice;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/user")
public class User
{
	private @Context ServletContext context;

	public UUID uuid;
	public String username;
	public String firstname;
	public String lastname;
	public int insuranceDetails;
	public Address address;
	public String emailAddress;
	public String telephoneNumber;

	@GET
	@Path("/self")
	@Produces( { MediaType.APPLICATION_JSON} )
	public static User self(@Context ContainerRequestContext request)
	{
		User user = new User();
		user.uuid = ((EntityInfo)request.getProperty("entity")).uuid;
		return user;
	}
}
