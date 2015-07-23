package edu.rutgers.dimacs.reu.utility;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.ResultSet;
import java.sql.SQLException;
//import java.util.concurrent.TimeUnit;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import javax.ejb.Singleton;
import javax.naming.NamingException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.common.collect.ImmutableSet;

@Singleton
public class DataStore {

	// public static final ImmutableMap<Integer, ImmutableSet<Integer>> adj =
	// null;
	private static final Logger LOGGER = Logger.getLogger(DataStore.class
			.getName());

	private final LoadingCache<Integer, ImmutableSet<Edge>> crossrefCache;

	private static DataStore instance = new DataStore();

	private DataStore() {
		crossrefCache = CacheBuilder.newBuilder().maximumWeight(10000000)
		// .expireAfterAccess(600, TimeUnit.MINUTES)
				.weigher(new Weigher<Integer, ImmutableSet<Edge>>() {
					@Override
					public int weigh(Integer key, ImmutableSet<Edge> value) {
						return value.size() + 1;
					}
				}).build(new CacheLoader<Integer, ImmutableSet<Edge>>() {
					@Override
					public ImmutableSet<Edge> load(Integer node)
							throws SQLException, NamingException {
						ImmutableSet.Builder<Edge> setBuilder = ImmutableSet
								.<Edge> builder();
						ResultSet rs = MySQLHandler.getInstance().getCrossrefsIncident(node);
						while (rs.next()) {
							EdgeType type;
							if (rs.getInt(3) == 1) {
								type = EdgeType.ADJACENT;
							} else if (rs.getInt(4) == 1) {
								type = EdgeType.CONTEXT;
							} else {
								type = EdgeType.NORMAL;
							}
							Edge e = null;
							if (node.intValue() == rs.getInt(1)) {
								e = new Edge(rs.getInt(2), type, true);
							} else {
								e = new Edge(rs.getInt(1), type, true);
							}
							setBuilder.add(e);
						}
						// return MySQLHandler.getCrossrefsLeaving(node,
						// CrossrefTypes.NORMALONLY);
						return setBuilder.build();
					}
				});
		try {
			fillCrossrefCache();
			LOGGER.info("Cache size: " + crossrefCache.size());
		} catch (NumberFormatException | IOException e) {
			LOGGER.warning("Failed to add edges to cache");
		}
	}

	private DataStore fillCrossrefCache() throws NumberFormatException, IOException {
		//ResultSet rs = MySQLHandler.getInstance().getCrossrefTable();
		Map<Integer, LinkedList<Edge>> map = new HashMap<>();
		InputStream is = DataStore.class.getClassLoader().getResourceAsStream("Cross_Refs.csv");
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String line;
		while (null != (line = br.readLine())) {
			String[] split = line.split(",");
			int one = Integer.parseInt(split[0]), two = Integer.parseInt(split[1]),
					three = Integer.parseInt(split[2]), four = Integer.parseInt(split[3]);
			EdgeType type;
			if (three == 1) {
				type = EdgeType.ADJACENT;
			} else if (four == 1) {
				type = EdgeType.CONTEXT;
			} else {
				type = EdgeType.NORMAL;
			}
			LinkedList<Edge> oneAdj = map.get(one), twoAdj = map
					.get(two);
			if (null == oneAdj) {
				oneAdj = new LinkedList<Edge>();
				map.put(one, oneAdj);
			}
			if (null == twoAdj) {
				twoAdj = new LinkedList<Edge>();
				map.put(two, twoAdj);
			}
			oneAdj.add(new Edge(two, type, true));
			twoAdj.add(new Edge(one, type, true));
		}
		for (Integer i : map.keySet()) {
			ImmutableSet.Builder<Edge> setBuilder = ImmutableSet
					.<Edge> builder();
			setBuilder.addAll(map.get(i));
			crossrefCache.put(i, setBuilder.build());
		}
		return this;
	}

	public static synchronized DataStore getInstance() {
		if (null == instance) {
			instance = new DataStore();
		}
		return instance;
	}

	/*public ImmutableSet<Edge> get(Integer key) throws ExecutionException {
		return cache.get(key);
	}*/
	
	public Set<Integer> getAdjacentUndirected(int node, EdgeType type) throws ExecutionException {
		HashSet<Integer> adjacents = new HashSet<>();
		ImmutableSet<Edge> edges = crossrefCache.get(node);
		for(Edge e : edges) {
			if(e.getType() == type) {
				adjacents.add(e.getDest());
			}
		}
		return adjacents;
	}
	
	public Set<Integer> getAdjacentUndirected(int node) throws ExecutionException {
		return getAdjacentUndirected(node, EdgeType.NORMAL);
	}

	private static final class Edge {
		public Edge(int dest, EdgeType type, boolean forward) {
			super();
			this.forward = forward;
			this.dest = dest;
			this.type = type;
		}

		boolean forward;
		private int dest;

		public boolean isForward() {
			return forward;
		}

		public int getDest() {
			return dest;
		}

		public EdgeType getType() {
			return type;
		}

		private EdgeType type;
	}

	public static enum EdgeType {
		NORMAL, CONTEXT, ADJACENT;
	}

	public Map<Integer, Collection<Integer>> getCrossrefsWithin(
			Set<Integer> nodes, EdgeType type) throws ExecutionException {
		Map<Integer, Collection<Integer>> map = new HashMap<>();
		for(Integer i : nodes) {
			LinkedList<Integer> list = new LinkedList<Integer>(); 
			ImmutableSet<Edge> adj = crossrefCache.get(i);
			for(Edge j : adj) {
				if(j.isForward() && j.getType() == type) {
					list.add(j.getDest());
				}
			}
			map.put(i, list);
		}
		return map;
	}
	
	public Map<Integer, Collection<Integer>> getCrossrefsWithin(
			Set<Integer> nodes) throws ExecutionException {
		return getCrossrefsWithin(nodes, EdgeType.NORMAL);
	}

	public Collection<? extends Integer> getSequencesWithKeyword(String word) throws NamingException, SQLException {
		return MySQLHandler.getInstance().getSequencesWithKeyword(word);
	}

	public Map<Integer, Map<String, Integer>> getWordMultiSet(
			LinkedList<Integer> sequenceIds) throws SQLException, NamingException {
		return MySQLHandler.getInstance().getWordMultiSet(sequenceIds);
	}

	public Map<Integer, String> getDescription(Iterable<Integer> path_ints) throws SQLException, NamingException {
		return MySQLHandler.getInstance().getDescription(path_ints);
	}
}