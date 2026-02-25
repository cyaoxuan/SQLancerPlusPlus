package sqlancer.common.genesisql.mutation;

import sqlancer.Randomly;
import sqlancer.common.ast.newast.NewBinaryOperatorNode;
import sqlancer.common.ast.newast.Node;
import sqlancer.general.ast.GeneralCast;
import sqlancer.general.ast.GeneralExpression;
import sqlancer.general.ast.GeneralSelect;
import sqlancer.general.GeneralSchema.GeneralCompositeDataType;

/**
 * Mutation that adds a CAST operator to an existing expression.
 * For example, changes "WHERE x = 5" to "WHERE CAST(x AS VARCHAR) = 5", or "WHERE y < 10" to "WHERE CAST(y AS FLOAT) < 10".
 */
public class AddCastMutation extends Mutation {

    public AddCastMutation() {
        super("Add Cast Mutation - wrap expressions with CAST operators");
    }

    @Override
    public boolean mutate(GeneralSelect select) throws Exception {
        if (select == null || select.getWhereClause() == null) {
            return false;
        }

        Node<GeneralExpression> originalWhereClause = 
            (Node<GeneralExpression>) select.getWhereClause();
        
        AddCastMutationVisitor visitor = new AddCastMutationVisitor();
        Node<GeneralExpression> mutatedWhereClause = visitor.visit(originalWhereClause);

        if (visitor.isMutationApplied()) {
            select.setWhereClause(mutatedWhereClause);
            return true;
        }
        
        return false;
    }

    private static class AddCastMutationVisitor {
        private boolean mutationApplied = false;

        public Node<GeneralExpression> visit(Node<GeneralExpression> node) {
            if (node == null) {
                return null;
            }

            // Try to wrap the current node with a CAST if we haven't mutated yet
            if (!mutationApplied && Randomly.getBoolean()) {
                Node<GeneralExpression> wrapped = wrapWithCast(node);
                if (wrapped != null) {
                    mutationApplied = true;
                    return wrapped;
                }
            }

            if (node instanceof NewBinaryOperatorNode) {
                NewBinaryOperatorNode<GeneralExpression> binaryNode = 
                    (NewBinaryOperatorNode<GeneralExpression>) node;

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

        private Node<GeneralExpression> wrapWithCast(Node<GeneralExpression> node) {
            try {
                // Get a random data type to cast to
                GeneralCompositeDataType targetType = GeneralCompositeDataType.getRandomWithoutNull();
                
                // Get a random cast operator (FUNC or COLON)
                GeneralCast.GeneralCastOperator castOp = GeneralCast.GeneralCastOperator.getRandom();
                
                // Create a cast node wrapping the expression
                GeneralCast castNode = new GeneralCast((Node<GeneralExpression>) node, targetType, castOp);
                return castNode;
            } catch (Exception e) {
                return null;
            }
        }

        public boolean isMutationApplied() {
            return mutationApplied;
        }
    }
}