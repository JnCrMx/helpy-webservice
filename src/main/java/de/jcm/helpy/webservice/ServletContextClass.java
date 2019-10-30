package de.jcm.helpy.webservice;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ServletContextClass implements ServletContextListener
{

	public static Connection connection;

	public void createConnection()
	{
		try
		{
			String connectionURL = "jdbc:mysql://root:port/path";
			Class.forName("com.mysql.jdbc.Driver");
			connection = DriverManager.getConnection(connectionURL, "root", "password");
		}
		catch(SQLException | ClassNotFoundException e)
		{
			// Do something
		}
	}

	public void contextInitialized(ServletContextEvent arg0)
	{
		System.out.println("ServletContextListener started");
		createConnection();
		arg0.getServletContext().setAttribute("connection", connection);
	}

	public void contextDestroyed(ServletContextEvent arg0)
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
