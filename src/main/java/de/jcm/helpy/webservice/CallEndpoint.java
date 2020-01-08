package de.jcm.helpy.webservice;

import de.jcm.helpy.*;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Path("/call")
public class CallEndpoint
{
	private @Context ServletContext context;

	private PreparedStatement createCallStatement;
	private PreparedStatement getIdStatement;
	private PreparedStatement joinCallStatement;
	private PreparedStatement testCallStatement;
	private PreparedStatement testEntityInCallStatement;
	private PreparedStatement insertInteractionStatement;
	private PreparedStatement nearCallsStatement;
	private PreparedStatement callInfoStatement;
	private PreparedStatement updateEntityInCallStatement;

	@Context
	private void prepare() throws SQLException
	{
		Connection connection = (Connection) context.getAttribute("connection");

		createCallStatement = connection.prepareStatement(
				"INSERT INTO emergency_call (latitude, longitude) VALUES(?, ?)");
		getIdStatement = connection.prepareStatement(
				"SELECT LAST_INSERT_ID()");
		joinCallStatement = connection.prepareStatement(
				"INSERT INTO entity_in_call (entity, emergency_call, position, state) "
						+ "VALUES(?, ?, ?, ?)");
		testCallStatement = connection.prepareStatement(
				"SELECT 1 FROM emergency_call WHERE id = ?");
		testEntityInCallStatement = connection.prepareStatement(
				"SELECT 1 FROM entity_in_call "
						+ "WHERE entity = ? AND emergency_call = ?");
		insertInteractionStatement = connection.prepareCall(
				"INSERT INTO emergency_call_interaction "
						+ "(question, answer, emergency_call, submitter) "
						+ "VALUES(?, ?, ?, ?)");
		nearCallsStatement = connection.prepareStatement(
				"SELECT id, geo_distance(latitude, longitude, ?, ?) AS distance, priority "
						+ "FROM emergency_call "
						+ "HAVING distance <= ? "
						+ "ORDER BY priority, distance ASC "
						+ "LIMIT 250");
		callInfoStatement = connection.prepareStatement(
				"SELECT priority, latitude, longitude, emergency_services_informed, time "
						+ "FROM emergency_call WHERE id = ?");
		updateEntityInCallStatement = connection.prepareStatement(
				"UPDATE entity_in_call SET state=? WHERE entity=? AND emergency_call=?");
	}

	@PUT
	@Produces(MediaType.TEXT_PLAIN)
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	public Response createCall(@FormParam("latitude") double latitude,
			@FormParam("longitude") double longitude,
			@Context ContainerRequestContext request) throws SQLException
	{
		EntityInfo entity = (EntityInfo)request.getProperty("entity");
		UUID uuid = entity.uuid;

		createCallStatement.setDouble(1, latitude);
		createCallStatement.setDouble(2, longitude);
		createCallStatement.execute();

		ResultSet result = getIdStatement.executeQuery();
		if(!result.next())
		{
			return Response.serverError().build();
		}
		int id = result.getInt(1);

		joinCallStatement.setBytes(1, SQLUtil.UUIDHelper.toBytes(uuid));
		joinCallStatement.setInt(2, id);
		joinCallStatement.setString(3, "CALLER");
		joinCallStatement.setString(4, "PRESENT");
		joinCallStatement.execute();

		return Response.ok(id).build();
	}

	public boolean testCall(int id) throws SQLException
	{
		testCallStatement.setInt(1, id);
		return testCallStatement.executeQuery().next();
	}

	public boolean testEntityInCall(int call, UUID entity) throws SQLException
	{
		testEntityInCallStatement.setBytes(1, SQLUtil.UUIDHelper.toBytes(entity));
		testEntityInCallStatement.setInt(2, call);
		return testEntityInCallStatement.executeQuery().next();
	}

	@PUT
	@Path("/{id}/answer")
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	public Response updateQuestion(@PathParam("id") int id,
			@FormParam("question") String question,
			@FormParam("answer") String answer,
			@Context ContainerRequestContext request) throws SQLException
	{
		EntityInfo entity = (EntityInfo)request.getProperty("entity");
		UUID uuid = entity.uuid;

		if(!testCall(id))
			return Response.status(Response.Status.NOT_FOUND.getStatusCode(),
					"call not found").build();

		if(!testEntityInCall(id, uuid))
			return Response.status(Response.Status.FORBIDDEN.getStatusCode(),
					"not joined call").build();

		insertInteractionStatement.setString(1, question);
		insertInteractionStatement.setString(2, answer);
		insertInteractionStatement.setInt(3, id);
		insertInteractionStatement.setBytes(4, SQLUtil.UUIDHelper.toBytes(uuid));
		insertInteractionStatement.execute();

		return Response.noContent().build();
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

		nearCallsStatement.setDouble(1, latitude);
		nearCallsStatement.setDouble(2, longitude);
		nearCallsStatement.setDouble(3, range);
		ResultSet result = nearCallsStatement.executeQuery();

		Map<Integer, DistancePriority> calls = new HashMap<>();
		while(result.next())
		{
			int call = result.getInt("id");
			double distance = result.getDouble("distance");
			int priority = result.getInt("priority");

			calls.put(call, new DistancePriority(distance, priority));
		}

		return Response.ok(calls).build();
	}

	public static class DistancePriority
	{
		public double distance;
		public int priority;

		public DistancePriority(double distance, int priority)
		{
			this.distance = distance;
			this.priority = priority;
		}
	}

	@GET
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response byId(@PathParam("id") int id, @Context ContainerRequestContext request) throws SQLException
	{
		EntityInfo entity = (EntityInfo)request.getProperty("entity");

		callInfoStatement.setInt(1, id);
		ResultSet result = callInfoStatement.executeQuery();

		if(result.next())
		{
			Call call = new Call();
			call.id = id;

			call.priority = result.getInt("priority");

			call.location = new GeoLocation();
			call.location.latitude = result.getDouble("latitude");
			call.location.longitude = result.getDouble("longitude");

			call.emergencyServicesInformed = result.getBoolean("emergency_services_informed");
			call.time = result.getTimestamp("time");

			return Response.ok(call).build();
		}
		else
		{
			return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
					.entity("call with id "+id+" not found!").build();
		}
	}

	@POST
	@Path("/{id}/join")
	@Consumes({"application/x-www-form-urlencoded"})
	public Response joinCall(@PathParam("id") int id, @FormParam("state") EntityInCallState state,
	                         @Context ContainerRequestContext request) throws SQLException
	{
		EntityInfo entity = (EntityInfo)request.getProperty("entity");

		if (!testCall(id)) {
			return Response.status(Response.Status.NOT_FOUND).type("text/plain")
					.entity("call with id " + id + " not found!").build();
		}
		if (testEntityInCall(id, entity.uuid)) {

			this.updateEntityInCallStatement.setString(1, state.name());
			this.updateEntityInCallStatement.setBytes(2, SQLUtil.UUIDHelper.toBytes(entity.uuid));
			this.updateEntityInCallStatement.setInt(3, id);
			this.updateEntityInCallStatement.execute();

			return Response.ok().build();
		}


		this.joinCallStatement.setBytes(1, SQLUtil.UUIDHelper.toBytes(entity.uuid));
		this.joinCallStatement.setInt(2, id);
		this.joinCallStatement.setString(3, "HELPER");
		this.joinCallStatement.setString(4, state.name());
		this.joinCallStatement.execute();

		return Response.ok().build();
	}
}
