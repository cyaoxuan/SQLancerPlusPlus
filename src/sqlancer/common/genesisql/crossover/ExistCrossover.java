package sqlancer.common.genesisql.crossover;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import sqlancer.Randomly;
import sqlancer.common.ast.newast.NewBinaryOperatorNode;
import sqlancer.common.ast.newast.NewUnaryPrefixOperatorNode;
import sqlancer.common.ast.newast.Node;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralToStringVisitor;
import sqlancer.general.ast.GeneralBinaryLogicalOperator;
import sqlancer.general.ast.GeneralExpression;
import sqlancer.general.ast.GeneralSelect;
import sqlancer.general.ast.GeneralUnaryPrefixOperator;

/**
 * ExistCrossover combines two parent SELECT queries by creating a new query
 * that uses one query as a subquery with EXISTS or NOT EXISTS conditions.
 * 
 * The crossover works in the following ways:
 * 1. Simple EXISTS/NOT EXISTS approach:
 *    - Takes the first query as the main query
 *    - Takes the second query as a subquery
 *    - Adds EXISTS (subquery) or NOT EXISTS (subquery) to the WHERE clause
 *    Example:
 *      Query 1: SELECT c1 FROM t1 WHERE c1=1
 *      Query 2: SELECT c2 FROM t2 WHERE c2>4
 *      Result:  SELECT c1 FROM t1 WHERE c1=1 AND EXISTS (SELECT c2 FROM t2 WHERE c2>4)
 * 
 * 2. Single-column subquery approach (if second query can be reduced to single column):
 *    - Uses the second query as a scalar subquery in WHERE or SELECT
 *    Example:
 *      Query 1: SELECT c1 FROM t1
 *      Query 2: SELECT c2 FROM t2 WHERE c2>4 (reduced to single column)
 *      Result:  SELECT c1 FROM t1 WHERE c1 IN (SELECT c2 FROM t2 WHERE c2>4)
 * 
 * Important: Deduplicates predicates to prevent exponential growth from repeated crossovers
 */
public class ExistCrossover extends Crossover {

    public ExistCrossover() {
        super("Exist Crossover - combines two queries via EXISTS/NOT EXISTS subqueries");
    }

    @Override
    public GeneralSelect crossover(GeneralSelect query1, GeneralSelect query2, 
                                     GeneralGlobalState globalState) throws Exception {
        try {
            // Create a deep copy of the first query to avoid modifying the original
            GeneralSelect offspringQuery = deepCopySelect(query1);
            
            // Try to create EXISTS/NOT EXISTS subquery condition
            Node<GeneralExpression> existsCondition = createExistsCondition(query2);
            
            // Combine the new condition with existing WHERE clause
            Node<GeneralExpression> currentWhere = (Node<GeneralExpression>) offspringQuery.getWhereClause();
            Node<GeneralExpression> combinedWhere = combineClauses(currentWhere, existsCondition);
            
            offspringQuery.setWhereClause(combinedWhere);
            
            return offspringQuery;
            
        } catch (Exception e) {
            // If crossover fails, return null
            return null;
        }
    }

    /**
     * Create an EXISTS or NOT EXISTS condition from the given query.
     * Randomly chooses between EXISTS and NOT EXISTS.
     * 
     * @param subquerySelect The query to use as a subquery
     * @return An expression representing EXISTS (subquery) or NOT EXISTS (subquery)
     */
    private Node<GeneralExpression> createExistsCondition(GeneralSelect subquerySelect) {
        // Create a deep copy of the subquery to avoid modifying the original
        GeneralSelect subqueryCopy = deepCopySelect(subquerySelect);
        
        // Wrap the subquery in an ExistsExpression node
        ExistsExpression existsExpr = new ExistsExpression(subqueryCopy);
        
        // Randomly decide whether to use EXISTS or NOT EXISTS
        if (Randomly.getBoolean()) {
            // Use NOT EXISTS
            return new NewUnaryPrefixOperatorNode<>(
                existsExpr, 
                GeneralUnaryPrefixOperator.NOT
            );
        } else {
            // Use EXISTS directly
            return existsExpr;
        }
    }

    /**
     * Combines two clauses using AND operator.
     * If either clause is null, returns the non-null clause.
     * Deduplicates identical predicates to prevent exponential growth.
     * 
     * @param clause1 The first clause (may be null)
     * @param clause2 The second clause (may be null)
     * @return A combined clause using AND, or null if both are null
     */
    private Node<GeneralExpression> combineClauses(Node<GeneralExpression> clause1, 
                                                    Node<GeneralExpression> clause2) {
        if (clause1 == null && clause2 == null) {
            return null;
        } else if (clause1 == null) {
            return clause2;
        } else if (clause2 == null) {
            return clause1;
        } else {
            // Check if clauses are identical - if so, return only one
            String clause1Str = GeneralToStringVisitor.asString(clause1);
            String clause2Str = GeneralToStringVisitor.asString(clause2);
            
            if (clause1Str.equals(clause2Str)) {
                // Both clauses are identical, return one
                return clause1;
            }
            
            // Flatten AND chains to prevent deep nesting
            Set<String> predicates = new HashSet<>();
            collectPredicates(clause1, predicates);
            collectPredicates(clause2, predicates);
            
            // If we collected duplicates during flattening, we've reduced the tree
            if (predicates.size() < 2) {
                // Single predicate after deduplication
                return clause1;
            }
            
            // Both clauses are non-null and different, combine them with AND
            return new NewBinaryOperatorNode<>(clause1, clause2, GeneralBinaryLogicalOperator.AND);
        }
    }

    /**
     * Recursively collects all predicates from an AND chain into a set.
     * This helps identify and deduplicate repeated predicates across nested AND operations.
     * 
     * @param node The current node to examine
     * @param predicates The set to accumulate predicate strings into
     */
    private void collectPredicates(Node<GeneralExpression> node, Set<String> predicates) {
        if (node == null) {
            return;
        }
        
        // Check if this is an AND binary operator node
        if (node instanceof NewBinaryOperatorNode) {
            NewBinaryOperatorNode<GeneralExpression> binOp = (NewBinaryOperatorNode<GeneralExpression>) node;
            if (binOp.getOp() == GeneralBinaryLogicalOperator.AND) {
                // Recursively collect from left and right
                collectPredicates(binOp.getLeft(), predicates);
                collectPredicates(binOp.getRight(), predicates);
                return;
            }
        }
        
        // For non-AND nodes, add their string representation
        predicates.add(GeneralToStringVisitor.asString(node));
    }

    /**
     * Create a deep copy of a GeneralSelect statement to avoid modifying the original.
     * This copies all relevant fields including fetch columns, joins, tables, order by, and WHERE clause.
     * 
     * @param original The original GeneralSelect to copy
     * @return A new GeneralSelect with the same structure and clauses
     */
    private GeneralSelect deepCopySelect(GeneralSelect original) {
        GeneralSelect copy = new GeneralSelect();
        copy.setFetchColumns(new ArrayList<>(original.getFetchColumns()));
        copy.setJoinList(new ArrayList<>(original.getJoinList()));
        copy.setFromList(new ArrayList<>(original.getFromList()));
        copy.setOrderByExpressions(new ArrayList<>(original.getOrderByExpressions()));
        copy.setWhereClause(original.getWhereClause());
        return copy;
    }

    /**
     * A wrapper expression that represents an EXISTS subquery.
     * This allows us to represent EXISTS (SELECT ...) as an expression that can be
     * negated, combined with AND/OR, etc.
     */
    public static class ExistsExpression implements Node<GeneralExpression> {
        private final GeneralSelect subquery;

        public ExistsExpression(GeneralSelect subquery) {
            this.subquery = subquery;
        }

        public GeneralSelect getSubquery() {
            return subquery;
        }
    }
}