package sqlancer.common.genesisql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sqlancer.general.GeneralToStringVisitor;

public class QueryPool {
	private List<QueryPoolEntry> queryPoolList;
	private Map<String, QueryPoolEntry> allGeneratedQueries;
	private Map<String, Integer> queryPlanPool; // Track query plans and their occurrence count
	
	public QueryPool() {
		this.queryPoolList = new ArrayList<>();
		this.allGeneratedQueries = new HashMap<>();
		this.queryPlanPool = new HashMap<>();
	}
	
	public List<QueryPoolEntry> getQueryPoolList() {
		return queryPoolList;
	}
	
	public void addQueryPoolEntry(QueryPoolEntry entry) {
		if (hasQueryBeenGenerated(GeneralToStringVisitor.asString(entry.getFirstQuery()))) {
			return;
		}
		
		this.queryPoolList.add(entry);
		this.allGeneratedQueries.put(GeneralToStringVisitor.asString(entry.getFirstQuery()), entry);
	}
	
	public void removeQueryPoolEntry(int index) {
		if (index >= 0 && index < queryPoolList.size()) {
			this.queryPoolList.remove(index);
		}
	}
	
	public QueryPoolEntry getQueryPoolEntry(int index) {
		if (index >= 0 && index < queryPoolList.size()) {
			return this.queryPoolList.get(index);
		}
		return null;
	}
	
	public int size() {
		return queryPoolList.size();
	}
	
	public void printQueryPool(int numEntries) {
		for (int i = 0; i < numEntries; i++) {
			System.out.println("Index: " + i + ", " + queryPoolList.get(i).toString());
		}
	}
	
	public void selectTopNQueries(int n) {
		// Sort by descending fitness scores
		this.queryPoolList.sort((a, b) -> Double.compare(b.getFitnessScore(), a.getFitnessScore()));
		
		// Remove everything after top N
		if (this.queryPoolList.size() > n) {
	        this.queryPoolList.subList(n, this.queryPoolList.size()).clear();
	    }
	}
	
	// Score decay to gradually kill off older queries :(
	public void decayFitnessScores() {
		for (QueryPoolEntry entry : this.queryPoolList) {
			entry.setFitnessScore(entry.getFitnessScore() - 1); // Can change decay value as needed, maybe it should be an option?
		}
	}
	
	public boolean hasQueryBeenGenerated(String queryString) {
		return allGeneratedQueries.containsKey(queryString);
	}
	
	public QueryPoolEntry getRandomQueryPoolEntry() {
		if (queryPoolList.isEmpty()) {
			return null;
		}
		int index = (int) (Math.random() * queryPoolList.size());
		return queryPoolList.get(index);
	}
	
	// For evaluation, but not used yet since we need to figure out how to extract query plans from the specific DBMS
	public boolean addQueryPlan(String queryPlan) {
		if (!queryPlanPool.containsKey(queryPlan)) {
			queryPlanPool.put(queryPlan, 1);
			return true; // New query plan
		} else {
			queryPlanPool.put(queryPlan, queryPlanPool.get(queryPlan) + 1);
			return false; // Duplicate query plan
		}
	}
}