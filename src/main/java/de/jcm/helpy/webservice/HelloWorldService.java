package de.jcm.helpy.webservice;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/hello")
public class HelloWorldService
{
	@GET
	@Path("/{param}")
	@Produces( {MediaType.APPLICATION_JSON} )
	public Response getMessage(@PathParam("param") String message)
	{
		Test t = new Test();
		t.a = message;
		t.t = new Test();
		t.t.a = message;

		return Response.status(200).entity(t).build();
	}

	@POST
	@Consumes( {MediaType.APPLICATION_JSON} )
	@Produces( {MediaType.APPLICATION_JSON} )
	@Path("/echo")
	public Test echo(Test test)
	{
		return test;
	}

	public static class Test
	{
		public String a;
		public Test t;

		public Test()
		{

		}
	}
}