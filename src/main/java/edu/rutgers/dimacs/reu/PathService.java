package edu.rutgers.dimacs.reu;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Collections;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import javax.ejb.Lock;
import javax.ejb.Singleton;
import javax.naming.NamingException;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.json.JSONObject;

import com.google.common.collect.Iterables;

import edu.rutgers.dimacs.reu.utility.*;
import edu.rutgers.dimacs.reu.utility.DataStore.EdgeTypeGroup;
import static javax.ejb.LockType.READ;

@Path("pathAddition")
@Singleton
@Lock(READ)
public class PathService {
	// LoadingCache<Integer, Collection<Integer>> cache;
	private static final Logger LOGGER = Logger.getLogger(PathService.class
			.getName());

	public PathService() throws SQLException, NamingException {
		LOGGER.info("Path Service Instanciated");
	}

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response getAddition(String input) {
		JSONObject obj = new JSONObject(input);
		String newNode = obj.getString("newNode");
		String existing = obj.getString("existing");
		boolean includeNeighborhoods = obj
				.getBoolean("includeNeighborhoods");
		long timeStart = System.nanoTime();
		LOGGER.info("query: " + newNode + "/" + existing);
		try {
			ArrayList<Integer> path = getShortestPath(newNode, existing);
			Graph graph = buildGraph(neighborhoods(path, includeNeighborhoods));
			return Response.ok(finalJSON(graph, path, timeStart)).build();
		} catch (ExecutionException e) {
			LOGGER.severe("cache failure on query :" + newNode + "/" + existing);
			return Response.serverError().build();
		}
	}

	public Graph buildGraph(HashSet<Integer> ints) throws ExecutionException {
		Graph graph = new Graph(false, "asdf");

		// add all the nodes
		for (int n : ints) {
			graph.addNode(Integer.toString(n));
		}

		Map<Integer, Collection<Integer>> refs = DataStore.getInstance()
				.getCrossrefsWithin(ints);

		// for each node, get the neighborhood
		// and if the neighbor_gn exists, add the edge
		for (GraphNode gn : graph.getNodeSet()) {
			int gn_int = Integer.parseInt(gn.toString());
			for (int neighbor_int : refs.get(gn_int)) {
				String neighbor_str = Integer.toString(neighbor_int);
				GraphNode neighbor_gn = graph.getNode(neighbor_str);
				if (neighbor_gn != null) {
					graph.addEdge(gn, neighbor_gn);
				}
			}
		}

		return graph;
	}

	public static void writeEdgesJSON(Graph graph, Writer writer)
			throws IOException {
		boolean first = true;
		TreeSet<Edge> orderedEdges = new TreeSet<>(graph.getEdgeSet());
		for (Edge edge : orderedEdges) {
			if (first) {
				first = false;
				writer.write("\n{" + edge.toString() + "}");
			} else {
				writer.write(",\n{" + edge.toString() + "}");
			}
		}
	}

	public StreamingOutput finalJSON(final Graph graph,
			final Collection<Integer> path_ints, final long timeStart) {
		StreamingOutput stream = new StreamingOutput() {
			@Override
			public void write(OutputStream os) throws IOException,
					WebApplicationException {
				Writer writer = new BufferedWriter(new OutputStreamWriter(os));
				writer.write("{\n\"nodes\":[\n");
				try {
					nodesJSON(graph, path_ints, writer);
				} catch (SQLException | NamingException e) {
					throw new IOException(e);
				}
				writer.write("], \"links\":[\n");
				writeEdgesJSON(graph, writer);
				writer.write("]\n}");
				writer.flush();
				writer.close();
				LOGGER.info("algorithm and response with JSON took "
						+ Integer.toString((int) ((System.nanoTime() - timeStart) / 1000000))
						+ "ms");
			}
		};
		return stream;
	}

	public ArrayList<Integer> getShortestPath(String source, String dests)
			throws ExecutionException {
		ArrayList<Integer> path = new ArrayList<Integer>();

		Integer source_int = Integer.parseInt(source);
		path.add(source_int);

		if (dests.equals("NONE")) {
			return path;
		}

		// store dests in TreeSet<Integer>
		String[] destinations = dests.split("-");
		TreeSet<Integer> dest_ints = new TreeSet<Integer>();
		for (int i = 0; i < destinations.length; i++) {
			dest_ints.add(Integer.parseInt(destinations[i]));
		}

		if (dest_ints.isEmpty()) {
			return path;
		}

		// make sure no dests are source
		if (dest_ints.contains(source_int)) {
			return path;
		}

		Queue<Integer> queue = new LinkedList<Integer>();
		HashSet<Integer> visited = new HashSet<Integer>();
		HashSet<Integer> dests_visited = new HashSet<Integer>();

		HashMap<Integer, Integer> nodeToParent = new HashMap<Integer, Integer>();
		nodeToParent.put(source_int, null);

		queue.add(source_int);
		visited.add(source_int);

		while (!queue.isEmpty()) {
			if (dests_visited.size() > 0) {
				break;
			}

			Integer curr_int = queue.remove();
			// System.out.println("curr_int: " + curr_int);

			for (int n : DataStore.getInstance().getAdjacentUndirected(
					curr_int, EdgeTypeGroup.NORMALONLY)) {
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

		if (dests_visited.size() == 0) {
			return path;
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
			path.addAll(path_ints);
			break;
		}

		return path;
	}

	public HashSet<Integer> neighborhoods(ArrayList<Integer> path,
			boolean addNeighbors) throws ExecutionException {
		HashSet<Integer> all_ints = new HashSet<Integer>();

		for (int n : path) {
			all_ints.add(n);
			if (addNeighbors)
				Iterables.addAll(all_ints, DataStore.getInstance()
						.getAdjacentUndirected(n, EdgeTypeGroup.NORMALONLY));
		}

		return all_ints;
	}

	public void nodesJSON(Graph graph, Collection<Integer> path_ints,
			Writer writer) throws SQLException, IOException, NamingException {
		if (path_ints.size() == 0) {
			return;
		}

		TreeSet<GraphNode> nodes = new TreeSet<>(graph.getNodeSet());
		LinkedList<Integer> wordNodes = new LinkedList<>();
		for (GraphNode gn : nodes) {
			wordNodes.add(Integer.parseInt(gn.id));
		}
		Map<Integer, Map<String, Integer>> allWords = DataStore.getInstance()
				.getWordMultiSet(wordNodes);
		Map<Integer, String> descriptions = DataStore.getInstance()
				.getDescription(path_ints);

		boolean first = true;
		for (GraphNode gn : nodes) {
			int gn_int = Integer.parseInt(gn.toString());
			ArrayList<String> selectedWords = new ArrayList<String>();
			Map<String, Integer> nodeWords = allWords.get(gn_int);
			// nodeWords = sortByComparator(nodeWords);

			int totalFreq = 0;
			for (String word : nodeWords.keySet()) {
				totalFreq += nodeWords.get(word);
			}
			for (String word : nodeWords.keySet()) {
				int wordFreq = nodeWords.get(word);
				if ((double) wordFreq / totalFreq > 0.25) {
					selectedWords.add(word);
				} // after sorting, check all n elements for this property -_-
			}

			String label = "";
			for (String selected : selectedWords) {
				label = label + "-" + selected;
			}
			gn.id += label;
			if (first) {
				first = false;
			} else {
				writer.write(",");
			}
			if (path_ints.contains(gn_int)) {
				writer.write("\n{\"name\":\"");
				writer.write(gn.toString());
				writer.write("\",\"description\":\"");
				writer.write(descriptions.get(gn_int));
				writer.write("\",\"path\":true}");
			} else {
				writer.write("\n{\"name\":\"");
				writer.write(gn.toString());
				writer.write("\"}");
			}
		}
	}

}
