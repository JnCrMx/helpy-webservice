package de.jcm.helpy.webservice.distribution;

import de.jcm.helpy.distribution.VersionInfo;

import javax.annotation.security.PermitAll;
import javax.servlet.ServletContext;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.util.Map;

@Path("/distribution")
public class DistributionEndpoint
{
	private @Context ServletContext context;

	@GET
	@Path("/client/platforms")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public String[] getClientPlatforms()
	{
		return new String[]{"android", "raspberrypi"};
	}

	@GET
	@Path("/client/{platform}/branches")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public String[] getClientBranches(@PathParam("platform") String platform)
	{
		return new String[]{"nightly"};
	}

	@GET
	@Path("/client/{platform}/{branch}")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public String getClientBranchInformation(@PathParam("platform") String platform,
			@PathParam("branch") String branch)
	{
		return branch;
	}

	@GET
	@Path("/client/{platform}/{branch}/download")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@PermitAll
	public Response getClientDownload(@PathParam("platform") String platform,
			@PathParam("branch") String branch)
	{
		StreamingOutput fileStream = output -> output.write(branch.getBytes());

		return Response
				.ok(fileStream)
				.header("content-disposition",
						"attachment; filename = "+branch+".zip")
				.build();
	}

	@GET
	@Path("/content/branches")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public String[] getContentBranches() throws IOException
	{
		Map<String, VersionInfo> contentDists =
				(Map<String, VersionInfo>) context.getAttribute("content_distributions");

		return contentDists.keySet().toArray(String[]::new);
	}

	@GET
	@Path("/content/{branch}")
	@Produces(MediaType.APPLICATION_JSON)
	@PermitAll
	public Response getContentBranchInformation(@PathParam("branch") String branch) throws IOException
	{
		Map<String, VersionInfo> contentDists =
				(Map<String, VersionInfo>) context.getAttribute("content_distributions");

		if(!contentDists.containsKey(branch))
			return Response.status(Response.Status.NOT_FOUND).build();

		return Response.ok(contentDists.get(branch)).build();
	}

	@GET
	@Path("/content/{branch}/download")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@PermitAll
	public Response getContentDownload(@PathParam("branch") String branch)
			throws IOException
	{
		Map<String, VersionInfo> contentDists =
				(Map<String, VersionInfo>) context.getAttribute("content_distributions");

		if(!contentDists.containsKey(branch))
			return Response.status(Response.Status.NOT_FOUND).build();

		VersionInfo dist = contentDists.get(branch);
		File distFile = new File(dist.directory, dist.filename);
		if(!distFile.exists())
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
					"file not found").build();

		return Response
				.ok((StreamingOutput)new FileInputStream(distFile)::transferTo)
				.header("content-disposition",
						"attachment; filename = "+dist.filename)
				.build();
	}
}
