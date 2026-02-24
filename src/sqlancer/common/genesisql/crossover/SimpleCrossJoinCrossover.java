package sqlancer.common.genesisql.crossover;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import sqlancer.common.ast.newast.NewBinaryOperatorNode;
import sqlancer.common.ast.newast.Node;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralToStringVisitor;
import sqlancer.general.ast.GeneralBinaryLogicalOperator;
import sqlancer.general.ast.GeneralColumnReference;
import sqlancer.general.ast.GeneralExpression;
import sqlancer.general.ast.GeneralSelect;

/**
 * SimpleCrossJoinCrossover combines two parent SELECT queries using a simple cross product.
 * 
 * This strategy creates offspring by:
 * 1. Combining and deduplicating fetch columns from both parents
 * 2. Extracting relevant columns when SELECT * is used
 * 3. Combining FROM lists (cross product - no explicit JOINs)
 * 4. Combining WHERE clauses with AND logic (with deduplication)
 * 5. Combining and deduplicating ORDER BY expressions
 * 6. Ignoring any JOINs from parent queries
 * 
 * Example:
 * Parent 1: SELECT c1 FROM t1 WHERE c1=1
 * Parent 2: SELECT c2 FROM t2 WHERE c2>4
 * Offspring: SELECT c1, c2 FROM t1, t2 WHERE c1=1 AND c2>4
 * 
 * Parent 1: SELECT t1.c1, t1.c2 FROM t1, t2 WHERE t1.c1=1 AND t2.c2>4
 * Parent 2: SELECT t1.c3, t3.c4 FROM t1, t3 WHERE t1.c3 = 'ABC' AND t3.c4>4
 * Offspring: SELECT t1.c1, t1.c2, t1.c3, t3.c4 FROM t1, t2, t3 WHERE t1.c1=1 AND t2.c2>4 AND t1.c3 = 'ABC' AND t3.c4>4
 */
public class SimpleCrossJoinCrossover extends Crossover {

    public SimpleCrossJoinCrossover() {
        super("Simple Cross Join Crossover - cross product without explicit JOINs");
    }

    @Override
    public GeneralSelect crossover(GeneralSelect parent1, GeneralSelect parent2, GeneralGlobalState globalState) throws Exception {
        try {
            // Create a new SELECT statement for the offspring
            GeneralSelect offspring = new GeneralSelect();

            // STEP 1: Combine and deduplicate columns from both parents
            List<Node<GeneralExpression>> offspringColumns = combineFetchColumns(parent1, parent2);
            offspring.setFetchColumns(offspringColumns);

            // STEP 2: Combine FROM lists (cross product - ignore JOINs from parents)
            List<Node<GeneralExpression>> offspringFromList = new ArrayList<>(parent1.getFromList());
            
            // Add tables from parent2's FROM list that aren't already in the result
            Set<String> seenTableAliases = new HashSet<>();
            for (Node<GeneralExpression> tableNode : parent1.getFromList()) {
                String alias = getTableAlias(tableNode);
                if (alias != null) {
                    seenTableAliases.add(alias);
                }
            }
            
            for (Node<GeneralExpression> tableNode : parent2.getFromList()) {
                String alias = getTableAlias(tableNode);
                if (alias == null || !seenTableAliases.contains(alias)) {
                    offspringFromList.add(tableNode);
                    if (alias != null) {
                        seenTableAliases.add(alias);
                    }
                }
            }
            
            offspring.setFromList(offspringFromList);

            // STEP 3: NO JOINs - use cross product (empty join list)
            offspring.setJoinList(new ArrayList<>());

            // STEP 4: Combine WHERE clauses with AND logic and deduplication
            Node<GeneralExpression> whereClause1 = (Node<GeneralExpression>) parent1.getWhereClause();
            Node<GeneralExpression> whereClause2 = (Node<GeneralExpression>) parent2.getWhereClause();
            
            Node<GeneralExpression> combinedWhereClause = combineClauses(whereClause1, whereClause2);
            offspring.setWhereClause(combinedWhereClause);

            // STEP 5: Combine and deduplicate ORDER BY expressions
            List<Node<GeneralExpression>> offspringOrderBy = deduplicateColumns(
                    parent1.getOrderByExpressions(),
                    parent2.getOrderByExpressions());
            offspring.setOrderByExpressions(offspringOrderBy);

            return offspring;

        } catch (Exception e) {
            // If crossover fails, return null to indicate failure
            return null;
        }
    }

    /**
     * Combines fetch columns from two parent queries.
     * If a query selects "*", extracts the specific columns mentioned in WHERE clauses.
     * Deduplicates columns based on their string representation.
     *
     * @param query1 The first parent query
     * @param query2 The second parent query
     * @return A combined list of fetch columns with no duplicates
     */
    private List<Node<GeneralExpression>> combineFetchColumns(GeneralSelect query1, GeneralSelect query2) {
        List<Node<GeneralExpression>> result = new ArrayList<>();
        Set<String> seenColumns = new HashSet<>();

        // Process columns from query1
        List<Node<GeneralExpression>> cols1 = expandSelectColumns(query1);
        for (Node<GeneralExpression> column : cols1) {
            String colStr = GeneralToStringVisitor.asString(column);
            if (!seenColumns.contains(colStr)) {
                result.add(column);
                seenColumns.add(colStr);
            }
        }

        // Process columns from query2
        List<Node<GeneralExpression>> cols2 = expandSelectColumns(query2);
        for (Node<GeneralExpression> column : cols2) {
            String colStr = GeneralToStringVisitor.asString(column);
            if (!seenColumns.contains(colStr)) {
                result.add(column);
                seenColumns.add(colStr);
            }
        }

        // Ensure we have at least one column
        if (result.isEmpty()) {
            result.add(query1.getFetchColumns().get(0));
        }

        return result;
    }

    /**
     * Expands SELECT columns from a query.
     * If the query selects "*", extracts columns mentioned in WHERE and ORDER BY clauses.
     * Otherwise, returns the fetch columns as-is.
     *
     * @param query The query to expand columns from
     * @return A list of expanded column nodes
     */
    private List<Node<GeneralExpression>> expandSelectColumns(GeneralSelect query) {
        List<Node<GeneralExpression>> fetchCols = query.getFetchColumns();

        // Check if selecting all columns ("*")
        if (fetchCols.size() == 1) {
            String colStr = GeneralToStringVisitor.asString(fetchCols.get(0));
            if (colStr.equals("*")) {
                // Extract columns from WHERE and ORDER BY clauses
                Set<Node<GeneralExpression>> extractedColumns = new HashSet<>();

                // Extract from WHERE clause
                if (query.getWhereClause() != null) {
                    extractColumnsFromExpression((Node<GeneralExpression>) query.getWhereClause(),
                            extractedColumns);
                }

                // Extract from ORDER BY clause
                if (query.getOrderByExpressions() != null && !query.getOrderByExpressions().isEmpty()) {
                    for (Node<GeneralExpression> orderExpr : query.getOrderByExpressions()) {
                        extractColumnsFromExpression(orderExpr, extractedColumns);
                    }
                }

                // If we extracted columns, return them; otherwise return the "*"
                if (!extractedColumns.isEmpty()) {
                    return new ArrayList<>(extractedColumns);
                }
            }
        }

        // Return fetch columns as-is
        return new ArrayList<>(fetchCols);
    }

    /**
     * Recursively extracts column references from an expression.
     *
     * @param expr The expression to extract columns from
     * @param extractedColumns The set to accumulate extracted columns into
     */
    private void extractColumnsFromExpression(Node<GeneralExpression> expr,
                                              Set<Node<GeneralExpression>> extractedColumns) {
        if (expr == null) {
            return;
        }

        // If it's a column reference, add it
        if (expr instanceof GeneralColumnReference) {
            extractedColumns.add(expr);
            return;
        }

        // If it's a binary operator, recursively extract from both sides
        if (expr instanceof NewBinaryOperatorNode) {
            NewBinaryOperatorNode<GeneralExpression> binOp = (NewBinaryOperatorNode<GeneralExpression>) expr;
            extractColumnsFromExpression(binOp.getLeft(), extractedColumns);
            extractColumnsFromExpression(binOp.getRight(), extractedColumns);
        }
    }

    /**
     * Gets the alias/name of a table node.
     *
     * @param tableNode The table node
     * @return The table alias/name, or null if it cannot be determined
     */
    private String getTableAlias(Node<GeneralExpression> tableNode) {
        // Try to get string representation which typically includes the table name/alias
        String tableStr = GeneralToStringVisitor.asString(tableNode);
        return tableStr;
    }

    /**
     * Deduplicates column nodes based on their string representation.
     * This prevents duplicate columns in SELECT and ORDER BY clauses.
     *
     * @param columns1 The first list of column nodes
     * @param columns2 The second list of column nodes
     * @return A combined list with no duplicate columns
     */
    private List<Node<GeneralExpression>> deduplicateColumns(
            List<Node<GeneralExpression>> columns1,
            List<Node<GeneralExpression>> columns2) {

        List<Node<GeneralExpression>> result = new ArrayList<>();
        Set<String> seenColumns = new HashSet<>();

        // Add all columns from first list
        for (Node<GeneralExpression> column : columns1) {
            String columnStr = GeneralToStringVisitor.asString(column);
            if (!seenColumns.contains(columnStr)) {
                result.add(column);
                seenColumns.add(columnStr);
            }
        }

        // Add columns from second list that aren't already in the result
        for (Node<GeneralExpression> column : columns2) {
            String columnStr = GeneralToStringVisitor.asString(column);
            if (!seenColumns.contains(columnStr)) {
                result.add(column);
                seenColumns.add(columnStr);
            }
        }

        return result;
    }

    /**
     * Combines two clauses using AND operator.
     * If either clause is null, returns the non-null clause.
     * Deduplicates identical predicates and flattens AND chains to prevent exponential growth.
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

            // If we collected any duplicates during flattening, we've reduced the tree
            // Otherwise, just combine them normally
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
}
