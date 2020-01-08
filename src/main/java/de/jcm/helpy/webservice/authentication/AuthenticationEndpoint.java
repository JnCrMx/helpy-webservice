package de.jcm.helpy.webservice.authentication;

import at.favre.lib.crypto.bcrypt.BCrypt;
import de.jcm.helpy.webservice.util.SQLUtil;
import org.apache.commons.codec.binary.Hex;

import javax.annotation.security.PermitAll;
import javax.naming.AuthenticationException;
import javax.servlet.ServletContext;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.security.SecureRandom;
import java.sql.*;
import java.util.Calendar;
import java.util.Random;
import java.util.UUID;

@Path("/authentication")
public class AuthenticationEndpoint
{
	@Context
	private ServletContext context;

	private Random random;
	private PreparedStatement selectUser;
	private PreparedStatement insertToken;

	@Context
	private void prepare() throws SQLException
	{
		random = new SecureRandom();

		Connection connection = (Connection) context.getAttribute("connection");

		selectUser = connection.prepareStatement(
				"SELECT uuid, password FROM user WHERE username = ?");
		insertToken = connection.prepareStatement(
				"INSERT INTO auth_token(token, uuid, expiration) VALUES(?, ?, ?)");
	}

	/**
	 * @api {post} /authentication/user Login
	 * @apiGroup Authentication
	 *
	 * @apiParam {String} username Username to login with.
	 * @apiParam {String} password Password to login with.
	 * @apiParamExample {String} Parameter-Example
	 *  username=testuser&password=testpassword
	 *
	 * @apiSuccess {String} token Access token for API use.
	 *
	 * @apiExample {Java} Example usage
	 * 	HelpyApi api = new HelpyApi();
	 *	TokenProvider provider = new LoginTokenProvider("testuser", "testpassword");
	 *	if(api.authenticate(provider))
	 *	{
	 *	   // Authentication successful!
	 *	   // Continue
	 *	   ...
	 *	}
	 *	else
	 *	{
	 *	   // Authentication NOT successful!
	 *	   // Abort with error!
	 *	}
	 */
	@POST
	@PermitAll
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
			return Response
					.status(Response.Status.UNAUTHORIZED.getStatusCode(),
							"Username or password wrong!")
					.build();
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
			throw new AuthenticationException("username not recognized");

		String hash = result.getString("password");

		if(!BCrypt.verifyer().verify(password.getBytes(), hash.getBytes()).verified)
			throw new AuthenticationException("failed to verify password");

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
