package edu.rutgers.dimacs.reu.utility;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

public class SQLiteHandler {
	
	private static final Logger LOGGER = Logger.getLogger(SQLiteHandler.class
			.getName());
	private Connection conn = null;
	private DataSource ds = null;
	
	private static SQLiteHandler instance;
	private SQLiteHandler() {
		try {
			Context initCtx = new InitialContext();
			Context envCtx = (Context) initCtx.lookup("java:comp/env");
			ds = (DataSource) envCtx.lookup("jdbc/OEIS-REU-2015");
			conn = ds.getConnection();
		} catch (NamingException | SQLException e) {
			LOGGER.log(Level.SEVERE, "Database unavailable", e);
		}
		instance = this;
		LOGGER.info("SQLite connected");
	}
	
	public static synchronized SQLiteHandler getInstance() {
		if(null == instance) {
			instance = new SQLiteHandler();
		}
		return instance;
	}

	public String getInfo() {
		StringBuilder result = new StringBuilder();
		if(conn != null) {
			try {
				DatabaseMetaData dm = (DatabaseMetaData) conn.getMetaData();
				result.append("Driver name: ").append(dm.getDriverName());
				result.append("<br>Driver version: ").append(dm.getDriverVersion());
				result.append("<br>Product name: ").append(dm.getDatabaseProductName());
				result.append("<br>Product version: ").append(dm.getDatabaseProductVersion());
				result.append("<br><br>Tables:<br>");
				Statement st = conn.createStatement();
				ResultSet rs = st.executeQuery("SELECT name, sql FROM sqlite_master WHERE type='table';");
				while (rs.next()) {
					result.append(rs.getString(1) + "<br>");
					result.append(rs.getString(2) + "<br>");
				}
			} catch (SQLException e) {
				e.printStackTrace();
				result.append("Unsuccessful");
			}
		} else {
			result.append("Unavailable");
		}
		return result.toString();
	}
	
	public ResultSet getCrossrefsIncident(int sequence) throws SQLException {
		return conn.createStatement().executeQuery("SELECT * FROM Cross_Refs WHERE source="
				+ sequence
				+ " OR destination="
				+ sequence + ";");
	}
	
	public Map<Integer, String> getDescription(Iterable<Integer> group) throws SQLException {
		Map<Integer, String> descs = new TreeMap<>();
		String in_str = "(";
		for (Integer i : group) {
			if (in_str.length() > 1)
				in_str += ", " + i.toString();
			else
				in_str += i.toString();
		}
		in_str += ")";
		ResultSet rs = conn.createStatement().executeQuery("SELECT id, description FROM Sequences WHERE id IN "
						+ in_str + ";");
		while (rs.next()) {
			descs.put(rs.getInt(1), rs.getString(2));
		}
		return descs;
	}
}
