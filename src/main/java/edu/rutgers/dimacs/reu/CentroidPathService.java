package edu.rutgers.dimacs.reu;


import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ejb.Lock;
import javax.ejb.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONException;
import org.json.JSONObject;

import edu.rutgers.dimacs.reu.utility.*;
import edu.rutgers.dimacs.reu.utility.DataStore.EdgeTypeGroup;
import static javax.ejb.LockType.READ;

@Singleton
@Path("centroidPathService")
@Lock(READ)
public class CentroidPathService {
	private static final Logger LOGGER = Logger.getLogger(CentroidPathService.class.getName());
	private final ArrayList<Map<Integer, ArrayList<Integer>>> peelToLmToPath;

	@SuppressWarnings("unchecked")
	public CentroidPathService() {
		ArrayList<Map<Integer, ArrayList<Integer>>> levels = null;
		Map<Integer, ArrayList<Integer>> map = null;
		try {
			levels = (ArrayList<Map<Integer, ArrayList<Integer>>>) loadSerialized("pvTo_LmToPath.ser");
			map = (Map<Integer, ArrayList<Integer>>) loadSerialized("lmToPath.ser");
		} catch (ClassNotFoundException | IOException e) {
			e.printStackTrace();
		} finally {
			if(null == levels && null != map) {
				levels = new ArrayList<>();
				levels.add(map);
			}
			peelToLmToPath = levels;
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
		if(null == is) return null;
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
		if(level < 0 || level >= peelToLmToPath.size()) {
			level = 0;
		}
		Set<Integer> all = new HashSet<>(peelToLmToPath.get(level).keySet());
		for (Integer lm : peelToLmToPath.get(level).keySet()) {
			all.addAll(peelToLmToPath.get(level).get(lm));
		}
		Graph graph = StreamingUtility.buildGraph(all);
		return Response.ok(StreamingUtility.finalJSON(graph, null, peelToLmToPath.get(level).keySet(), timeStart)).build();
	}
	
	@Path("getNeighbors")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getNeighbors(@QueryParam("vertex") final int vertex) {
		long timeStart = System.nanoTime();
		Set<Integer> vertices = new HashSet<>();
		Set<Integer> path_ints = new HashSet<>();
		vertices.add(vertex);
		path_ints.add(vertex);
		try {
			Iterable<Integer> it = DataStore.getInstance()
					.getAdjacentUndirected(vertex, EdgeTypeGroup.NORMALONLY);
			for(Integer n : it) {
				vertices.add(n);
			}
		} catch (ExecutionException e) {
			LOGGER.log(Level.SEVERE, "Exception thrown during getNeighbors", e);
			return Response.serverError().build();
		}
		Graph graph = StreamingUtility.buildGraph(vertices);
		return Response.ok(StreamingUtility.finalJSON(graph, path_ints, null, timeStart)).build();
	}

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response getAddition(String input) {
		JSONObject obj = new JSONObject(input);
		String newNode;
		try {
			newNode = obj.getString("newNode");
		} catch(JSONException e) {
			return Response.serverError().build();
		}
		int peelLevel = 0;
		try {
			peelLevel = obj.getInt("peel");
		} catch(JSONException e) {}
		long timeStart = System.nanoTime();
		LOGGER.info("query: " + input);
		Collection<Integer> path = getPath(newNode, peelLevel);
		Set<Integer> path_ints = new HashSet<Integer>(path);
		Graph graph = StreamingUtility.buildGraph(path_ints);
		return Response.ok(StreamingUtility.finalJSON(graph, path_ints, peelToLmToPath.get(peelLevel).keySet(), timeStart)).build();
	}
	
	@Path("peelLevels")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public String getPeelLevels() {
		return Integer.toString(peelToLmToPath.size());
	}

	private Collection<Integer> getPath(String inputStr, int level) {
		int input = Integer.parseInt(inputStr);
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
					if (peelToLmToPath.get(level).keySet().contains(x)) {
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

		ArrayList<Integer> output_path = new ArrayList<>(pathToLM);
		output_path.addAll(peelToLmToPath.get(level).get(closestlm));
		return output_path;
	}

	
	
	/*private void labelVertices(Graph graph) {
		LinkedList<Integer> wordNodes = new LinkedList<>();
		for (GraphNode gn : graph.getNodeSet()) {
			wordNodes.add(Integer.parseInt(gn.toString()));
		}
		Map<Integer, Map<String, Integer>> allWords = null;
		try {
			allWords = ds.getWordMultiSet(wordNodes);
		} catch (SQLException | NamingException e) {
			LOGGER.warning("Words and descriptions not available from DataStore");
		}
		for (GraphNode gn : graph.getNodeSet()) {
			int gn_int = Integer.parseInt(gn.toString());
			StringBuilder label = new StringBuilder();
			if(allWords != null) {
				ArrayList<String> selectedWords = new ArrayList<String>();
				Map<String, Integer> nodeWords = allWords.get(gn_int);
	
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
				for (String selected : selectedWords) {
					label.append("-");
					label.append(selected);
				}
				gn.label = label.toString();
			}
		}
	}*/

}
