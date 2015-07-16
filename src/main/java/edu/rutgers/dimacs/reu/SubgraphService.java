package edu.rutgers.dimacs.reu;

import static javax.ejb.LockType.READ;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.ejb.Lock;
import javax.ejb.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import edu.rutgers.dimacs.reu.utility.*;

@Path("subgraph")
@Singleton
@Lock(READ)
public class SubgraphService {
	public static final List<String> knownKeywords = Arrays.asList("allocated",
			"base", "bref", "changed", "cofr", "cons", "core", "dead", "dumb",
			"easy", "eigen", "fini", "frac", "full", "hard", "hear", "less",
			"look", "more", "mult", "new", "nice", "nonn", "obsc", "recycled",
			"sign", "tabf", "tabl", "uned", "unkn", "walk", "word");
	
	@GET
	@Path("{query}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response handleQuery(@PathParam("query") String query) {
		return Response.serverError().build();
	}

	public Iterable<Integer> subsetByKeywords(Iterable<Integer> baseNodes,
			Iterable<String> keywords) throws SQLException {
		Set<Integer> allWithKeywords = new TreeSet<>();
		for (String s : keywords) {
			String word = s.toLowerCase();
			if(!knownKeywords.contains(word)) {
				throw new RuntimeException("Input is not a known keyword");
			} else {
				allWithKeywords.addAll(MySQLHandler.getSequencesWithKeyword(word));
			}
		}
		List<Integer> subsetWithKeywords = new LinkedList<>();
		for(Integer i : baseNodes) {
			if(allWithKeywords.contains(i)) {
				subsetWithKeywords.add(i);
			}
		}
		return subsetWithKeywords;
	}

	public Graph buildGraph(Set<Integer> nodes) throws SQLException {
		Graph graph = new Graph(false, "subgraph");
		//Map<Integer, Collection<Integer>> map = MySQLHandler.getCrossrefsLeaving(nodes, MySQLHandler.CrossrefTypes.NORMALONLY);
		Map<Integer, Collection<Integer>> map = MySQLHandler.getAllCrossrefs();
		for (Integer i : map.keySet()) {
			for(Integer j : map.get(i)) {
				if(nodes.contains(i) && nodes.contains(j)) {
					graph.addEdge(i.toString(), j.toString());
				}
			}
		}
		return graph;
	}

	public Graph peel(Graph graph, int level) {
		LinkedList<String> peeled = new LinkedList<>();
		LinkedList<GraphNode> nodes = new LinkedList<>();
		for (GraphNode gn : graph.getNodeSet()) {
			if(gn.getDegree() <= level) {
				nodes.push(gn);
			}
		}
		while(!nodes.isEmpty()) {
			GraphNode current = nodes.pop();
			if(current.getDegree() <= level) {
				for(GraphNode neighbor : current.getNeighbors()) {
					nodes.push(neighbor);
				}
				if(graph.getNode(current.id) == current ) {
					graph.removeNode(current);
					peeled.add(current.toString());
				}
			}
		}
		return graph;
	}

}