package de.jcm.helpy.webservice;

import de.jcm.helpy.Box;
import de.jcm.helpy.EntityInfo;
import de.jcm.helpy.GeoLocation;
import de.jcm.helpy.webservice.util.SQLUtil;

import javax.annotation.security.PermitAll;
import javax.servlet.ServletContext;
import javax.ws.rs.*;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Path("/box")
public class BoxEndpoint
{
	private @Context ServletContext context;

	private PreparedStatement boxInfoStatement;
	private PreparedStatement boxEquipmentStatement;
	private PreparedStatement nearBoxStatement;

	@Context
	private void prepare() throws SQLException
	{
		Connection connection = (Connection) context.getAttribute("connection");

		boxInfoStatement = connection.prepareStatement(
				"SELECT latitude, longitude, operator FROM box WHERE uuid = ?");
		boxEquipmentStatement = connection.prepareStatement(
				"SELECT name FROM box_equipment WHERE box = ?");
		nearBoxStatement = connection.prepareStatement(
				"SELECT uuid, geo_distance(latitude, longitude, ?, ?) AS distance "
						+ "FROM box "
						+ "HAVING distance <= ? "
						+ "ORDER BY distance ASC "
						+ "LIMIT 250");
	}

	@GET
	@Path("/self")
	@Produces(MediaType.APPLICATION_JSON)
	public Response self(@Context ContainerRequestContext request) throws SQLException
	{
		EntityInfo entity = (EntityInfo)request.getProperty("entity");
		UUID uuid = entity.uuid;

		boxInfoStatement.setBytes(1, SQLUtil.UUIDHelper.toBytes(uuid));
		ResultSet result = boxInfoStatement.executeQuery();

		if(result.next())
		{
			Box box = new Box();
			box.uuid = uuid;
			box.role = entity.role;

			box.location = new GeoLocation();
			box.location.latitude = result.getDouble("latitude");
			box.location.longitude = result.getDouble("longitude");

			box.operator = SQLUtil.UUIDHelper.fromBytes(result.getBytes("operator"));

			boxEquipmentStatement.setBytes(1, SQLUtil.UUIDHelper.toBytes(uuid));
			ResultSet result2 = boxEquipmentStatement.executeQuery();
			ArrayList<Box.BoxEquipment> equipment = new ArrayList<>();
			while(result2.next())
			{
				Box.BoxEquipment equipmentPiece = new Box.BoxEquipment();
				equipmentPiece.name = result2.getString("name");
				equipment.add(equipmentPiece);
			}

			box.equipment = equipment.toArray(new Box.BoxEquipment[0]);

			return Response.ok(box).build();
		}
		else
		{
			return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
					.entity("self (uuid = " + uuid.toString() + ") is not a box!").build();
		}
	}

	@GET
	@Path("/{uuid}")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response byId(@PathParam("uuid") UUID uuid, @Context ContainerRequestContext request) throws SQLException
	{
		EntityInfo entity = (EntityInfo)request.getProperty("entity");
		if(entity!=null && entity.uuid.equals(uuid))
		{
			return self(request);
		}

		boxInfoStatement.setBytes(1, SQLUtil.UUIDHelper.toBytes(uuid));
		ResultSet result = boxInfoStatement.executeQuery();

		if(result.next())
		{
			Box box = new Box();
			box.uuid = uuid;

			box.location = new GeoLocation();
			box.location.latitude = result.getDouble("latitude");
			box.location.longitude = result.getDouble("longitude");

			return Response.ok(box).build();
		}
		else
		{
			return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
					.entity("entity (uuid = " + uuid.toString() + ") is not a box!").build();
		}
	}

	@POST
	@Path("/range")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@PermitAll
	public Response inRange(@FormParam("latitude") double latitude,
			@FormParam("longitude") double longitude, @FormParam("range") double range) throws SQLException
	{
		if(range > 10000)
		{
			return Response.status(Response.Status.FORBIDDEN.getStatusCode(),
					"range too high").build();
		}

		nearBoxStatement.setDouble(1, latitude);
		nearBoxStatement.setDouble(2, longitude);
		nearBoxStatement.setDouble(3, range);
		ResultSet result = nearBoxStatement.executeQuery();

		Map<UUID, Double> boxes = new HashMap<>();
		while(result.next())
		{
			UUID uuid = SQLUtil.UUIDHelper.fromBytes(result.getBytes("uuid"));
			double distance = result.getDouble("distance");

			boxes.put(uuid, distance);
		}

		return Response.ok(boxes).build();
	}
}
