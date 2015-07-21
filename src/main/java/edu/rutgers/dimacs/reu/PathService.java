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
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.ejb.Lock;
import javax.ejb.Singleton;
import javax.naming.NamingException;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.PathParam;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import edu.rutgers.dimacs.reu.utility.*;
import edu.rutgers.dimacs.reu.utility.MySQLHandler.CrossrefTypes;
import static javax.ejb.LockType.READ;

@Path("pathAddition")
@Singleton
@Lock(READ)
public class PathService {
	LoadingCache<Integer, Collection<Integer>> cache;
	private static final Logger LOGGER = Logger.getLogger(PathService.class
			.getName());

	public PathService() throws SQLException, NamingException {
		MySQLHandler.setup();
		cache = CacheBuilder.newBuilder().maximumSize(300000)
				.expireAfterAccess(600, TimeUnit.MINUTES)
				.build(new CacheLoader<Integer, Collection<Integer>>() {
					@Override
					public Collection<Integer> load(Integer node) throws SQLException {
						Collection<Integer> neighbors_ints = MySQLHandler
								.getCrossrefsLeaving(node, CrossrefTypes.NORMALONLY);
						Collection<Integer> entering_ints = MySQLHandler
								.getCrossrefsInto(node, CrossrefTypes.NORMALONLY);
						neighbors_ints.addAll(entering_ints);
						return neighbors_ints;
					}
				});
		//cache.putAll(MySQLHandler.getAllCrossrefs()); //loads the entire graph, takes 2.5s
		System.out.println("Path Service Instanciated");
	}

	@GET
	@Path("{newNode}/{pathTo}/{includeNeighborhoods}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAddition(@PathParam("newNode") String newNode,
			@PathParam("pathTo") String existing, @PathParam("includeNeighborhoods") @DefaultValue("false") boolean includeNeighborhoods) {
		try {
			
			long timeStart;
			
			if(cache.size() < 10000) {
				timeStart = System.nanoTime();
				cache.putAll(MySQLHandler.getAllCrossrefs());
				LOGGER.info("Refilling cache took "
						+ Integer.toString((int) ((System.nanoTime() - timeStart) / 1000000))
						+ "ms");
			}
			
			//log
			LOGGER.info("query: " + newNode + "/" + existing);
			timeStart = System.nanoTime();
			
			//run
			ArrayList<ArrayList<Integer>> paths = getShortestPath(newNode,
					existing);
			
			//log
			LOGGER.info("getShortestPath took "
				+ Integer.toString((int) ((System.nanoTime() - timeStart) / 1000000))
				+ "ms");
			timeStart = System.nanoTime();
			
			//run
			Graph graph = buildGraph(neighborhoods(paths, includeNeighborhoods));

			//log
			LOGGER.info("building graph took "
				+ Integer.toString((int) ((System.nanoTime() - timeStart) / 1000000))
				+ "ms");
			timeStart = System.nanoTime();
			
			//run
			HashSet<Integer> paths_set = new HashSet<Integer>();
			for (ArrayList<Integer> path : paths) {
				paths_set.addAll(path);
			}
			StreamingOutput stream = finalJSON(graph, paths_set);
			return Response.ok(stream).build();
		
		} catch (ExecutionException e) {
			LOGGER.severe("cache failure on query " + "");
			return Response.serverError().build();
		} catch (SQLException e) {
			LOGGER.severe("labeling failure (sql)");
			return Response.serverError().build();
		}
	}

	public Graph buildGraph(HashSet<Integer> ints) throws ExecutionException {
		Graph graph = new Graph(false, "asdf");

		// add all the nodes
		for (int n : ints) {
			graph.addNode(Integer.toString(n));
		}

		// for each node, get the neighborhood
		// and if the neighbor_gn exists, add the edge
		for (GraphNode gn : graph.getNodeSet()) {
			int gn_int = Integer.parseInt(gn.toString());
			for (int neighbor_int : cache.get(gn_int)) {
				String neighbor_str = Integer.toString(neighbor_int);
				GraphNode neighbor_gn = graph.getNode(neighbor_str);
				if (neighbor_gn != null) {
					graph.addEdge(gn, neighbor_gn);
				}
			}
		}

		return graph;
	}

	public static void writeEdgesJSON(Graph graph, Writer writer) throws IOException {
		boolean first = true;
		TreeSet<Edge> orderedEdges = new TreeSet<>(graph.getEdgeSet());
		for (Edge edge : orderedEdges) {
			if(first) {
				first = false;
				writer.write("\n{" + edge.toString() + "}");
			} else {
				writer.write(",\n{" + edge.toString() + "}");
			}
		}
	}

	public StreamingOutput finalJSON(final Graph graph, final HashSet<Integer> path_ints) {
		final long timeStart = System.nanoTime();
		StreamingOutput stream = new StreamingOutput() {
			@Override
			public void write(OutputStream os) throws IOException,
					WebApplicationException {
				Writer writer = new BufferedWriter(new OutputStreamWriter(
						os));
				writer.write("{\n\"nodes\":[\n");
				try {
					nodesJSON(graph, path_ints, writer);
				} catch (SQLException e) {
					throw new IOException(e);
				}
				writer.write("], \"links\":[\n");
				writeEdgesJSON(graph, writer);
				writer.write("]\n}");
				writer.flush();
				writer.close();
				LOGGER.info("generating JSON took "
						+ Integer.toString((int) ((System.nanoTime() - timeStart) / 1000000))
						+ "ms");
			}
		};
		return stream;
	}

	public ArrayList<ArrayList<Integer>> getShortestPath(String source,
			String dests) throws ExecutionException {
		ArrayList<ArrayList<Integer>> paths = new ArrayList<ArrayList<Integer>>();

		Integer source_int = Integer.parseInt(source);

		if (dests.equals("NONE")) {
			ArrayList<Integer> the_str = new ArrayList<Integer>();
			the_str.add(source_int);
			paths.add(the_str);
			return paths;
		}

		// store dests in TreeSet<Integer>
		String[] destinations = dests.split("-");
		TreeSet<Integer> dest_ints = new TreeSet<Integer>();
		for (int i = 0; i < destinations.length; i++) {
			dest_ints.add(Integer.parseInt(destinations[i]));
		}

		// make sure no dests are source
		LinkedList<Integer> toRemove = new LinkedList<>();
		for (Integer n : dest_ints) {
			if (n.equals(source_int)) {
				toRemove.add(n);
			}
		}
		for(Integer n : toRemove) {
			dest_ints.remove(n);
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
			if (dests_visited.size() > 0) {
				break;
			}

			Integer curr_int = queue.remove();
			// System.out.println("curr_int: " + curr_int);

			for (int n : cache.get(curr_int)) {
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
			ArrayList<Integer> path_ints = new ArrayList<Integer>();
			path_ints.add(source_int);
			paths.add(path_ints);
			return paths;
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

	public HashSet<Integer> neighborhoods(
			ArrayList<ArrayList<Integer>> paths_ints, boolean addNeighbors) throws ExecutionException {
		HashSet<Integer> all_ints = new HashSet<Integer>();

		for (ArrayList<Integer> path : paths_ints) {
			for (int n : path) {
				all_ints.add(n);
				if(addNeighbors) all_ints.addAll(cache.get(n));
			}
		}

		return all_ints;
	}

	public void nodesJSON(Graph graph, HashSet<Integer> path_ints, Writer writer) throws SQLException, IOException {
		if (path_ints.size() == 0) {
			return;
		}

		TreeSet<GraphNode> nodes = new TreeSet<>(graph.getNodeSet());
		LinkedList<Integer> wordNodes = new LinkedList<>();
		for(GraphNode gn : nodes) {
			wordNodes.add(Integer.parseInt(gn.id));
		}
		Map<Integer, Map<String, Integer>> allWords = MySQLHandler.getWordMultiSet(wordNodes);
		Map<Integer, String> descriptions = MySQLHandler.getDescription(path_ints);

		boolean first = true;
		for (GraphNode gn : nodes) {
			int gn_int = Integer.parseInt(gn.toString());
			ArrayList<String> selectedWords = new ArrayList<String>();
			Map<String, Integer> nodeWords = allWords.get(gn_int);
			//nodeWords = sortByComparator(nodeWords);

			int totalFreq = 0;
			for (String word : nodeWords.keySet()) {
				totalFreq += nodeWords.get(word);
			}
			for (String word : nodeWords.keySet()) {
				int wordFreq = nodeWords.get(word);
				if ((double) wordFreq / totalFreq > 0.25) {
					selectedWords.add(word);
				} //after sorting, check all n elements for this property -_-
			}
			
			String label = "";
			for (String selected : selectedWords) {
				label = label + "-" + selected;
			}
			gn.id += label;
			if(first) {
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
	
	/*private Map<String, Integer> sortByComparator(Map<String, Integer> unsortMap) {

		List<Entry<String, Integer>> list = new LinkedList<Entry<String, Integer>>(unsortMap.entrySet());

		// Sorting the list based on values
		Collections.sort(list, new Comparator<Entry<String, Integer>>() {
			public int compare(Entry<String, Integer> o1, Entry<String, Integer> o2) {
				return o2.getValue().compareTo(o1.getValue());
			}
		});

		// Maintaining insertion order with the help of LinkedList
		Map<String, Integer> sortedMap = new LinkedHashMap<String, Integer>();
		for (Entry<String, Integer> entry : list) {
			sortedMap.put(entry.getKey(), entry.getValue());
		}

		return sortedMap;
	}*/


}
