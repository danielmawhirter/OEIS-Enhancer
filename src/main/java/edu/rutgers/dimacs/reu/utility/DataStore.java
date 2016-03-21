package edu.rutgers.dimacs.reu.utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
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
	public static synchronized DataStore getInstance() {
		if (null == instance) {
			instance = new DataStore();
		}
		return instance;
	}
	private DataStore() {
		crossrefCache = CacheBuilder.newBuilder().maximumWeight(10000000)
				.expireAfterAccess(60, TimeUnit.MINUTES)
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
						ResultSet rs = SQLiteHandler.getInstance()
								.getCrossrefsIncident(node);
						while (rs.next()) {
							Edge e = null;
							if (node.intValue() == rs.getInt(1)) {
								e = new Edge(rs.getInt(2), true);
							} else {
								e = new Edge(rs.getInt(1), false);
							}
							setBuilder.add(e);
						}
						return setBuilder.build();
					}
				});
	}

	public Iterable<Integer> getAdjacentUndirected(int node) throws ExecutionException {
		ImmutableSet<Edge> edges = crossrefCache.get(node);

		final UnmodifiableIterator<Edge> it = edges.iterator();
		return new Iterable<Integer>() {
			private Edge current = it.next();

			@Override
			public Iterator<Integer> iterator() {
				return new Iterator<Integer>() {
					@Override
					public boolean hasNext() {
						return null != current;
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

	private static class Edge {
		public Edge(int dest, boolean forward) {
			super();
			this.forward = forward;
			this.dest = dest;
		}

		public final boolean forward;
		public final int dest;
	}

	public Map<Integer, Set<Integer>> getCrossrefsWithin (
			Set<Integer> vertices) throws ExecutionException {
		Map<Integer, Set<Integer>> map = new HashMap<>();
		for (Integer i : vertices) {
			Set<Integer> list = new HashSet<Integer>();
			ImmutableSet<Edge> adj = crossrefCache.get(i);
			for (Edge j : adj) {
				if (j.forward) {
					list.add(j.dest);
				}
			}
			map.put(i, list);
		}
		return map;
	}
	
	public TreeMap<Integer, TreeSet<Integer>> getSubgraphInduced (
			Set<Integer> vertices) throws ExecutionException {
		TreeMap<Integer, TreeSet<Integer>> map = new TreeMap<>();
		for(Integer i : vertices) {
			map.put(i, new TreeSet<Integer>());
		}
		for (Integer i : vertices) {
			for (Edge j : crossrefCache.get(i)) {
				if(vertices.contains(j.dest)) {
					if(i < j.dest) {
						map.get(i).add(j.dest);
					} else {
						map.get(j.dest).add(i);
					}
				}
			}
		}
		return map;
	}

	public Map<Integer, String> getDescription(Set<Integer> vertices)
			throws SQLException, NamingException {
		if(null == vertices) return null;
		return SQLiteHandler.getInstance().getDescription(vertices);
	}

}