package de.jcm.helpy.webservice.authentication;

import de.jcm.helpy.EntityInfo;
import de.jcm.helpy.webservice.util.SQLUtil;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.servlet.ServletContext;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

@Provider
public class AuthenticationFilter implements ContainerRequestFilter
{
	private static final String AUTHORIZATION_PROPERTY = "Authorization";
	private static final String AUTHENTICATION_SCHEME = "Bearer";

	private @Context ResourceInfo resourceInfo;
	private @Context ServletContext context;

	private PreparedStatement selectToken;

	@Context
	private void prepare() throws SQLException
	{
		Connection connection = (Connection) context.getAttribute("connection");

		selectToken = connection.prepareStatement(
				"SELECT e.uuid AS uuid, e.role AS role "
						+ "FROM auth_token t, entity e "
						+ "WHERE t.uuid = e.uuid "
						  + "AND t.token = ? "
						  + "AND ( t.expiration > CURRENT_TIMESTAMP()"
						+ " OR t.expiration IS NULL)");
	}

	@Override
	public void filter(ContainerRequestContext requestContext)
	{
		Method method = resourceInfo.getResourceMethod();

		/*
		Why not put
		>	!method.isAnnotationPresent(PermitAll.class)
		here?
		There are some methods that MIGHT return more/other information to authorized user.
		Therefore continue authorization even if it is not required by method.
		 */

		//Access denied for all
		if(method.isAnnotationPresent(DenyAll.class))
		{
			requestContext.abortWith(Response
					.status(Response.Status.FORBIDDEN.getStatusCode(),
							"Access blocked for all users!")
					.build());
			return;
		}

		//Get request headers
		MultivaluedMap<String, String> headers = requestContext.getHeaders();

		//Fetch authorization header
		List<String> authorization = headers.get(AUTHORIZATION_PROPERTY);

		//If no authorization information present
		if(authorization == null || authorization.isEmpty())
		{
			if(!method.isAnnotationPresent(PermitAll.class))
			{
				requestContext.abortWith(
						Response.status(Response.Status.UNAUTHORIZED.getStatusCode(),
								"No Authorization header found!").build());
			}
			return;
		}

		String token = authorization.get(0)
				.replaceFirst(AUTHENTICATION_SCHEME + " ", "");

		try
		{
			EntityInfo entityInfo = verifyToken(token);
			requestContext.setProperty("entity", entityInfo);

			//Verify user access
			if(method.isAnnotationPresent(RolesAllowed.class))
			{
				RolesAllowed rolesAnnotation = method.getAnnotation(RolesAllowed.class);
				Set<String> rolesSet = new HashSet<>(Arrays.asList(rolesAnnotation.value()));

				/*//Is user valid?
				if( ! isUserAllowed(username, password, rolesSet))
				{
					requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
					return;
				}*/
			}
		}
		catch(NotAuthorizedException e)
		{
			/*
			If user attempts to authorize with invalid token, he has to be informed
			that the token is invalid, even if no authorization is required.
			 */
			requestContext.abortWith(Response
					.status(Response.Status.UNAUTHORIZED.getStatusCode(),
							"Authorization token is invalid!")
					.build());
			return;
		}
		catch(SQLException e)
		{
			e.printStackTrace();
			if(!method.isAnnotationPresent(PermitAll.class))
			{
				requestContext.abortWith(Response
						.status(Response.Status.INTERNAL_SERVER_ERROR).build());
			}
			return;
		}
	}

	private EntityInfo verifyToken(String token) throws SQLException, NotAuthorizedException
	{
		selectToken.setString(1, token);

		ResultSet result = selectToken.executeQuery();
		if(!result.next())
		{
			throw new NotAuthorizedException("cannot find token");
		}

		EntityInfo info = new EntityInfo();
		info.uuid = SQLUtil.UUIDHelper.fromBytes(result.getBytes("uuid"));
		info.role = result.getString("role");

		return info;
	}

	private boolean isUserAllowed(final String username, final String password, final Set<String> rolesSet)
	{
		boolean isAllowed = false;

		//Step 1. Fetch password from database and match with password in argument
		//If both match then get the defined role for user from database and continue; else return isAllowed [false]
		//Access the database and do this part yourself
		//String userRole = userMgr.getUserRole(username);

		if(username.equals("howtodoinjava") && password.equals("password"))
		{
			String userRole = "ADMIN";

			//Step 2. Verify user role
			if(rolesSet.contains(userRole))
			{
				isAllowed = true;
			}
		}
		return isAllowed;
	}
}