package de.jcm.helpy.webservice.authentication;

import javax.servlet.ServletContext;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

@Path("/authentication")
public class AuthenticationEndpoint
{
	private @Context ServletContext context;

	private PreparedStatement checkUser;

	@Context
	private void prepare() throws SQLException
	{
		Connection connection = (Connection) context.getAttribute("connection");

		System.out.println("checkUser = " + checkUser);

		checkUser = connection.prepareStatement(
				"SELECT uuid FROM user WHERE username=? AND password=?");

		System.out.println("checkUser = " + checkUser);
	}

	@POST
	@Path("/user")
	@Produces(MediaType.TEXT_PLAIN)
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	public Response authenticateUser(@FormParam("username") String username,
			@FormParam("password") String password)
	{
		try
		{
			UUID uuid = authenticate(username, password);
			String token = issueToken(uuid);

			return Response.ok(token).build();
		}
		catch (Exception e)
		{
			return Response.status(Response.Status.FORBIDDEN).build();
		}
	}

	private UUID authenticate(String username, String password) throws Exception
	{

		return null;
	}

	private String issueToken(UUID uuid)
	{
		return "";
	}
}
