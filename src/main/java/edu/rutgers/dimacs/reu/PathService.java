package edu.rutgers.dimacs.reu;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Set;
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
	
	public static HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> lmtolmPaths;
	public static HashMap<Integer, HashMap<Integer, Double>> edgeToWeight;

	public PathService() throws SQLException, NamingException, IOException, ClassNotFoundException {
		loadAPSP("lmapsp.ser");
		loadWeights("edge_weights.ser");
		LOGGER.info("Path Service Instanciated");
	}

	/**
	 * load serialized apsp into local hashmap
	 * @param source
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	@SuppressWarnings("unchecked")
	public void loadAPSP(String source) throws IOException, ClassNotFoundException {
		InputStream is = PathService.class.getClassLoader()
				.getResourceAsStream(source);
		ObjectInputStream ois = new ObjectInputStream(is);
		lmtolmPaths = (HashMap<Integer, HashMap<Integer, ArrayList<Integer>>>) ois
				.readObject();
		ois.close();
		is.close();
	}
	
	/**
	 * get the edge weights into a local hashmap
	 * @param source
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	@SuppressWarnings("unchecked")
	public void loadWeights(String source) throws IOException, ClassNotFoundException {
		InputStream is = PathService.class.getClassLoader()
				.getResourceAsStream(source);
		ObjectInputStream ois = new ObjectInputStream(is);
		edgeToWeight = (HashMap<Integer, HashMap<Integer, Double>>) ois
				.readObject();
		ois.close();
		is.close();
	}

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response getAddition(String input) {
		JSONObject obj = new JSONObject(input);
		String newNode = obj.getString("newNode");
		String existing = obj.getString("existing");
		boolean includeNeighborhoods = obj.getBoolean("includeNeighborhoods");
		long timeStart = System.nanoTime();
		LOGGER.info("query: " + newNode + "/" + existing);
		try {
			Collection<Integer> path = getShortestPath(newNode, existing);
			Set<Integer> path_ints = neighborhoods(path, includeNeighborhoods);
			Graph graph = buildGraph(path_ints);
			return Response.ok(finalJSON(graph, path_ints, timeStart)).build();
		} catch (ExecutionException e) {
			LOGGER.severe("cache failure on query :" + newNode + "/" + existing);
			return Response.serverError().build();
		}
	}
	
	public Collection<Integer> output_path(Integer input,
			TreeSet<Integer> existing_nodes) {
		/*
		if (lmtolmPaths.keySet().contains(178087)) {
			LOGGER.info("in lmtolmPaths: " + 178087);
			LOGGER.info("its lmPaths: " + lmtolmPaths.get(178087).toString());
		}*/

		/*
		ArrayList<Integer> test_path = new ArrayList<Integer>();
		test_path.add(input);
		Integer a = 230317;
		Integer b = 178087;
		test_path.add(a); test_path.add(b);
		if (true) {
			return test_path;
		}*/
		
		
		//BFS from input until the closest landmark in the graph is found
		Queue<Integer> queue = new LinkedList<Integer>(); 
		HashSet<Integer> visited = new HashSet<Integer>();
		HashMap<Integer, Integer> gnToParent = new HashMap<Integer, Integer>();
		  
		queue.add(input);
		visited.add(input);
		gnToParent.put(input, input);
		int closestlm = 0;
		boolean landmarkFound = false;
		while (!queue.isEmpty()) {
			Integer u = queue.remove();
			try {
				for (int x : DataStore.getInstance().getAdjacentUndirected(u, EdgeTypeGroup.NORMALONLY)) {
					if (!visited.contains(x)) {
						queue.add(x);
						visited.add(x);
						gnToParent.put(x, u);
					}
					if (lmtolmPaths.keySet().contains(x)) {
						closestlm = x;
						landmarkFound = true;
						break;
					}
				}
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
			if (landmarkFound) break; 
		}
		

		// Dijkstra from input until the closest landmark in the graph is found
		// Implemented without a priority queue
		// gnToDist gives dists and acts as a queue - null means
		// unvisited
		/*
		HashSet<Integer> visited = new HashSet<Integer>();
		HashMap<Integer, Integer> gnToParent = new HashMap<Integer, Integer>();
		HashMap<Integer, Double> gnToDist = new HashMap<Integer, Double>();

		visited.add(input);
		gnToParent.put(input, input);
		gnToDist.put(input, 0.0);

		int closestlm = 0;
		boolean landmarkFound = false;

		while (!gnToDist.keySet().isEmpty()) {
			Integer u = extractMin(gnToDist, visited);
			if (gnToParent.keySet().size() == 1) {
				u = input;
			}
			
			//LOGGER.info("min: " + u);

			visited.add(u);
			if (visited.size() % 1000 == 0) {
				LOGGER.info("num visited: " + visited.size());
			}

			if (lmtolmPaths.keySet().contains(u)) {
				closestlm = u;
				landmarkFound = true;
				break;
			}

			try {
				for (int x : DataStore.getInstance().getAdjacentUndirected(u,
						EdgeTypeGroup.NORMALONLY)) {
					double weight = 1.0;
					if (edgeToWeight.get(u).get(x) != null) {
						weight = edgeToWeight.get(u).get(x);
					}
					if (edgeToWeight.get(x).get(u) != null) {
						weight = edgeToWeight.get(x).get(u);
					}

					double dist_through_u = gnToDist.get(u) + weight; // NullPointerException - fixed
																		// ////////////////////////////
					if (gnToDist.get(x) == null
							|| dist_through_u < gnToDist.get(x)) {
						gnToDist.put(x, dist_through_u);
						gnToParent.put(x, u);
					}
				}
				//gnToDist.remove(u);
			} catch (ExecutionException e) {
				e.printStackTrace();
				return null;
			}

		}*/

		ArrayList<Integer> pathToLM = new ArrayList<Integer>();
		pathToLM.add(input);
		
		if (!landmarkFound)
			return pathToLM;

		// retrieve the path to the found landmark
		int curr = closestlm;
		while (curr != input) {
			pathToLM.add(curr);
			curr = gnToParent.get(curr);
		}
		
		// if nothing is existing, we can just return pathToLM
		if (existing_nodes.isEmpty()) {
			return pathToLM;
		}

		// if closestlm is already existing, we can just return pathToLM
		if (existing_nodes.contains(closestlm)) {
			return pathToLM;
		}

		// otherwise, we have to union pathToLM with the shortest path from
		// closestlm to an existing lm
		Collection<Integer> lm_to_lm = shortestLMtoLM(closestlm, lmtolmPaths, existing_nodes);
		ArrayList<Integer> output_path = new ArrayList<>(pathToLM.size() + lm_to_lm.size());
		output_path.addAll(pathToLM);
		output_path.addAll(lm_to_lm);

		LOGGER.info("output_path: " + output_path.toString());

		return output_path;
	}

	// takes in a landmark and existing nodes, returns the shortest path to an
	// existing landmark
	public static ArrayList<Integer> shortestLMtoLM(int lm,
			HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> lmtolmPaths,
			TreeSet<Integer> existing_nodes) {

		HashMap<Integer, ArrayList<Integer>> lmToPath = lmtolmPaths.get(lm);
		int closestlm = -1;
		int min_dist = Integer.MAX_VALUE;
		for (int landmark : lmToPath.keySet()) {
			if (existing_nodes.contains(landmark)) {
				int dist = lmToPath.get(landmark).size();
				if (dist <= min_dist) {
					min_dist = dist;
					closestlm = landmark;
				}
			}
		}

		ArrayList<Integer> output = lmToPath.get(closestlm);
		return output;
	}

	public static Integer extractMin(HashMap<Integer, Double> gnToDist,
			HashSet<Integer> visited) {
		Integer min = 0;
		double min_dist = Double.MAX_VALUE;
		for (Integer u : gnToDist.keySet()) {
			if (visited.contains(u)) {
				continue;
			}
			if (gnToDist.get(u) <= min_dist) {
				min = u;
				min_dist = gnToDist.get(u);
			}
		}

		return min;
	}

	public Collection<Integer> getShortestPath(String source, String dests)
			throws ExecutionException {
		Integer source_int = Integer.parseInt(source);
		TreeSet<Integer> dest_ints = new TreeSet<Integer>();
		if (dests.equals("NONE")) {
			return output_path(source_int, dest_ints);
		}
		String[] destinations = dests.split("-");
		for (int i = 0; i < destinations.length; i++) {
			dest_ints.add(Integer.parseInt(destinations[i]));
		}
		return output_path(source_int, dest_ints);
	}

	/*
	 * public ArrayList<Integer> getShortestPath(String source, String dests)
	 * throws ExecutionException { ArrayList<Integer> path = new
	 * ArrayList<Integer>();
	 * 
	 * Integer source_int = Integer.parseInt(source); path.add(source_int);
	 * 
	 * if (dests.equals("NONE")) { return path; }
	 * 
	 * // store dests in TreeSet<Integer> String[] destinations =
	 * dests.split("-"); TreeSet<Integer> dest_ints = new TreeSet<Integer>();
	 * for (int i = 0; i < destinations.length; i++) {
	 * dest_ints.add(Integer.parseInt(destinations[i])); }
	 * 
	 * if (dest_ints.isEmpty()) { return path; }
	 * 
	 * // make sure no dests are source if (dest_ints.contains(source_int)) {
	 * return path; }
	 * 
	 * Queue<Integer> queue = new LinkedList<Integer>(); HashSet<Integer>
	 * visited = new HashSet<Integer>(); HashSet<Integer> dests_visited = new
	 * HashSet<Integer>();
	 * 
	 * HashMap<Integer, Integer> nodeToParent = new HashMap<Integer, Integer>();
	 * nodeToParent.put(source_int, null);
	 * 
	 * queue.add(source_int); visited.add(source_int);
	 * 
	 * while (!queue.isEmpty()) { if (dests_visited.size() > 0) { break; }
	 * 
	 * Integer curr_int = queue.remove(); // System.out.println("curr_int: " +
	 * curr_int);
	 * 
	 * for (int n : DataStore.getInstance().getAdjacentUndirected( curr_int,
	 * EdgeTypeGroup.NORMALONLY)) { if (visited.contains(n)) { continue; }
	 * visited.add(n); // System.out.println("n: " + n); nodeToParent.put(n,
	 * curr_int);
	 * 
	 * queue.add(n);
	 * 
	 * if (dest_ints.contains(n)) { dests_visited.add(n); } } }
	 * 
	 * if (dests_visited.size() == 0) { return path; }
	 * 
	 * for (int dest_int : dests_visited) { ArrayList<Integer> path_ints = new
	 * ArrayList<Integer>(); int curr_int = dest_int; while
	 * (nodeToParent.get(curr_int) != null) { path_ints.add(curr_int); curr_int
	 * = nodeToParent.get(curr_int); } path_ints.add(curr_int);
	 * Collections.reverse(path_ints); path.addAll(path_ints); break; }
	 * 
	 * return path; }
	 */

	public Set<Integer> neighborhoods(Collection<Integer> path,
			boolean addNeighbors) throws ExecutionException {
		HashSet<Integer> all_ints = new HashSet<Integer>(path);

		for (int n : path) {
			all_ints.add(n);
			if (addNeighbors) {
				for(Integer k : DataStore.getInstance()
						.getAdjacentUndirected(n, EdgeTypeGroup.NORMALONLY)) {
					all_ints.add(k);
				}
			}
		}

		return all_ints;
	}

	public static void writeNodesJSON(Graph graph, Set<Integer> path_ints,
			Writer writer) throws SQLException, IOException, NamingException {
		if (path_ints.size() == 0) {
			return;
		}

		LinkedList<Integer> wordNodes = new LinkedList<>();
		for (GraphNode gn : graph.getNodeSet()) {
			wordNodes.add(Integer.parseInt(gn.id));
		}
		Map<Integer, Map<String, Integer>> allWords = DataStore.getInstance().getWordMultiSet(wordNodes);
		Map<Integer, String> descriptions = DataStore.getInstance().getDescription(path_ints);

		boolean first = true;
		for (GraphNode gn : graph.getNodeSet()) {
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

			StringBuilder label =  new StringBuilder();
			for (String selected : selectedWords) {
				label.append("-");
				label.append(selected);
			}
			gn.id += label.toString();
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
				writer.write("\",\"path\":true");
				if(lmtolmPaths.containsKey(gn_int)) { //mark landmarks
					writer.write(",\"landmark\":true");
				}
				writer.write("}");
			} else {
				writer.write("\n{\"name\":\"");
				writer.write(gn.toString());
				writer.write("\"}");
			}
		}
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

	public static StreamingOutput finalJSON(final Graph graph,
			final Set<Integer> path_ints, final long timeStart) {
		StreamingOutput stream = new StreamingOutput() {
			@Override
			public void write(OutputStream os) throws IOException,
					WebApplicationException {
				Writer writer = new BufferedWriter(new OutputStreamWriter(os));
				writer.write("{\n\"nodes\":[\n");
				try {
					writeNodesJSON(graph, path_ints, writer);
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
	
	public static Graph buildGraph(Set<Integer> ints) throws ExecutionException {
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

}
