package edu.rutgers.dimacs.reu;

import java.sql.SQLException;
import java.util.Collection;
//import java.util.concurrent.TimeUnit;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;

import edu.rutgers.dimacs.reu.utility.MySQLHandler;
import edu.rutgers.dimacs.reu.utility.MySQLHandler.CrossrefTypes;

public class DataStore {

	public static LoadingCache<Integer, Collection<Integer>> edgeCache = CacheBuilder
			.newBuilder().maximumWeight(1000000)
			// .expireAfterAccess(600, TimeUnit.MINUTES)
			.weigher(new Weigher<Integer, Collection<Integer>>() {
				@Override
				public int weigh(Integer key, Collection<Integer> value) {
					return value.size() + 1;
				}
			}).build(new CacheLoader<Integer, Collection<Integer>>() {
				@Override
				public Collection<Integer> load(Integer node)
						throws SQLException {
					return MySQLHandler.getCrossrefsLeaving(node,
							CrossrefTypes.NORMALONLY);
				}
			});
}
