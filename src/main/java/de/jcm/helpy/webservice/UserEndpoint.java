package de.jcm.helpy.webservice;

import de.jcm.helpy.Address;
import de.jcm.helpy.EntityInfo;
import de.jcm.helpy.User;
import de.jcm.helpy.webservice.util.SQLUtil;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Path("/user")
public class UserEndpoint
{
	private @Context ServletContext context;

	private PreparedStatement userInfoStatement;

	@Context
	private void prepare() throws SQLException
	{
		Connection connection = (Connection) context.getAttribute("connection");

		userInfoStatement = connection.prepareStatement(
				"SELECT username, firstname, lastname, insurance_details, "
						+ "country, city, zip_code, street, house_number, "
						+ "email, telephone FROM user WHERE uuid = ?");
	}

	/**
	 * @api {get} /user/self Get own user information.
	 * @apiName GetSelf
	 * @apiGroup User
	 *
	 * @apiHeader {String} Authorization Authorization token.
	 * @apiHeaderExample {Header} Header-Example
	 * 	Authorization: Bearer 568C78470AD219FB1A7AB5FA8EBB8D10
	 */
	@GET
	@Path("/self")
	@Produces( { MediaType.APPLICATION_JSON} )
	public Response self(@Context ContainerRequestContext request) throws SQLException
	{
		EntityInfo entity = (EntityInfo)request.getProperty("entity");
		UUID uuid = entity.uuid;

		userInfoStatement.setBytes(1, SQLUtil.UUIDHelper.toBytes(uuid));
		ResultSet result = userInfoStatement.executeQuery();

		if(result.next())
		{
			User user = new User();
			user.uuid = uuid;
			user.role = entity.role;

			user.username = result.getString("username");

			user.firstname = result.getString("firstname");
			user.lastname = result.getString("lastname");

			user.insuranceDetails = result.getInt("insurance_details");

			user.address = new Address();
			user.address.country = result.getString("country").toCharArray();
			user.address.city = result.getString("city");
			user.address.zipCode = result.getInt("zip_code");
			user.address.street = result.getString("street");
			user.address.houseNumber = result.getString("house_number");

			user.emailAddress = result.getString("email");
			user.telephoneNumber = result.getString("telephone");

			return Response.ok(user).build();
		}
		else
		{
			return Response
					.status(Response.Status.NOT_FOUND)
					.type(MediaType.TEXT_PLAIN)
					.entity("self (uuid = "+uuid.toString()+") is not a user!")
					.build();
		}
	}
}
