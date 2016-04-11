package edu.rutgers.dimacs.reu.utility;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
	//private PreparedStatement prepCrossrefsIncident;
	//private PreparedStatement prepDescription;
	
	private static SQLiteHandler instance;
	private SQLiteHandler() {
		try {
			Context initCtx = new InitialContext();
			Context envCtx = (Context) initCtx.lookup("java:comp/env");
			this.ds = (DataSource) envCtx.lookup("jdbc/OEIS-REU-2015");
			this.conn = ds.getConnection();
			//this.prepCrossrefsIncident = conn.prepareStatement("SELECT * FROM Cross_Refs WHERE source=? OR destination=?;");
			//this.prepDescription = conn.prepareStatement("SELECT id, description FROM Sequences WHERE id IN ?;");
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
				result.append("<br>Database URL: ").append(dm.getURL());
				result.append("<br><br>Tables:<br>");
				Set<String> tables = new HashSet<>();
				Statement st = conn.createStatement();
				ResultSet rs = st.executeQuery("SELECT name, sql FROM sqlite_master WHERE type='table';");
				while (rs.next()) {
					result.append(rs.getString(1) + "<br>");
					result.append(rs.getString(2) + "<br>");
					tables.add(rs.getString(1));
				}
				for(String table : tables) {
					rs = st.executeQuery("SELECT * FROM " + table + " LIMIT 0;");
					ResultSetMetaData md = rs.getMetaData();
					result.append("<br>").append(table).append("<br>| ");
					for(int i = 1; i <= md.getColumnCount(); i++) {
						result.append(md.getColumnName(i)).append(":").append(md.getColumnTypeName(i)).append(" | ");
					}
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
	
	public ResultSet getCrossrefsIncident(Set<Integer> group) throws SQLException {
		return conn.createStatement().executeQuery("SELECT * FROM Cross_Refs WHERE source IN "
				+ forSet(group)
				+ " OR destination IN "
				+ forSet(group) + ";");
	}
	
	public Map<Integer, String> getDescription(Set<Integer> group) throws SQLException {
		Map<Integer, String> descs = new TreeMap<>();
		ResultSet rs = conn.createStatement().executeQuery("SELECT id, description FROM Sequences WHERE id IN "
						+ forSet(group) + ";");
		while (rs.next()) {
			descs.put(rs.getInt(1), rs.getString(2));
		}
		return descs;
	}
	
	private String forSet(Set<Integer> group) {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		boolean first = true;
		for(Integer i : group) {
			if(first) {
				first = false;
			} else {
				sb.append(", ");
			}
			sb.append(i.toString());
		}
		sb.append(")");
		return sb.toString();
	}
}
