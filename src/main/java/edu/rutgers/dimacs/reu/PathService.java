package edu.rutgers.dimacs.reu;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Collections;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.ejb.Lock;
import javax.ejb.Singleton;
import javax.naming.NamingException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.PathParam;

import edu.rutgers.dimacs.reu.utility.*;
import static javax.ejb.LockType.READ;

@Path("pathAddition")
@Singleton
@Lock(READ)
public class PathService {
	

	public PathService() throws SQLException, NamingException {
		MySQLHandler.setup();
		System.out.println("Path Service Instanciated");
	}

	@GET
	@Path("{newNode}/{pathTo}")
	@Produces(MediaType.APPLICATION_JSON)
	public String getAddition(@PathParam("newNode") String newNode,
			@PathParam("pathTo") String existing) {
		try {
			ArrayList<ArrayList<Integer>> paths = getShortestPaths(newNode,
					existing);
			Graph graph = buildGraph(neighborhoods(paths));

			HashSet<Integer> paths_set = new HashSet<Integer>();
			for (ArrayList<Integer> path : paths) {
				paths_set.addAll(path);
			}

			return finalJSON(graph, paths_set);
		} catch (SQLException e) {
			e.printStackTrace();
			return "{\"error\": \"sql error\"}";
		}
	}

	public static Graph buildGraph(HashSet<Integer> ints) throws SQLException {
		Graph graph = new Graph(false, "asdf");

		// add all the nodes
		for (int n : ints) {
			graph.addNode(Integer.toString(n));
		}

		// for each node, get the neighborhood
		// and if the neighbor_gn exists, add the edge
		for (GraphNode gn : graph.getNodeSet()) {
			int gn_int = Integer.parseInt(gn.toString());
			Set<Integer> neighbor_ints = MySQLHandler
					.getCrossrefsLeaving(gn_int);
			Set<Integer> entering_ints = MySQLHandler.getCrossrefsInto(gn_int);
			neighbor_ints.addAll(entering_ints);

			for (int neighbor_int : neighbor_ints) {
				String neighbor_str = Integer.toString(neighbor_int);
				GraphNode neighbor_gn = graph.getNode(neighbor_str);
				if (neighbor_gn != null) {
					graph.addEdge(gn, neighbor_gn);
				}
			}
		}

		return graph;
	}

	public static String edgesJSON(Graph graph) {

		String result = "";
		for (Edge edge : graph.getEdgeSet()) {
			result = result + ",\n{" + edge.toString() + "}";
		}

		if (result.charAt(0) == ',') {
			result = result.substring(1);
		}

		return result;
	}

	public static String finalJSON(Graph graph, HashSet<Integer> path_ints) {
		String result = "{\n\"links\":[\n";
		result = result + edgesJSON(graph);
		result = result + "], \"nodes\":[\n";
		result = result + nodesJSON(graph, path_ints) + "]\n}";

		return result;
		// return
		// "{\"links\":[{\"id\":\"1--2\", \"source_name\":\"1\", \"target_name\":\"2\"}],\"nodes\":[{\"name\":\"1\"},{\"name\":\"2\"}]}";
	}

	public static ArrayList<ArrayList<Integer>> getShortestPaths(String source,
			String dests) throws SQLException {
		ArrayList<ArrayList<Integer>> paths = new ArrayList<ArrayList<Integer>>();

		Integer source_int = Integer.parseInt(source);

		if (dests.equals("NONE")) {
			ArrayList<Integer> the_str = new ArrayList<Integer>();
			the_str.add(source_int);
			paths.add(the_str);
			return paths;
		}

		// store dests in HashSet<Integer>
		String[] destinations = dests.split("-");
		HashSet<Integer> dest_ints = new HashSet<Integer>();
		for (int i = 0; i < destinations.length; i++) {
			dest_ints.add(Integer.parseInt(destinations[i]));
		}

		// make sure no dests are source
		for (Integer n : dest_ints) {
			if (n.equals(source_int)) {
				dest_ints.remove(n);
			}
		}

		if (dest_ints.isEmpty()) {
			ArrayList<Integer> the_str = new ArrayList<Integer>();
			the_str.add(source_int);
			paths.add(the_str);
			return paths;
		}

		Queue<Integer> queue = new LinkedList<Integer>();
		HashSet<Integer> visited = new HashSet<Integer>();
		HashSet<Integer> dests_visited = new HashSet<Integer>();

		HashMap<Integer, Integer> nodeToParent = new HashMap<Integer, Integer>();
		nodeToParent.put(source_int, null);

		queue.add(source_int);
		visited.add(source_int);

		while (!queue.isEmpty()) {
			if (dests_visited.containsAll(dest_ints)) {
				break;
			}

			Integer curr_int = queue.remove();
			// System.out.println("curr_int: " + curr_int);

			Set<Integer> neighbors_ints = MySQLHandler
					.getCrossrefsLeaving(curr_int);
			Set<Integer> entering_ints = MySQLHandler
					.getCrossrefsInto(curr_int);
			neighbors_ints.addAll(entering_ints);

			for (int n : neighbors_ints) {
				if (visited.contains(n)) {
					continue;
				}
				visited.add(n);
				// System.out.println("n: " + n);
				nodeToParent.put(n, curr_int);

				queue.add(n);

				if (dest_ints.contains(n)) {
					dests_visited.add(n);
				}
			}
		}

		for (int dest_int : dests_visited) {
			ArrayList<Integer> path_ints = new ArrayList<Integer>();
			int curr_int = dest_int;
			while (nodeToParent.get(curr_int) != null) {
				path_ints.add(curr_int);
				curr_int = nodeToParent.get(curr_int);
			}
			path_ints.add(curr_int);
			Collections.reverse(path_ints);
			paths.add(path_ints);
		}

		return paths;
	}

	public static HashSet<Integer> neighborhoods(
			ArrayList<ArrayList<Integer>> paths_ints) throws SQLException {
		HashSet<Integer> all_ints = new HashSet<Integer>();

		for (ArrayList<Integer> path : paths_ints) {
			for (int n : path) {
				all_ints.add(n);
				Set<Integer> neighbors_ints = MySQLHandler
						.getCrossrefsLeaving(n);
				Set<Integer> entering_ints = MySQLHandler.getCrossrefsInto(n);
				neighbors_ints.addAll(entering_ints);

				all_ints.addAll(neighbors_ints);
			}
		}

		return all_ints;
	}

	public static String nodesJSON(Graph graph, HashSet<Integer> path_ints) {

		String result = "";

		TreeSet<GraphNode> nodes = new TreeSet<>(graph.getNodeSet());

		for (GraphNode gn : nodes) {
			int gn_int = Integer.parseInt(gn.toString());
			if (path_ints.contains(gn_int)) {
				result = result + ",\n{\"name\":\"" + gn.toString()
						+ "\", \"path\":true}";
			} else {
				result = result + ",\n{\"name\":\"" + gn.toString() + "\"}";
			}
		}

		if (result.charAt(0) == ',') {
			result = result.substring(1);
		}

		return result;
	}

}
