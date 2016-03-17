package edu.rutgers.dimacs.reu;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ejb.Lock;
import javax.ejb.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import edu.rutgers.dimacs.reu.utility.*;
import edu.rutgers.dimacs.reu.utility.DataStore.EdgeTypeGroup;
import static javax.ejb.LockType.READ;

@Singleton
@Path("centroidPathService")
@Lock(READ)
public class CentroidPathService {
	private static final Logger LOGGER = Logger.getLogger(CentroidPathService.class.getName());
	private final ArrayList<Map<Integer, ArrayList<Integer>>> peelToLmToPath;
	private final Map<Integer, Double> lmToShannon;
	private final Map<Integer, Double> nodeToWeight;
	private final ArrayList<Integer> neighborCounts;

	@SuppressWarnings("unchecked")
	public CentroidPathService() {
		ArrayList<Map<Integer, ArrayList<Integer>>> levels = null;
		Map<Integer, Double> lts = null, ntw = null;
		ArrayList<Integer> nc = null;
		try {
			levels = (ArrayList<Map<Integer, ArrayList<Integer>>>) loadSerialized("pvTo_LmToPath.ser");
			lts = (Map<Integer, Double>) loadSerialized("lmToShannon.ser");
			ntw = (Map<Integer, Double>) loadSerialized("nodeToWeight.ser");
			nc = (ArrayList<Integer>) loadSerialized("neighborCounts.ser");
			Double maxShan = 0.0;
			for(Integer i : lts.keySet()) {
				maxShan = Math.max(maxShan, lts.get(i));
			}
			for(Integer i : lts.keySet()) {
				lts.put(i, lts.get(i) / maxShan);
			}
			LOGGER.info("maximum Shannon is " + maxShan);
		} catch (ClassNotFoundException | IOException e) {
			LOGGER.log(Level.SEVERE, "Exception during serialized loading", e);
		} finally {
			peelToLmToPath = levels;
			lmToShannon = lts;
			nodeToWeight = ntw;
			neighborCounts = nc;
		}
		LOGGER.info("Centroid Path Service Instanciated");
	}

	/**
	 * load serialized file as object
	 * 
	 * @param source
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private Object loadSerialized(String source) throws IOException, ClassNotFoundException {
		InputStream is = this.getClass().getClassLoader().getResourceAsStream(source);
		if (null == is)
			return null;
		ObjectInputStream ois = new ObjectInputStream(is);
		Object o = ois.readObject();
		ois.close();
		is.close();
		return o;
	}

	@Path("getLandmarks")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getLandmarks(@QueryParam("peel") final int peel) {
		long timeStart = System.nanoTime();
		int level = peel;
		if (level < 0 || level >= peelToLmToPath.size()) {
			level = 0;
		}
		Set<Integer> all = new HashSet<>(peelToLmToPath.get(level).keySet());
		for (Integer lm : peelToLmToPath.get(level).keySet()) {
			all.addAll(peelToLmToPath.get(level).get(lm));
		}
		Graph graph = StreamingUtility.buildGraph(all);
		return Response.ok(StreamingUtility.finalJSON(graph, null, peelToLmToPath.get(level).keySet(), nodeToWeight,
				lmToShannon, neighborCounts, timeStart)).build();
	}

	@Path("getEgonet")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getEgonet(@QueryParam("vertex") final int vertex) {
		long timeStart = System.nanoTime();
		Set<Integer> vertices = new HashSet<>();
		Set<Integer> path_ints = new HashSet<>();
		vertices.add(vertex);
		path_ints.add(vertex);
		try {
			Iterable<Integer> it = DataStore.getInstance().getAdjacentUndirected(vertex, EdgeTypeGroup.NORMALONLY);
			for (Integer n : it) {
				vertices.add(n);
			}
		} catch (ExecutionException e) {
			LOGGER.log(Level.SEVERE, "Exception thrown during getNeighbors", e);
			return Response.serverError().build();
		}
		Graph graph = StreamingUtility.buildGraph(vertices);
		return Response.ok(StreamingUtility.finalJSON(graph, path_ints, null, nodeToWeight, lmToShannon, neighborCounts, timeStart))
				.build();
	}

	@Path("getNeighborhoodSize")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public String getNeighborhoodSize(@QueryParam("vertex") final int vertex) {
		int count = 0;
		try {
			Iterable<Integer> it = DataStore.getInstance().getAdjacentUndirected(vertex, EdgeTypeGroup.NORMALONLY);
			for (Iterator<Integer> i = it.iterator(); i.hasNext();) {
				i.next();
				count++;
			}
		} catch (ExecutionException e) {
			LOGGER.log(Level.SEVERE, "Exception thrown during getNeighbors", e);
			return Integer.toString(-1);
		}
		return Integer.toString(count);
	}

	@Path("shortestPath")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response shortestPath(@QueryParam("one") final int one, @QueryParam("two") final int two) {
		long timeStart = System.nanoTime();
		LOGGER.info("one: " + one + " two: " + two);
		Collection<Integer> path = getShortestPath(one, two);
		Set<Integer> path_ints = new HashSet<Integer>(path);
		Graph graph = StreamingUtility.buildGraph(path_ints);
		return Response.ok(StreamingUtility.finalJSON(graph, path_ints, null, nodeToWeight, lmToShannon, neighborCounts, timeStart))
				.build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAddition(@QueryParam("vertex") final int vertex, @QueryParam("peel") final int peelLevel) {
		long timeStart = System.nanoTime();
		LOGGER.info("vertex: " + vertex + " peel: " + peelLevel);
		Collection<Integer> path = getLandmarkPath(vertex, peelLevel);
		Set<Integer> path_ints = new HashSet<Integer>(path);
		Graph graph = StreamingUtility.buildGraph(path_ints);
		return Response.ok(StreamingUtility.finalJSON(graph, path_ints, peelToLmToPath.get(peelLevel).keySet(),
				nodeToWeight, lmToShannon, neighborCounts, timeStart)).build();
	}

	@Path("peelLevels")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public String getPeelLevels() {
		return Integer.toString(peelToLmToPath.size());
	}

	// landmark-driven pseudo shortest path
	private Collection<Integer> getLandmarkPath(int vertex, int level) {
		Queue<Integer> queue = new LinkedList<Integer>();
		HashSet<Integer> visited = new HashSet<Integer>();
		HashMap<Integer, Integer> gnToParent = new HashMap<Integer, Integer>();

		queue.add(vertex);
		visited.add(vertex);
		gnToParent.put(vertex, vertex);
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
					if (peelToLmToPath.get(level).keySet().contains(x)) {
						closestlm = x;
						landmarkFound = true;
						break;
					}
				}
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
			if (landmarkFound)
				break;
		}

		ArrayList<Integer> pathToLM = new ArrayList<Integer>();
		pathToLM.add(vertex);

		if (!landmarkFound)
			return pathToLM;

		// retrieve the path to the found landmark
		int curr = closestlm;
		while (curr != vertex) {
			pathToLM.add(curr);
			curr = gnToParent.get(curr);
		}

		ArrayList<Integer> output_path = new ArrayList<>(pathToLM);
		output_path.addAll(peelToLmToPath.get(level).get(closestlm));
		return output_path;
	}

	// bfs-driven true shortest path
	private Collection<Integer> getShortestPath(int one, int two) {
		Queue<Integer> queue = new LinkedList<Integer>();
		HashSet<Integer> visited = new HashSet<Integer>();
		HashMap<Integer, Integer> gnToParent = new HashMap<Integer, Integer>();

		queue.add(one);
		visited.add(one);
		gnToParent.put(one, one);
		boolean found = false;
		while (!queue.isEmpty() && !found) {
			Integer u = queue.remove();
			try {
				for (int x : DataStore.getInstance().getAdjacentUndirected(u, EdgeTypeGroup.NORMALONLY)) {
					if (!visited.contains(x)) {
						queue.add(x);
						visited.add(x);
						gnToParent.put(x, u);
					}
					if (two == x) {
						found = true;
						break;
					}
				}
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
		}

		LinkedList<Integer> path = new LinkedList<Integer>();
		path.add(one);

		if (!found) {
			path.add(two);
			return path;
		}

		// retrieve the path to the found landmark
		int curr = two;
		while (curr != one) {
			path.add(curr);
			curr = gnToParent.get(curr);
		}
		return path;
	}

	/*
	 * private void labelVertices(Graph graph) { LinkedList<Integer> wordNodes =
	 * new LinkedList<>(); for (GraphNode gn : graph.getNodeSet()) {
	 * wordNodes.add(Integer.parseInt(gn.toString())); } Map<Integer,
	 * Map<String, Integer>> allWords = null; try { allWords =
	 * ds.getWordMultiSet(wordNodes); } catch (SQLException | NamingException e)
	 * { LOGGER.warning("Words and descriptions not available from DataStore");
	 * } for (GraphNode gn : graph.getNodeSet()) { int gn_int =
	 * Integer.parseInt(gn.toString()); StringBuilder label = new
	 * StringBuilder(); if(allWords != null) { ArrayList<String> selectedWords =
	 * new ArrayList<String>(); Map<String, Integer> nodeWords =
	 * allWords.get(gn_int);
	 * 
	 * int totalFreq = 0; for (String word : nodeWords.keySet()) { totalFreq +=
	 * nodeWords.get(word); } for (String word : nodeWords.keySet()) { int
	 * wordFreq = nodeWords.get(word); if ((double) wordFreq / totalFreq > 0.25)
	 * { selectedWords.add(word); } // after sorting, check all n elements for
	 * this property -_- } for (String selected : selectedWords) {
	 * label.append("-"); label.append(selected); } gn.label = label.toString();
	 * } } }
	 */

}
