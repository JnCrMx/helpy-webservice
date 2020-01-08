package de.jcm.helpy.webservice.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.jcm.helpy.distribution.VersionInfo;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@WebListener
public class HelpyContextListener implements ServletContextListener
{

	public static Connection connection;
	public static Configuration config;

	public static ObjectMapper jsonMapper;

	public static Map<String, VersionInfo> contentDistributions;
	public static Map<String, Map<String, VersionInfo>> clientDistributions;

	private void createConnection()
	{
		try
		{
			Class.forName("com.mysql.cj.jdbc.Driver");
			String connectionURL = "jdbc:mysql://address="
					+ "(host=" + config.getString("db.host", "localhost") + ")"
					+ "(port=" + config.getInt("db.port", 3306) + ")"
					+ "(autoReconnect=true)"
					+ "/" + config.getString("db.database");
			connection = DriverManager.getConnection(connectionURL, config.getString("db.user"), config.getString("db.password"));
		}
		catch(SQLException | ClassNotFoundException e)
		{
			e.printStackTrace();
		}
	}

	private void findDistributions()
	{
		contentDistributions = new HashMap<>();
		File contentDir = new File(config.getString("distribution.content"));
		Stream.of(Objects.requireNonNull(contentDir.listFiles(f -> f.getName().endsWith(".json"))))
				.forEach(this::loadContentDistribution);

		clientDistributions = new HashMap<>();
		File clientDir = new File(config.getString("distribution.client"));
		File[] clientPlatforms = clientDir.listFiles(File::isDirectory);
		assert clientPlatforms != null;
		for(File clientPlatform : clientPlatforms)
		{
			Stream.of(Objects.requireNonNull(
					clientPlatform.listFiles(f -> f.getName().endsWith(".json"))))
					.forEach(f->loadClientDistribution(clientPlatform.getName(), f));
		}
	}

	private void loadContentDistribution(File descriptor)
	{
		System.out.printf("Found content distribution described by file [%s]\n",
				descriptor.getAbsolutePath());
		loadDistribution(descriptor, contentDistributions);
	}

	private void loadClientDistribution(String platform, File descriptor)
	{
		System.out.printf("Found client distribution for platform [%s] described by file [%s]\n",
				platform, descriptor.getAbsolutePath());
		if(!clientDistributions.containsKey(platform))
			clientDistributions.put(platform, new HashMap<>());
		loadDistribution(descriptor, clientDistributions.get(platform));
	}

	private void loadDistribution(File descriptor, Map<String, VersionInfo> versionMap)
	{
		try
		{
			VersionInfo info = jsonMapper.readValue(descriptor, VersionInfo.class);
			info.directory = descriptor.getParentFile();
			versionMap.put(info.branch, info);
		}
		catch(IOException e)
		{
			System.out.printf("Failed to load distribution from [%s]:", descriptor.getAbsolutePath());
			e.printStackTrace();
		}
	}

	private void loadConfig()
	{
		Configurations configs = new Configurations();
		try
		{
			config = configs.properties(new File("/etc/helpy-webservice.properties"));
		}
		catch (ConfigurationException e)
		{
			e.printStackTrace();
		}
	}

	public void contextInitialized(ServletContextEvent event)
	{
		jsonMapper = new ObjectMapper();

		loadConfig();
		createConnection();
		findDistributions();

		event.getServletContext().setAttribute("config", config);
		event.getServletContext().setAttribute("connection", connection);
		event.getServletContext().setAttribute("content_distributions", contentDistributions);
		event.getServletContext().setAttribute("client_distributions", clientDistributions);
	}

	public void contextDestroyed(ServletContextEvent event)
	{
		try
		{
			if(connection != null)
			{
				connection.close();
			}
		}
		catch(SQLException se)
		{
			// Do something
		}
	}

}
