package edu.rutgers.dimacs.reu;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ejb.Lock;
import javax.ejb.Singleton;
import javax.naming.NamingException;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONArray;

import edu.rutgers.dimacs.reu.utility.*;
import static javax.ejb.LockType.READ;

// TODO thicken/color edge based on weight and main view/ego net view (requires weights, not done)
// TODO marked vertex import
// TODO mark toggle

@Singleton
@Path("centroidPathService")
@Lock(READ)
public class CentroidPathService {
	private static final Logger LOGGER = Logger.getLogger(CentroidPathService.class.getName());
	private final ArrayList<Map<Integer, ArrayList<Integer>>> peelToLmToPath;
	private final Map<Integer, Double> lmToShannon;
	private final Map<Integer, Double> nodeToWeight;
	//private final ArrayList<Integer> neighborCounts;

	@SuppressWarnings("unchecked")
	public CentroidPathService() {
		ArrayList<Map<Integer, ArrayList<Integer>>> levels = null;
		Map<Integer, Double> lts = null, ntw = null;
		ArrayList<Integer> nc = null;
		try {
			levels = (ArrayList<Map<Integer, ArrayList<Integer>>>) loadSerialized("pvTo_LmToPath.ser");
			//lts = (Map<Integer, Double>) loadSerialized("lmToShannon.ser");
			//ntw = (Map<Integer, Double>) loadSerialized("nodeToWeight.ser");
			//nc = (ArrayList<Integer>) loadSerialized("neighborCounts.ser");
		} catch (ClassNotFoundException | IOException e) {
			LOGGER.log(Level.SEVERE, "Exception during serialized loading", e);
		} finally {
			peelToLmToPath = levels;
			lmToShannon = lts;
			nodeToWeight = ntw;
			//neighborCounts = nc;
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
		return Response.ok(StreamingUtility.streamJSON(all, null, peelToLmToPath.get(level).keySet(), nodeToWeight,
				lmToShannon, timeStart)).build();
	}

	@Path("getSubgraph")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response iteratedShortestPath(String data) {
		long timeStart = System.nanoTime();
		JSONArray js = new JSONArray(data);
		Set<Integer> input_vertices = new HashSet<>();
		HashSet<Integer> vertices = new HashSet<>();
		for (int i = 0; i < js.length(); i++) {
			if(!input_vertices.contains(js.getInt(i))) {
				input_vertices.add(js.getInt(i));
				vertices.addAll(getShortestPath(js.getInt(i), vertices));
			}
		}
		return Response.ok(StreamingUtility.streamJSON(vertices, input_vertices, null, nodeToWeight, lmToShannon,
				timeStart)).build();
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
			Iterable<Integer> it = DataStore.getInstance().getAdjacentUndirected(vertex);
			for (Integer n : it) {
				vertices.add(n);
			}
		} catch (ExecutionException e) {
			LOGGER.log(Level.SEVERE, "Exception thrown during getNeighbors", e);
			return Response.serverError().build();
		}
		return Response.ok(StreamingUtility.streamJSON(vertices, path_ints, null, nodeToWeight, lmToShannon,
				timeStart)).build();
	}

	@Path("getEgonetWithoutCenter")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getEgonetWithoutCenter(@QueryParam("vertex") final int vertex) {
		long timeStart = System.nanoTime();
		Set<Integer> vertices = new HashSet<>();
		try {
			Iterable<Integer> it = DataStore.getInstance().getAdjacentUndirected(vertex);
			for (Integer n : it) {
				vertices.add(n);
			}
		} catch (ExecutionException e) {
			LOGGER.log(Level.SEVERE, "Exception thrown during getNeighbors", e);
			return Response.serverError().build();
		}

		try {
			TreeMap<Integer, TreeMap<Integer, Double>> induced = DataStore.getInstance().getSubgraphInduced(vertices);
			HashMap<Integer, HashSet<Integer>> undir = new HashMap<>();
			for (Integer s : induced.keySet()) {
				for (Integer d : induced.get(s).keySet()) {
					undir.putIfAbsent(s, new HashSet<Integer>());
					undir.get(s).add(d);
					undir.putIfAbsent(d, new HashSet<Integer>());
					undir.get(d).add(s);
				}
			}
			Map<Integer, ArrayList<Integer>> egonet_lmToPath = Clustering.generate_lmToPath(undir);
			Set<Integer> all = new HashSet<>(egonet_lmToPath.keySet());
			for (Integer lm : egonet_lmToPath.keySet()) {
				all.addAll(egonet_lmToPath.get(lm));
			}
			if(vertices.size() < 128) {
				all.addAll(vertices);
				return Response.ok(StreamingUtility.streamJSON(all, null, egonet_lmToPath.keySet(), nodeToWeight, null,
						timeStart)).build();
			} else {
				return Response.ok(StreamingUtility.streamJSON(all, null, egonet_lmToPath.keySet(), nodeToWeight, null,
						timeStart)).build();
			}
		} catch (ExecutionException e) {
			LOGGER.log(Level.SEVERE, "Exception thrown during getNeighbors", e);
			return Response.serverError().build();
		}
	}

	@Path("getNeighborhoodSize")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public String getNeighborhoodSize(@QueryParam("vertex") final int vertex) {
		int count = 0;
		try {
			Iterable<Integer> it = DataStore.getInstance().getAdjacentUndirected(vertex);
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
		return Response.ok(StreamingUtility.streamJSON(path_ints, path_ints, null, nodeToWeight, lmToShannon,
				timeStart)).build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAddition(@QueryParam("vertex") final int vertex, @QueryParam("peel") final int peelLevel) {
		long timeStart = System.nanoTime();
		LOGGER.info("vertex: " + vertex + " peel: " + peelLevel);
		Collection<Integer> path = getLandmarkPath(vertex, peelLevel);
		if (null == path)
			return getEgonet(vertex);
		Set<Integer> path_ints = new HashSet<Integer>(path);
		return Response.ok(StreamingUtility.streamJSON(path_ints, path_ints, peelToLmToPath.get(peelLevel).keySet(),
				nodeToWeight, lmToShannon, timeStart)).build();
	}

	@Path("peelLevels")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public String getPeelLevels() {
		return Integer.toString(peelToLmToPath.size());
	}

	@Path("description")
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public String getDescription(@QueryParam("vertex") final int vertex) {
		Set<Integer> s = new HashSet<>();
		s.add(vertex);
		try {
			return DataStore.getInstance().getDescription(s).get(vertex);
		} catch (SQLException | NamingException e) {
			LOGGER.log(Level.SEVERE, "Exception thrown during getDescription", e);
			return "";
		}
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
				for (int x : DataStore.getInstance().getAdjacentUndirected(u)) {
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
			// HashSet<Integer> hs = new HashSet<>(queue);
			// DataStore.getInstance().prefetch(hs);
		}

		ArrayList<Integer> pathToLM = new ArrayList<Integer>();
		pathToLM.add(vertex);

		if (!landmarkFound)
			return null;

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

	private Collection<Integer> getShortestPath(int one, int two) {
		HashSet<Integer> s = new HashSet<>();
		s.add(two);
		return getShortestPath(one, s);
	}

	// bfs-driven true shortest path
	private Collection<Integer> getShortestPath(int one, HashSet<Integer> dests) {
		if (dests.isEmpty()) {
			LinkedList<Integer> path = new LinkedList<Integer>();
			path.add(one);
			return path;
		}
		Queue<Integer> queue = new LinkedList<Integer>();
		HashSet<Integer> visited = new HashSet<Integer>();
		HashMap<Integer, Integer> gnToParent = new HashMap<Integer, Integer>();

		queue.add(one);
		visited.add(one);
		gnToParent.put(one, one);
		int found = -1;
		while (!queue.isEmpty() && found < 0) {
			Integer u = queue.remove();
			try {
				for (int x : DataStore.getInstance().getAdjacentUndirected(u)) {
					if (!visited.contains(x)) {
						queue.add(x);
						visited.add(x);
						gnToParent.put(x, u);
					}
					if (dests.contains(x)) {
						found = x;
						break;
					}
				}
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
			// HashSet<Integer> hs = new HashSet<>(queue);
			// DataStore.getInstance().prefetch(hs);
		}

		LinkedList<Integer> path = new LinkedList<Integer>();
		path.add(one);

		if (found < 0) {
			path.addAll(dests);
			return path;
		}

		// retrieve the path to the found landmark
		int curr = found;
		while (curr != one) {
			path.add(curr);
			curr = gnToParent.get(curr);
		}
		return path;
	}

}
