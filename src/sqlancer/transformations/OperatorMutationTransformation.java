package sqlancer.transformations;

import sqlancer.common.ast.newast.NewBinaryOperatorNode;
import sqlancer.common.ast.newast.Node;
import sqlancer.general.ast.GeneralBinaryComparisonOperator;
import sqlancer.general.ast.GeneralExpression;
import sqlancer.general.ast.GeneralSelect;
import sqlancer.Randomly;

/**
 * Transformation that mutates comparison operators in the WHERE clause of a SELECT statement.
 * For example, changes "=" to "<", ">" to "<=", etc.
 * 
 * This transformation operates directly on the AST without using JSQLParser,
 * using a visitor pattern to traverse and mutate the expression tree.
 */
public class OperatorMutationTransformation extends Transformation {

    private GeneralSelect selectStatement;
    private boolean mutationApplied;

    public OperatorMutationTransformation() {
        super("operator mutation in WHERE clause");
    }

    @Override
    public boolean init(String sql) {
        // This transformation works with GeneralSelect objects, not SQL strings
        // The actual initialization is done via setSelectStatement()
        this.current = sql;
        return true;
    }

    /**
     * Set the GeneralSelect statement to be transformed.
     * This must be called instead of init() for this transformation.
     * 
     * @param select The GeneralSelect statement to transform
     */
    public void setSelectStatement(GeneralSelect select) {
        this.selectStatement = select;
        this.mutationApplied = false;
    }

    /**
     * Apply operator mutation to the WHERE clause of the select statement.
     * This method uses a visitor to traverse the WHERE clause expression tree
     * and mutates comparison operators.
     */
    @Override
    public void apply() {
        if (selectStatement == null || selectStatement.getWhereClause() == null) {
            isChanged = false;
            return;
        }

        // Attempt to mutate the WHERE clause
        Node<GeneralExpression> originalWhereClause = 
            (Node<GeneralExpression>) selectStatement.getWhereClause();
        
        OperatorMutationVisitor visitor = new OperatorMutationVisitor();
        Node<GeneralExpression> mutatedWhereClause = visitor.visit(originalWhereClause);

        // Check if mutation was applied
        if (visitor.isMutationApplied()) {
            selectStatement.setWhereClause(mutatedWhereClause);
            isChanged = true;
            mutationApplied = true;
            onStatementChanged();
        } else {
            isChanged = false;
        }
    }

    @Override
    protected void onStatementChanged() {
        if (statementChangedHandler != null && selectStatement != null) {
            // Convert the mutated GeneralSelect back to string representation
            // This would be handled by GeneralToStringVisitor in the actual usage
            statementChangedHandler.accept("WHERE clause mutated");
        }
    }

    /**
     * Check if a mutation was applied during the last apply() call.
     * 
     * @return true if a mutation was applied, false otherwise
     */
    public boolean isMutationApplied() {
        return mutationApplied;
    }

    /**
     * Internal visitor class that traverses the WHERE clause expression tree
     * and mutates comparison operators using the visitor pattern.
     */
    private static class OperatorMutationVisitor {
        private boolean mutationApplied = false;

        /**
         * Visit a node and apply operator mutation if applicable.
         * Recursively traverses the tree and mutates the first comparison operator found.
         * 
         * @param node The node to visit
         * @return A new node with potentially mutated operator, or the original node
         */
        public Node<GeneralExpression> visit(Node<GeneralExpression> node) {
            if (node == null) {
                return null;
            }

            if (node instanceof NewBinaryOperatorNode) {
                NewBinaryOperatorNode<GeneralExpression> binaryNode = 
                    (NewBinaryOperatorNode<GeneralExpression>) node;

                // Check if this is a comparison operator and we haven't mutated yet
                if (!mutationApplied && binaryNode.getOp() instanceof GeneralBinaryComparisonOperator) {
                    // Only mutate with a certain probability to avoid over-mutation
                    if (Randomly.getBoolean()) {
                        return mutateComparisonOperator(binaryNode);
                    }
                }

                // Recursively visit left and right children
                Node<GeneralExpression> newLeft = visit(binaryNode.getLeft());
                Node<GeneralExpression> newRight = visit(binaryNode.getRight());

                // If children changed, create a new node
                if (newLeft != binaryNode.getLeft() || newRight != binaryNode.getRight()) {
                    return new NewBinaryOperatorNode<>(newLeft, newRight, binaryNode.getOp());
                }
            }

            return node;
        }

        /**
         * Mutate a comparison operator to a different one.
         * 
         * @param binaryNode The binary node containing the comparison operator
         * @return A new node with a mutated operator
         */
        private Node<GeneralExpression> mutateComparisonOperator(
                NewBinaryOperatorNode<GeneralExpression> binaryNode) {

            GeneralBinaryComparisonOperator currentOp = 
                (GeneralBinaryComparisonOperator) binaryNode.getOp();

            // Get a different comparison operator
            GeneralBinaryComparisonOperator newOp = getRandomDifferentOperator(currentOp);

            mutationApplied = true;

            // Return a new node with the mutated operator
            return new NewBinaryOperatorNode<>(
                binaryNode.getLeft(),
                binaryNode.getRight(),
                newOp
            );
        }

        /**
         * Get a random comparison operator that is different from the current one.
         * 
         * @param currentOp The current operator
         * @return A different random comparison operator
         */
        private GeneralBinaryComparisonOperator getRandomDifferentOperator(
                GeneralBinaryComparisonOperator currentOp) {

            GeneralBinaryComparisonOperator[] operators = GeneralBinaryComparisonOperator.values();

            // Filter out the current operator and select a random one from the rest
            GeneralBinaryComparisonOperator newOp;
            do {
                newOp = Randomly.fromOptions(operators);
            } while (newOp == currentOp);

            return newOp;
        }

        /**
         * Check if a mutation was applied during the last visit.
         * 
         * @return true if a mutation was applied, false otherwise
         */
        public boolean isMutationApplied() {
            return mutationApplied;
        }
    }
}