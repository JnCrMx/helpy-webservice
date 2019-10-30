package de.jcm.helpy.webservice;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.security.Key;

@Path("/authentication")
public class AuthenticationEndpoint
{
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	public Response authenticateUser(@FormParam("username") String username,
			@FormParam("password") String password)
	{
		try
		{
			// Authenticate the user using the credentials provided
			authenticate(username, password);

			// Issue a token for the user
			String token = issueToken(username);

			// Return the token on the response
			return Response.ok(token).build();
		}
		catch (Exception e)
		{
			return Response.status(Response.Status.FORBIDDEN).build();
		}
	}

	private void authenticate(String username, String password) throws Exception
	{
		// Authenticate against a database, LDAP, file or whatever
		// Throw an Exception if the credentials are invalid
	}

	private String issueToken(String username)
	{
		// Issue a token (can be a random String persisted to a database or a JWT token)
		// The issued token must be associated to a user
		// Return the issued token

		Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);

		return Jwts.builder().setSubject(username).signWith(key).compact();
	}
}
