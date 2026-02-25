package sqlancer.common.genesisql.mutation;

import sqlancer.Randomly;
import sqlancer.common.ast.newast.NewBinaryOperatorNode;
import sqlancer.common.ast.newast.Node;
import sqlancer.general.ast.GeneralBinaryComparisonOperator;
import sqlancer.general.ast.GeneralExpression;
import sqlancer.general.ast.GeneralSelect;

/**
 * Mutation that mutates comparison operators in the WHERE clause of a SELECT statement.
 * For example, changes "=" to "<", ">" to "<=", etc.
 * 
 * This mutation operates directly on the AST without using JSQLParser,
 * using a visitor pattern to traverse and mutate the expression tree.
 */
public class BinaryOperatorMutation extends Mutation {

    public BinaryOperatorMutation() {
        super("Operator Mutation - mutate comparison operators in WHERE clause");
    }

    /**
     * Mutate comparison operators in the WHERE clause of the given select statement.
     * Modifies the select statement in place.
     * 
     * @param select The GeneralSelect statement to mutate in place
     * @return true if a mutation was applied, false otherwise
     * @throws Exception If an error occurs during mutation
     */
    @Override
    public boolean mutate(GeneralSelect select) throws Exception {
        if (select == null || select.getWhereClause() == null) {
            return false;
        }

        // Attempt to mutate the WHERE clause
        Node<GeneralExpression> originalWhereClause = 
            (Node<GeneralExpression>) select.getWhereClause();
        
        OperatorMutationVisitor visitor = new OperatorMutationVisitor();
        Node<GeneralExpression> mutatedWhereClause = visitor.visit(originalWhereClause);

        // Check if mutation was applied
        if (visitor.isMutationApplied()) {
            select.setWhereClause(mutatedWhereClause);
            return true;
        }
        
        return false;
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
