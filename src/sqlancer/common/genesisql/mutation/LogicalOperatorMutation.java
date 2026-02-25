package sqlancer.common.genesisql.mutation;

import sqlancer.Randomly;
import sqlancer.common.ast.newast.NewBinaryOperatorNode;
import sqlancer.common.ast.newast.Node;
import sqlancer.general.ast.GeneralBinaryLogicalOperator;
import sqlancer.general.ast.GeneralExpression;
import sqlancer.general.ast.GeneralSelect;

/**
 * Mutation that replaces logical operators (AND/OR) in the WHERE clause.
 * For example, changes "WHERE x = 5 AND y < 10" to "WHERE x = 5 OR y < 10".
 */
public class LogicalOperatorMutation extends Mutation {

    public LogicalOperatorMutation() {
        super("Logical Operator Mutation - mutate AND/OR operators in WHERE clause");
    }

    @Override
    public boolean mutate(GeneralSelect select) throws Exception {
        if (select == null || select.getWhereClause() == null) {
            return false;
        }

        Node<GeneralExpression> originalWhereClause = 
            (Node<GeneralExpression>) select.getWhereClause();
        
        LogicalOperatorMutationVisitor visitor = new LogicalOperatorMutationVisitor();
        Node<GeneralExpression> mutatedWhereClause = visitor.visit(originalWhereClause);

        if (visitor.isMutationApplied()) {
            select.setWhereClause(mutatedWhereClause);
            return true;
        }
        
        return false;
    }

    private static class LogicalOperatorMutationVisitor {
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
                        return mutateLogicalOperator(binaryNode);
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

        private Node<GeneralExpression> mutateLogicalOperator(
                NewBinaryOperatorNode<GeneralExpression> binaryNode) {

            GeneralBinaryLogicalOperator currentOp = 
                (GeneralBinaryLogicalOperator) binaryNode.getOp();

            // Get a different logical operator
            GeneralBinaryLogicalOperator newOp = getRandomDifferentOperator(currentOp);

            mutationApplied = true;

            // Return a new node with the mutated operator
            return new NewBinaryOperatorNode<>(
                binaryNode.getLeft(),
                binaryNode.getRight(),
                newOp
            );
        }

        private GeneralBinaryLogicalOperator getRandomDifferentOperator(
                GeneralBinaryLogicalOperator currentOp) {

            GeneralBinaryLogicalOperator[] operators = GeneralBinaryLogicalOperator.values();

            // Select a different operator
            GeneralBinaryLogicalOperator newOp;
            do {
                newOp = Randomly.fromOptions(operators);
            } while (newOp == currentOp);

            return newOp;
        }

        public boolean isMutationApplied() {
            return mutationApplied;
        }
    }
}