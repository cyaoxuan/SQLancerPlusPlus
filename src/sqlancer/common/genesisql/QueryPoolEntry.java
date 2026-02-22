package sqlancer.common.genesisql;

import sqlancer.common.ast.newast.NewUnaryPostfixOperatorNode;
import sqlancer.common.ast.newast.NewUnaryPrefixOperatorNode;
import sqlancer.common.ast.newast.Node;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.general.GeneralToStringVisitor;
import sqlancer.general.ast.GeneralExpression;
import sqlancer.general.ast.GeneralSelect;
import sqlancer.general.ast.GeneralUnaryPostfixOperator;
import sqlancer.general.ast.GeneralUnaryPrefixOperator;

public class QueryPoolEntry {
	private final GeneralSelect originalQuery; // query with no WHERE clause
	private final GeneralSelect firstQuery; // query with original predicate
	private final GeneralSelect secondQuery; // query with negated predicate
	private final GeneralSelect thirdQuery; // query with nullified predicate
	private final ExpectedErrors errors; // expected errors for this query
	private int fitnessScore;
	private final int generation;

	/**
	 * Constructor that generates all query variants from firstQuery
	 */
	public QueryPoolEntry(GeneralSelect firstQuery, ExpectedErrors errors, int fitnessScore, int generation) {
		this.firstQuery = firstQuery;
		this.errors = errors;
		this.fitnessScore = fitnessScore;
		this.generation = generation;
		
		// Generate all query variants during initialization
		this.originalQuery = generateOriginalQuery(firstQuery);
		this.secondQuery = generateSecondQuery(firstQuery);
		this.thirdQuery = generateThirdQuery(firstQuery);
	}

	/**
	 * Generate original query (no WHERE clause) from firstQuery
	 */
	private GeneralSelect generateOriginalQuery(GeneralSelect firstQuery) {
		GeneralSelect originalQuery = new GeneralSelect();
		originalQuery.setFetchColumns(firstQuery.getFetchColumns());
		originalQuery.setJoinList(firstQuery.getJoinList());
		originalQuery.setFromList(firstQuery.getFromList());
		originalQuery.setOrderByExpressions(firstQuery.getOrderByExpressions());
		originalQuery.setWhereClause(null);
		return originalQuery;
	}

	/**
	 * Generate second query (negated predicate) from firstQuery
	 */
	private GeneralSelect generateSecondQuery(GeneralSelect firstQuery) {
		GeneralSelect secondQuery = new GeneralSelect();
		secondQuery.setFetchColumns(firstQuery.getFetchColumns());
		secondQuery.setJoinList(firstQuery.getJoinList());
		secondQuery.setFromList(firstQuery.getFromList());
		secondQuery.setOrderByExpressions(firstQuery.getOrderByExpressions());
		
		// Negate the WHERE clause
		Node<GeneralExpression> whereClause = (Node<GeneralExpression>) firstQuery.getWhereClause();
		Node<GeneralExpression> negatedClause = negatePredicate(whereClause);
		secondQuery.setWhereClause(negatedClause);
		
		return secondQuery;
	}
	
	/**
	 * Generate third query (null predicate) from firstQuery
	 */
	private GeneralSelect generateThirdQuery(GeneralSelect firstQuery) {
		GeneralSelect thirdQuery = new GeneralSelect();
		thirdQuery.setFetchColumns(firstQuery.getFetchColumns());
		thirdQuery.setJoinList(firstQuery.getJoinList());
		thirdQuery.setFromList(firstQuery.getFromList());
		thirdQuery.setOrderByExpressions(firstQuery.getOrderByExpressions());
		
		// Apply IS NULL to the WHERE clause
		Node<GeneralExpression> whereClause = (Node<GeneralExpression>) firstQuery.getWhereClause();
		Node<GeneralExpression> nullifiedClause = isNull(whereClause);
		thirdQuery.setWhereClause(nullifiedClause);
		
		return thirdQuery;
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
		return firstQuery.getOrderByExpressions() != null && !firstQuery.getOrderByExpressions().isEmpty();
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
	
	/**
	 * Negate a predicate expression using NOT operator
	 */
	private Node<GeneralExpression> negatePredicate(Node<GeneralExpression> predicate) {
		return new NewUnaryPrefixOperatorNode<>(predicate, GeneralUnaryPrefixOperator.NOT);
	}

	/**
	 * Apply IS NULL to a predicate expression
	 */
	private Node<GeneralExpression> isNull(Node<GeneralExpression> expr) {
		return new NewUnaryPostfixOperatorNode<>(expr, GeneralUnaryPostfixOperator.IS_NULL);
	}
	
	@Override
	public String toString() {
		return "Query: " + GeneralToStringVisitor.asString(firstQuery) + ", Fitness Score: " + fitnessScore + ", Generation: " + generation;
	}
}