package edu.rutgers.dimacs.reu.utility;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
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
import com.google.common.collect.UnmodifiableIterator;

@Singleton
public class DataStore {

	private static final Logger LOGGER = Logger.getLogger(DataStore.class
			.getName());
	private final LoadingCache<Integer, ImmutableSet<Edge>> crossrefCache;

	private static DataStore instance = new DataStore(); //static singleton

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
						ResultSet rs = MySQLHandler.getInstance()
								.getCrossrefsIncident(node);
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
								e = new Edge(rs.getInt(2), type, false);
							}
							setBuilder.add(e);
						}
						return setBuilder.build();
					}
				});
		try {
			fillCrossrefCache();
			LOGGER.info("Cache size: " + crossrefCache.size());
		} catch (NumberFormatException | IOException e) {
			LOGGER.warning("Failed to add edges to cache");
		}
		try {
			MySQLHandler.getInstance().verifyAccess();
		} catch (SQLException | NamingException e) {
			LOGGER.warning("Database-connected datasource unavailable");
		}
	}

	private DataStore fillCrossrefCache() throws NumberFormatException,
			IOException {
		// ResultSet rs = MySQLHandler.getInstance().getCrossrefTable();
		Map<Integer, LinkedList<Edge>> map = new HashMap<>();
		InputStream is = DataStore.class.getClassLoader().getResourceAsStream(
				"Cross_Refs.csv");
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String line;
		while (null != (line = br.readLine())) {
			String[] split = line.split(",");
			int one = Integer.parseInt(split[0]), two = Integer
					.parseInt(split[1]), three = Integer.parseInt(split[2]), four = Integer
					.parseInt(split[3]);
			EdgeType type;
			if (three == 1) {
				type = EdgeType.ADJACENT;
			} else if (four == 1) {
				type = EdgeType.CONTEXT;
			} else {
				type = EdgeType.NORMAL;
			}
			LinkedList<Edge> oneAdj = map.get(one), twoAdj = map.get(two);
			if (null == oneAdj) {
				oneAdj = new LinkedList<Edge>();
				map.put(one, oneAdj);
			}
			if (null == twoAdj) {
				twoAdj = new LinkedList<Edge>();
				map.put(two, twoAdj);
			}
			oneAdj.add(new Edge(two, type, true));
			twoAdj.add(new Edge(one, type, false));
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

	public Iterable<Integer> getAdjacentUndirected(int node,
			final EdgeTypeGroup typeGroup) throws ExecutionException {
		ImmutableSet<Edge> edges = crossrefCache.get(node);

		final UnmodifiableIterator<Edge> it = edges.iterator();
		return new Iterable<Integer>() {
			private Edge current = it.next();

			@Override
			public Iterator<Integer> iterator() {
				return new Iterator<Integer>() {
					@Override
					public boolean hasNext() {
						if (null == current) {
							return false;
						}
						while (!typeGroup.compatible(current.type)) {
							if (!it.hasNext()) {
								return false;
							}
							current = it.next();
						}
						return true;
					}

					@Override
					public Integer next() {
						Integer select = current.dest;
						if (it.hasNext()) {
							current = it.next();
						} else {
							current = null;
						}
						return select;
					}
					
					@Override
					public void remove() {
						if(hasNext()) {
							next();
						}
					}
				};
			}

		};

	}

	public Iterable<Integer> getAdjacentUndirected(int node)
			throws ExecutionException {
		return getAdjacentUndirected(node, EdgeTypeGroup.NORMALONLY);
	}

	private static class Edge {
		public Edge(int dest, EdgeType type, boolean forward) {
			super();
			this.forward = forward;
			this.dest = dest;
			this.type = type;
		}

		public final boolean forward;
		public final int dest;
		public final EdgeType type;
	}

	public static enum EdgeType {
		NORMAL(true, false, false), CONTEXT(false, true, false), ADJACENT(
				false, false, true);
		public final boolean normal, context, adjacent;

		private EdgeType(boolean normal, boolean context, boolean adjacent) {
			this.normal = normal;
			this.context = context;
			this.adjacent = adjacent;
		}
	}

	public static enum EdgeTypeGroup {
		ALL(true, true, true), CONTEXTANDNORMAL(true, true, false), NORMALONLY(
				true, false, false);
		public final boolean normal, context, adjacent;

		private EdgeTypeGroup(boolean normal, boolean context, boolean adjacent) {
			this.normal = normal;
			this.context = context;
			this.adjacent = adjacent;
		}

		public boolean compatible(EdgeType e) {
			return (this.normal && e.normal) || (this.context && e.context)
					|| (this.adjacent && e.adjacent);
		}
	}

	public Map<Integer, Collection<Integer>> getCrossrefsWithin(
			Set<Integer> nodes, EdgeType type) throws ExecutionException {
		Map<Integer, Collection<Integer>> map = new HashMap<>();
		for (Integer i : nodes) {
			LinkedList<Integer> list = new LinkedList<Integer>();
			ImmutableSet<Edge> adj = crossrefCache.get(i);
			for (Edge j : adj) {
				if (j.forward && j.type == type) {
					list.add(j.dest);
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

	public Collection<? extends Integer> getSequencesWithKeyword(String word)
			throws NamingException, SQLException {
		return MySQLHandler.getInstance().getSequencesWithKeyword(word);
	}

	public Map<Integer, Map<String, Integer>> getWordMultiSet(
			LinkedList<Integer> sequenceIds) throws SQLException,
			NamingException {
		return MySQLHandler.getInstance().getWordMultiSet(sequenceIds);
	}

	public Map<Integer, String> getDescription(Iterable<Integer> path_ints)
			throws SQLException, NamingException {
		if(null == path_ints) return null;
		return MySQLHandler.getInstance().getDescription(path_ints);
	}

}