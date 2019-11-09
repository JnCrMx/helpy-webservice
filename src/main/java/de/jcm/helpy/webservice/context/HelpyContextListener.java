package de.jcm.helpy.webservice.context;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@WebListener
public class HelpyContextListener implements ServletContextListener
{

	public static Connection connection;
	public static Configuration config;

	private void createConnection()
	{
		try
		{
			Class.forName("com.mysql.cj.jdbc.Driver");
			String connectionURL = "jdbc:mysql://"
					+ config.getString("db.host", "localhost")
					+ ":"
					+ config.getInt("db.port", 3306)
					+ "/"
					+ config.getString("db.database");
			connection = DriverManager.getConnection(connectionURL,
					config.getString("db.user"), config.getString("db.password"));
		}
		catch(SQLException | ClassNotFoundException e)
		{
			e.printStackTrace();
		}
	}

	private void loadConfig()
	{
		Configurations configs = new Configurations();
		try
		{
			config = configs.properties(new File("/etc/helpy-server.properties"));
		}
		catch (ConfigurationException e)
		{
			e.printStackTrace();
		}
	}

	public void contextInitialized(ServletContextEvent event)
	{
		System.out.println("ServletContextListener started");
		loadConfig();
		createConnection();

		event.getServletContext().setAttribute("config", config);
		event.getServletContext().setAttribute("connection", connection);

		System.out.println(new File(".").getAbsolutePath());
	}

	public void contextDestroyed(ServletContextEvent event)
	{
		System.out.println("ServletContextListener destroyed");
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
