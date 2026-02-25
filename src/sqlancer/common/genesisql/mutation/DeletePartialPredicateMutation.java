package sqlancer.common.genesisql.mutation;

import sqlancer.Randomly;
import sqlancer.common.ast.newast.NewBinaryOperatorNode;
import sqlancer.common.ast.newast.Node;
import sqlancer.general.ast.GeneralBinaryLogicalOperator;
import sqlancer.general.ast.GeneralExpression;
import sqlancer.general.ast.GeneralSelect;

/**
 * Mutation that deletes parts of a predicate by removing a condition from a WHERE clause.
 * For example, changes "WHERE x = 5 AND y < 10" to "WHERE x = 5", or "WHERE a > 0 OR b < 100" to "WHERE a > 0".
 * This simplifies the predicate by keeping one side of a logical operator and removing the other.
 */
public class DeletePartialPredicateMutation extends Mutation {

    public DeletePartialPredicateMutation() {
        super("Delete Partial Predicate Mutation - remove parts of WHERE clause");
    }

    @Override
    public boolean mutate(GeneralSelect select) throws Exception {
        if (select == null || select.getWhereClause() == null) {
            return false;
        }

        Node<GeneralExpression> originalWhereClause = 
            (Node<GeneralExpression>) select.getWhereClause();
        
        DeletePartialPredicateVisitor visitor = new DeletePartialPredicateVisitor();
        Node<GeneralExpression> mutatedWhereClause = visitor.visit(originalWhereClause);

        if (visitor.isMutationApplied()) {
            select.setWhereClause(mutatedWhereClause);
            return true;
        }
        
        return false;
    }

    private static class DeletePartialPredicateVisitor {
        private boolean mutationApplied = false;

        public Node<GeneralExpression> visit(Node<GeneralExpression> node) {
            if (node == null) {
                return null;
            }

            if (node instanceof NewBinaryOperatorNode) {
                NewBinaryOperatorNode<GeneralExpression> binaryNode = 
                    (NewBinaryOperatorNode<GeneralExpression>) node;

                // Check if this is a logical operator and we haven't mutated yet
                if (!mutationApplied && binaryNode.getOp() instanceof GeneralBinaryLogicalOperator) {
                    // Only mutate with a certain probability
                    if (Randomly.getBoolean()) {
                        // Randomly choose to keep either the left or right side
                        if (Randomly.getBoolean()) {
                            mutationApplied = true;
                            return binaryNode.getLeft();
                        } else {
                            mutationApplied = true;
                            return binaryNode.getRight();
                        }
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

        public boolean isMutationApplied() {
            return mutationApplied;
        }
    }
}