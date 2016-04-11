package edu.rutgers.dimacs.reu.utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
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

	private static final Logger LOGGER = Logger.getLogger(DataStore.class.getName());
	private final LoadingCache<Integer, ImmutableSet<Edge>> crossrefCache;

	private static DataStore instance = new DataStore(); // static singleton

	public static synchronized DataStore getInstance() {
		if (null == instance) {
			instance = new DataStore();
		}
		return instance;
	}

	private DataStore() {
		crossrefCache = CacheBuilder.newBuilder().maximumWeight(10000000).expireAfterAccess(60, TimeUnit.MINUTES)
				.weigher(new Weigher<Integer, ImmutableSet<Edge>>() {
					@Override
					public int weigh(Integer key, ImmutableSet<Edge> value) {
						return value.size() + 1;
					}
				}).build(new CacheLoader<Integer, ImmutableSet<Edge>>() {
					@Override
					public ImmutableSet<Edge> load(Integer node) throws SQLException, NamingException {
						ImmutableSet.Builder<Edge> setBuilder = ImmutableSet.<Edge> builder();
						ResultSet rs = SQLiteHandler.getInstance().getCrossrefsIncident(node);
						while (rs.next()) {
							Edge e = null;
							if (node.intValue() == rs.getInt(1)) {
								e = new Edge(rs.getInt(2), true, 0);
							} else {
								e = new Edge(rs.getInt(1), false, 0);
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
			private Edge current = it.hasNext() ? it.next() : null;

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
						if (hasNext()) {
							next();
						}
					}
				};
			}

		};

	}

	private static class Edge {
		public Edge(int dest, boolean forward, double weight) {
			super();
			this.forward = forward;
			this.dest = dest;
			this.weight = weight;
		}

		public final boolean forward;
		public final int dest;
		public final double weight;

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Edge))
				return false;
			Edge e = (Edge) o;
			return e.forward == this.forward && e.dest == this.dest;
		}

		@Override
		public int hashCode() {
			return (Integer.toString(dest) + ":" + Boolean.toString(forward)).hashCode();
		}
	}

	public TreeMap<Integer, TreeMap<Integer, Double>> getSubgraphInduced(Set<Integer> vertices)
			throws ExecutionException {
		//prefetch(vertices);
		TreeMap<Integer, TreeMap<Integer, Double>> map = new TreeMap<>();
		for (Integer i : vertices) {
			map.put(i, new TreeMap<Integer, Double>());
		}
		for (Integer i : vertices) {
			for (Edge j : crossrefCache.get(i)) {
				if (vertices.contains(j.dest)) {
					if (i < j.dest) {
						map.get(i).put(j.dest, j.weight);
					} else {
						map.get(j.dest).put(i, j.weight);
					}
				}
			}
		}
		return map;
	}

	public Map<Integer, String> getDescription(Set<Integer> vertices) throws SQLException, NamingException {
		if (null == vertices)
			return null;
		return SQLiteHandler.getInstance().getDescription(vertices);
	}

	public void prefetch(final Set<Integer> vertices) {
		vertices.removeAll(crossrefCache.getAllPresent(vertices).keySet());
		Map<Integer, ImmutableSet.Builder<Edge>> map = new HashMap<>();
		for (Integer i : vertices) {
			map.put(i, ImmutableSet.<Edge> builder());
		}
		try {
			ResultSet rs = SQLiteHandler.getInstance().getCrossrefsIncident(vertices);
			while (rs.next()) {
				int one = rs.getInt(1);
				int two = rs.getInt(2);
				if (map.containsKey(one)) {
					map.get(one).add(new Edge(two, true, 0));
				}
				if (map.containsKey(two)) {
					map.get(two).add(new Edge(one, false, 0));
				}
			}
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "Exception while loading multiple adj lists", e);
			return;
		}
		for(int k : map.keySet()) {
			crossrefCache.put(k, map.get(k).build());
		}
	}

}