package sqlancer.common.genesisql;

import sqlancer.common.query.ExpectedErrors;
import sqlancer.general.GeneralToStringVisitor;
import sqlancer.general.ast.GeneralSelect;

public class QueryPoolEntry {
	private final GeneralSelect originalQuery; // query with no WHERE clause
	private final GeneralSelect firstQuery; // query with original predicate
	private final GeneralSelect secondQuery; // query with negated predicate
	private final GeneralSelect thirdQuery; // query with null predicate
	private final boolean orderBy; // whether ORDER BY was generated
	private final ExpectedErrors errors; // expected errors for this query
	private int fitnessScore;
	private final int generation;

	public QueryPoolEntry(GeneralSelect originalQuery, GeneralSelect firstQuery, GeneralSelect secondQueryNegated, GeneralSelect thirdQueryNull, boolean orderBy, ExpectedErrors errors, int fitnessScore, int generation) {
		this.originalQuery = originalQuery;
		this.firstQuery = firstQuery;
		this.secondQuery = secondQueryNegated;
		this.thirdQuery = thirdQueryNull;
		this.orderBy = orderBy;
		this.errors = errors;
		this.fitnessScore = fitnessScore;
		this.generation = generation;
	}

	public GeneralSelect getOriginalQuery() {
		return originalQuery;
	}

	public GeneralSelect getFirstQuery() {
		return firstQuery;
	}
	
	public GeneralSelect getSecondQuery() {
		return secondQuery;
	}
	
	public GeneralSelect getThirdQuery() {
		return thirdQuery;
	}

	public boolean hasOrderBy() {
		return orderBy;
	}

	public ExpectedErrors getErrors() {
		return errors;
	}

	public int getFitnessScore() {
		return fitnessScore;
	}

	public void setFitnessScore(int fitnessScore) {
		this.fitnessScore = fitnessScore;
	}

	public int getGeneration() {
		return generation;
	}
	
	@Override
	public String toString() {
		return "Query: " + GeneralToStringVisitor.asString(firstQuery) + ", Fitness Score: " + fitnessScore + ", Generation: " + generation;
	}
}