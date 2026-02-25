package sqlancer.common.genesisql.crossover;

import java.util.ArrayList;

import sqlancer.common.ast.newast.Node;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.ast.GeneralExpression;
import sqlancer.general.ast.GeneralSelect;

/**
 * SwapWhereCrossover combines two parent SELECT queries by taking one parent's complete structure
 * (fetch columns, FROM clause, JOINs, ORDER BY) and replacing its WHERE clause with another parent's WHERE clause.
 * 
 * This strategy creates offspring by:
 * 1. Taking parent1's structure (columns, FROM, JOINs, ORDER BY)
 * 2. Replacing parent1's WHERE clause with parent2's WHERE clause
 * 
 * This is useful for testing whether different predicates work with different table structures,
 * and for discovering bugs that depend on specific combinations of structure and filtering logic.
 * 
 * Example:
 * Parent 1: SELECT c1 FROM t1 WHERE c1 > 10
 * Parent 2: SELECT c2 FROM t2 WHERE c2 < 5
 * Offspring: SELECT c1 FROM t1 WHERE c2 < 5
 */
public class SwapWhereCrossover extends Crossover {

    public SwapWhereCrossover() {
        super("Swap Where Crossover - replace WHERE clause while keeping query structure");
    }

    @Override
    public GeneralSelect crossover(GeneralSelect parent1, GeneralSelect parent2, GeneralGlobalState globalState) throws Exception {
        try {
            // Create a new SELECT statement for the offspring
            GeneralSelect offspring = new GeneralSelect();

            // Take parent1's structure
            offspring.setFetchColumns(new ArrayList<>(parent1.getFetchColumns()));
            offspring.setFromList(new ArrayList<>(parent1.getFromList()));
            offspring.setJoinList(new ArrayList<>(parent1.getJoinList()));
            offspring.setOrderByExpressions(new ArrayList<>(parent1.getOrderByExpressions()));

            // Replace WHERE clause with parent2's WHERE clause
            Node<GeneralExpression> parent2WhereClause = (Node<GeneralExpression>) parent2.getWhereClause();
            offspring.setWhereClause(parent2WhereClause);

            return offspring;

        } catch (Exception e) {
            // If crossover fails, return null to indicate failure
            return null;
        }
    }
}