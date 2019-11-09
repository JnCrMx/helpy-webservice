package de.jcm.helpy.webservice;

import javax.annotation.security.PermitAll;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/user")
public class User
{
	public UUID uuid;
	public String username;
	public String firstname;
	public String lastname;
	public int insuranceDetails;
	public Address address;
	public String emailAddress;
	public String telephoneNumber;

	@GET
	@PermitAll
	@Path("/self")
	@Produces( { MediaType.APPLICATION_JSON} )
	public static User self(@Context ServletContext context)
	{
		User user = new User();
		user.uuid = UUID.randomUUID();
		return user;
	}
}
