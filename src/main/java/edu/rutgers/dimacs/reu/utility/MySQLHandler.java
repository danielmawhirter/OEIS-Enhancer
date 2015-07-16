package edu.rutgers.dimacs.reu.utility;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

public final class MySQLHandler {
	private static Connection con = null;
	private static Statement st = null;

	public enum CrossrefTypes {
		ALL(""), CONTEXTANDNORMAL("Adjacent=0"), NORMALONLY(
				"Adjacent=0 AND In_Context=0");
		private final String stmt;

		private CrossrefTypes(String s) {
			this.stmt = s;
		}

		public String getStmt() {
			return stmt.length() > 0 ? " AND " + stmt : stmt;
		}

		public String getWholeStmt() {
			return stmt.length() > 0 ? " WHERE " + stmt : stmt;
		}
	}

	public enum WordSources {
		ALL(true, true, true), COMMENTS(true, false, false), REFERENCES(false,
				true, false), LINKS(false, false, true), NOTCOMMENTS(false,
				true, true), NOTREFERENCES(true, false, true), NOTLINKS(true,
				true, false);
		public final boolean comments, references, links;

		private WordSources(boolean comments, boolean references, boolean links) {
			this.comments = comments;
			this.references = references;
			this.links = links;
		}
	}

	public static boolean setup() throws NamingException, SQLException {
		Context initCtx = new InitialContext();
		Context envCtx = (Context) initCtx.lookup("java:comp/env");
		DataSource ds = (DataSource) envCtx.lookup("jdbc/OEIS-REU-2015");
		con = ds.getConnection();
		st = con.createStatement();
		return true;
	}

	public static boolean close() throws SQLException {
		st.close();
		st = null;
		con.close();
		con = null;
		return true;
	}

	public static Map<String, Integer> getWordMultiSet(int sequenceId,
			WordSources sources) throws SQLException {
		Map<String, Integer> wordMultiSet = new HashMap<>();
		String query = "";
		if (sources.comments) {
			query += "SELECT Word,Frequency FROM Com_Words WHERE SID="
					+ Integer.toString(sequenceId);
		}
		if (sources.links) {
			if (sources.comments) {
				query += " UNION ALL SELECT Word,Frequency FROM Link_Words WHERE SID="
						+ Integer.toString(sequenceId);
			} else {
				query += "SELECT Word,Frequency FROM Link_Words WHERE SID="
						+ Integer.toString(sequenceId);
			}
		}
		if (sources.references) {
			if (sources.comments || sources.links) {
				query += " UNION ALL SELECT Word,Frequency FROM Ref_Words WHERE SID="
						+ Integer.toString(sequenceId);
			} else {
				query += "SELECT Word,Frequency FROM Ref_Words WHERE SID="
						+ Integer.toString(sequenceId);
			}
		}
		query += ";";
		ResultSet rs = st.executeQuery(query);
		while (rs.next()) {
			String word = rs.getString(1);
			if (word.equals("NONE"))
				continue;
			Integer count = wordMultiSet.get(word);
			if (count == null) {
				wordMultiSet.put(word, rs.getInt(2));
			} else {
				wordMultiSet.put(word, count + rs.getInt(2));
			}
		}
		return wordMultiSet;
	}

	public static Map<String, Integer> getWordMultiSet(int sequenceId)
			throws SQLException {
		return getWordMultiSet(sequenceId, WordSources.ALL);
	}

	public static Map<Integer, Map<String, Integer>> getWordMultiSet(
			Iterable<Integer> group, WordSources sources) throws SQLException {
		Map<Integer, Map<String, Integer>> result = new TreeMap<>();
		String in_str = "(";
		for (Integer i : group) {
			result.put(i, new HashMap<String, Integer>());
			if (in_str.length() > 1)
				in_str += ", " + i.toString();
			else
				in_str += i.toString();
		}
		in_str += ")";

		String query = "";
		if (sources.comments) {
			query += "SELECT SID,Word,Frequency FROM Com_Words WHERE SID IN "
					+ in_str;
		}
		if (sources.links) {
			if (sources.comments) {
				query += " UNION ALL SELECT SID,Word,Frequency FROM Link_Words WHERE SID IN "
						+ in_str;
			} else {
				query += "SELECT SID,Word,Frequency FROM Link_Words WHERE SID="
						+ in_str;
			}
		}
		if (sources.references) {
			if (sources.comments || sources.links) {
				query += " UNION ALL SELECT SID,Word,Frequency FROM Ref_Words WHERE SID IN "
						+ in_str;
			} else {
				query += "SELECT SID,Word,Frequency FROM Ref_Words WHERE SID="
						+ in_str;
			}
		}
		query += ";";
		ResultSet rs = st.executeQuery(query);
		while (rs.next()) {
			int SID = rs.getInt(1);
			String word = rs.getString(2);
			int Frequency = rs.getInt(3);
			Map<String, Integer> current = result.get(SID);
			Integer count = current.get(word);
			if (count == null) {
				current.put(word, Frequency);
			} else {
				current.put(word, count + Frequency);
			}
		}

		return result;
	}

	public static Map<Integer, Map<String, Integer>> getWordMultiSet(
			Iterable<Integer> sequenceIds) throws SQLException {
		return getWordMultiSet(sequenceIds, WordSources.ALL);
	}

	public static Set<Integer> getCrossrefsLeaving(int sequenceId,
			CrossrefTypes types) throws SQLException {
		Set<Integer> out = new TreeSet<>();
		ResultSet rs;

		rs = st.executeQuery("SELECT otherSeq_Ref FROM Cross_Refs WHERE ourSeq_Ref="
				+ Integer.toString(sequenceId) + types.getStmt() + ";");
		while (rs.next()) {
			out.add(rs.getInt(1));
		}

		return out;
	}

	public static Set<Integer> getCrossrefsLeaving(int sequenceId)
			throws SQLException {
		return getCrossrefsLeaving(sequenceId, CrossrefTypes.ALL);
	}

	public static Map<Integer, Collection<Integer>> getCrossrefsLeaving(
			Iterable<Integer> group, CrossrefTypes types) throws SQLException {
		Map<Integer, Collection<Integer>> out = new TreeMap<Integer, Collection<Integer>>();
		String in_str = "(";
		for (Integer i : group) {
			if (in_str.length() > 1)
				in_str += ", " + i.toString();
			else
				in_str += i.toString();
		}
		in_str += ")";
		ResultSet rs;

		rs = st.executeQuery("SELECT * FROM Cross_Refs WHERE ourSeq_Ref IN"
				+ in_str + types.getStmt() + ";");
		while (rs.next()) {
			Collection<Integer> dests = out.get(rs.getInt(1));
			if (dests != null) {
				dests.add(rs.getInt(2));
			} else {
				dests = new LinkedList<>();
				dests.add(rs.getInt(2));
				out.put(rs.getInt(1), dests);
			}

		}

		return out;
	}

	public static Map<Integer, Collection<Integer>> getCrossrefsLeaving(
			Iterable<Integer> group) throws SQLException {
		return getCrossrefsLeaving(group, CrossrefTypes.ALL);
	}

	public static Set<Integer> getCrossrefsInto(int sequenceId,
			CrossrefTypes types) throws SQLException {
		Set<Integer> out = new TreeSet<>();
		ResultSet rs;

		rs = st.executeQuery("SELECT ourSeq_Ref FROM Cross_Refs WHERE otherSeq_Ref="
				+ Integer.toString(sequenceId) + types.getStmt() + ";");
		while (rs.next()) {
			out.add(rs.getInt(1));
		}

		return out;
	}

	public static Set<Integer> getCrossrefsInto(int sequenceId)
			throws SQLException {
		return getCrossrefsInto(sequenceId, CrossrefTypes.ALL);
	}

	public static Map<Integer, LinkedList<Integer>> getCrossrefsInto(
			Iterable<Integer> group, CrossrefTypes types) throws SQLException {
		Map<Integer, LinkedList<Integer>> out = new TreeMap<Integer, LinkedList<Integer>>();
		String in_str = "(";
		for (Integer i : group) {
			if (in_str.length() > 1)
				in_str += ", " + i.toString();
			else
				in_str += i.toString();
		}
		in_str += ")";
		ResultSet rs;

		rs = st.executeQuery("SELECT * FROM Cross_Refs WHERE otherSeq_Ref IN"
				+ in_str + types.getStmt() + ";");
		while (rs.next()) {
			LinkedList<Integer> srcs = out.get(rs.getInt(2));
			if (srcs != null) {
				srcs.add(rs.getInt(1));
			} else {
				srcs = new LinkedList<>();
				srcs.add(rs.getInt(1));
				out.put(rs.getInt(2), srcs);
			}

		}

		return out;
	}

	public static Map<Integer, LinkedList<Integer>> getCrossrefsInto(
			Iterable<Integer> group) throws SQLException {
		return getCrossrefsInto(group, CrossrefTypes.ALL);
	}

	public static Map<Integer, Collection<Integer>> getAllCrossrefs(
			CrossrefTypes types) throws SQLException {
		Map<Integer, Collection<Integer>> result = new TreeMap<>();
		ResultSet rs = st
				.executeQuery("SELECT ourSeq_Ref,otherSeq_Ref FROM Cross_Refs"
						+ types.getWholeStmt());
		while (rs.next()) {
			Integer src = rs.getInt(1);
			Integer dest = rs.getInt(2);
			Collection<Integer> srcAdj = result.get(src);
			if (null == srcAdj) {
				srcAdj = new TreeSet<>();
				result.put(src, srcAdj);
			}
			Collection<Integer> destAdj = result.get(dest);
			if (null == destAdj) {
				destAdj = new TreeSet<>();
				result.put(dest, destAdj);
			}
			srcAdj.add(dest);
			destAdj.add(src);
		}
		return result;
	}

	public static Map<Integer, Collection<Integer>> getAllCrossrefs()
			throws SQLException {
		return getAllCrossrefs(CrossrefTypes.NORMALONLY);
	}

	public static String getDescription(int id) throws SQLException {

		ResultSet rs = st.executeQuery("SELECT Name FROM Sequences WHERE SID="
				+ Integer.toString(id) + ";");
		if (rs.next())
			return rs.getString(1);

		return null;
	}

	public static Map<Integer, String> getDescription(Iterable<Integer> group) throws SQLException {
		Map<Integer, String> descs = new TreeMap<>();
		String in_str = "(";
		for (Integer i : group) {
			if (in_str.length() > 1)
				in_str += ", " + i.toString();
			else
				in_str += i.toString();
		}
		in_str += ")";
		ResultSet rs = st.executeQuery("SELECT SID,Name FROM Sequences WHERE SID IN "
				+ in_str + ";");
		while(rs.next()) {
			descs.put(rs.getInt(1), rs.getString(2)); 
		}
		return descs;

	}

	public static int getAuthorYear(int id) throws SQLException {
		ResultSet rs = st
				.executeQuery("SELECT Year_Created FROM Authors WHERE SID="
						+ Integer.toString(id) + ";");
		if (rs.next())
			return rs.getInt(1);
		return -1;
	}

	public static Set<Integer> getSequencesWithKeyword(String keyword)
			throws SQLException {
		Set<Integer> ids = new TreeSet<>();
		ResultSet rs = st
				.executeQuery("SELECT SID FROM Key_Words WHERE K_Word LIKE '"
						+ keyword + "';");
		while (rs.next()) {
			ids.add(rs.getInt(1));
		}
		return ids;
	}

	public static Set<Integer> getSequencesWithPeel(int value)
			throws SQLException {
		Set<Integer> ids = new TreeSet<>();
		ResultSet rs = st
				.executeQuery("SELECT SID FROM Sequences WHERE Peel_Value="
						+ Integer.toString(value) + ";");
		while (rs.next()) {
			ids.add(rs.getInt(1));
		}
		return ids;
	}

	public static Set<String> getKeywordsOf(int id) throws SQLException {
		Set<String> words = new HashSet<>();
		ResultSet rs = st
				.executeQuery("SELECT K_Word FROM Key_Words WHERE SID="
						+ Integer.toString(id) + ";");
		while (rs.next()) {
			words.add(rs.getString(1));
		}
		return words;
	}

	public static Set<String> getContributors(int id) throws SQLException {
		Set<String> out = new HashSet<>();

		ResultSet rs = st
				.executeQuery("SELECT Name FROM Contributors WHERE SID="
						+ Integer.toString(id) + ";");
		while (rs.next()) {
			out.add(rs.getString(1));
		}

		return out;
	}

}
