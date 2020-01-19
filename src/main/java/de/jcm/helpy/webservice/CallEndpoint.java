package de.jcm.helpy.webservice;

import de.jcm.helpy.*;
import de.jcm.helpy.webservice.authentication.AuthenticationFilter;
import de.jcm.helpy.webservice.util.SQLUtil;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.servlet.ServletContext;
import javax.ws.rs.*;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

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
	private PreparedStatement getInteractionsStatement;
	private PreparedStatement interactionDataStatement;

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
						+ "(language, content_path, chosen_option, emergency_call, submitter) "
						+ "VALUES(?, ?, ?, ?, ?)");
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
		getInteractionsStatement = connection.prepareStatement(
				"SELECT id, language, content_path, chosen_option, emergency_call, "
						+ "submitter, time "
						+ "FROM emergency_call_interaction WHERE emergency_call = ? "
						+ "ORDER BY time DESC");
		interactionDataStatement = connection.prepareStatement(
				"SELECT additional_data FROM emergency_call_interaction "
						+ "WHERE id = ? AND emergency_call = ?");
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
	@Path("/{id}/interactions")
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	public Response addInteraction(@PathParam("id") int id,
			@FormParam("language") String language,
			@FormParam("content_path") String contentPath,
			@FormParam("chosen_option") int chosenOption,
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

		insertInteractionStatement.setString(1, language);
		insertInteractionStatement.setString(2, contentPath);
		insertInteractionStatement.setInt(3, chosenOption);
		insertInteractionStatement.setInt(4, id);
		insertInteractionStatement.setBytes(5, SQLUtil.UUIDHelper.toBytes(uuid));
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

	@GET
	@Path("/{id}/interactions")
	@Produces(MediaType.APPLICATION_JSON)
	@RolesAllowed("AMBULANCE")
	public Response getInteractions(@PathParam("id") int id,
	                                @Context ContainerRequestContext request) throws SQLException
	{
		if (!testCall(id)) {
			return Response.status(Response.Status.NOT_FOUND).type("text/plain")
					.entity("call with id " + id + " not found!").build();
		}

		this.getInteractionsStatement.setInt(1, id);
		ResultSet result = this.getInteractionsStatement.executeQuery();

		ArrayList<CallInteraction> interactions = new ArrayList<>();
		while(result.next())
		{
			CallInteraction interaction = new CallInteraction();
			interaction.id = result.getInt("id");
			interaction.language = result.getString("language");
			interaction.contentPath = result.getString("content_path");
			interaction.chosenOption = result.getInt("chosen_option");
			interaction.callId = result.getInt("emergency_call");
			interaction.submitter = SQLUtil.UUIDHelper.fromBytes(result.getBytes("submitter"));
			interaction.time = result.getTimestamp("time");

			interactions.add(interaction);
		}

		return Response.ok(interactions).build();
	}

	@GET
	@Path("/{id}/interactions/last")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getLastInteraction(@PathParam("id") int id,
	                                @Context ContainerRequestContext request) throws SQLException
	{
		EntityInfo entity = (EntityInfo)request.getProperty("entity");

		if (!testCall(id))
		{
			return Response.status(Response.Status.NOT_FOUND).type("text/plain")
					.entity("call with id " + id + " not found!").build();
		}
		if(!testEntityInCall(id, entity.uuid)
				&& !(entity.role.equals("AMBULANCE")
				|| Arrays.asList(AuthenticationFilter.SUPER_ROLES).contains(entity.role)))
		{
			return Response.status(Response.Status.NOT_ACCEPTABLE)
					.type(MediaType.TEXT_PLAIN_TYPE)
					.entity("user not in call!")
					.build();
		}

		this.getInteractionsStatement.setInt(1, id);
		ResultSet result = this.getInteractionsStatement.executeQuery();

		if(result.next())
		{
			CallInteraction interaction = new CallInteraction();
			interaction.id = result.getInt("id");
			interaction.language = result.getString("language");
			interaction.contentPath = result.getString("content_path");
			interaction.chosenOption = result.getInt("chosen_option");
			interaction.callId = result.getInt("emergency_call");
			interaction.time = result.getTimestamp("time");
			if(entity.role.equals("AMBULANCE")
					|| Arrays.asList(AuthenticationFilter.SUPER_ROLES).contains(entity.role))
			{
				interaction.submitter =
						SQLUtil.UUIDHelper.fromBytes(result.getBytes("submitter"));
			}

			return Response.ok(interaction).build();
		}
		else
		{
			return Response.noContent().build();
		}
	}

	@GET
	@Path("/{cid}/interactions/{iid}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@RolesAllowed({"AMBULANCE"})
	public Response getInteractionInfo(@PathParam("cid") int callId,
	                                   @PathParam("iid") int interactionId,
	                                @Context ContainerRequestContext request) throws SQLException
	{
		if(!testCall(callId))
		{
			return Response.status(Response.Status.NOT_FOUND).type("text/plain")
					.entity("call with id " + callId + " not found!").build();
		}

		this.interactionDataStatement.setInt(1, interactionId);
		this.interactionDataStatement.setInt(2, callId);
		ResultSet result = this.interactionDataStatement.executeQuery();

		if(result.next())
		{
			byte[] data = result.getBytes("additional_data");
			if(data!=null)
			{
				return Response
						.ok((StreamingOutput) new ByteArrayInputStream(data)::transferTo)
						.build();
			}
			else
			{
				return Response.noContent().build();
			}
		}
		else
		{
			return Response.status(Response.Status.NOT_FOUND).type("text/plain")
					.entity("interaction with id " + interactionId + " not found!").build();
		}
	}
}
