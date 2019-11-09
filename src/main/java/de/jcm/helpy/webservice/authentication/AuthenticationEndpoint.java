package de.jcm.helpy.webservice.authentication;

import at.favre.lib.crypto.bcrypt.BCrypt;
import de.jcm.helpy.webservice.util.SQLUtil;
import org.apache.commons.codec.binary.Hex;

import javax.naming.AuthenticationException;
import javax.servlet.ServletContext;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.security.SecureRandom;
import java.sql.*;
import java.util.Base64;
import java.util.Calendar;
import java.util.Random;
import java.util.UUID;

@Path("/authentication")
public class AuthenticationEndpoint
{
	private @Context ServletContext context;

	private @Context Random random;
	private @Context PreparedStatement selectUser;
	private @Context PreparedStatement insertToken;

	@Context
	private void prepare() throws SQLException
	{
		random = new SecureRandom();

		Connection connection = (Connection) context.getAttribute("connection");

		selectUser = connection.prepareStatement(
				"SELECT uuid, password FROM user WHERE username=?");
		insertToken = connection.prepareStatement(
				"INSERT INTO auth_token(token, uuid, expiration) VALUES(?, ?, ?)");
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
		catch(AuthenticationException e)
		{
			return Response.status(Response.Status.FORBIDDEN).build();
		}
		catch(SQLException e)
		{
			return Response.serverError().build();
		}
	}

	private UUID authenticate(String username, String password) throws AuthenticationException, SQLException
	{
		selectUser.setString(1, username);
		ResultSet result = selectUser.executeQuery();

		if(!result.next())
			throw new AuthenticationException("username not present");

		String hash = result.getString("password");

		if(!BCrypt.verifyer().verify(password.getBytes(), hash.getBytes()).verified)
			throw new AuthenticationException("couldn't verify password");

		return SQLUtil.UUIDHelper.fromBytes(result.getBytes("uuid"));
	}

	private String issueToken(UUID uuid) throws SQLException
	{
		byte[] tokenBytes = new byte[16];
		random.nextBytes(tokenBytes);

		String token = Hex.encodeHexString(tokenBytes).toUpperCase();
		Calendar expiration = Calendar.getInstance();
		expiration.add(Calendar.HOUR, 1);

		insertToken.setString(1, token);
		insertToken.setBytes(2, SQLUtil.UUIDHelper.toBytes(uuid));
		insertToken.setTimestamp(3, Timestamp.from(expiration.toInstant()));
		insertToken.execute();

		return token;
	}
}
