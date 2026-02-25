package sqlancer.common.genesisql.mutation;

import sqlancer.Randomly;
import sqlancer.common.ast.newast.NewBinaryOperatorNode;
import sqlancer.common.ast.newast.Node;
import sqlancer.general.ast.GeneralConstant;
import sqlancer.general.ast.GeneralExpression;
import sqlancer.general.ast.GeneralSelect;

/**
 * Mutation that modifies literal constants in predicates.
 * For example, changes "WHERE x = 5" to "WHERE x = 10", or "WHERE y < 'abc'" to "WHERE y < 'def'".
 */
public class ValueMutation extends Mutation {

    public ValueMutation() {
        super("Value Mutation - mutate literal constants in WHERE clause");
    }

    @Override
    public boolean mutate(GeneralSelect select) throws Exception {
        if (select == null || select.getWhereClause() == null) {
            return false;
        }

        Node<GeneralExpression> originalWhereClause = 
            (Node<GeneralExpression>) select.getWhereClause();
        
        ValueMutationVisitor visitor = new ValueMutationVisitor();
        Node<GeneralExpression> mutatedWhereClause = visitor.visit(originalWhereClause);

        if (visitor.isMutationApplied()) {
            select.setWhereClause(mutatedWhereClause);
            return true;
        }
        
        return false;
    }

    private static class ValueMutationVisitor {
        private boolean mutationApplied = false;
        private Randomly r = new Randomly();

        public Node<GeneralExpression> visit(Node<GeneralExpression> node) {
            if (node == null) {
                return null;
            }

            if (node instanceof GeneralConstant.GeneralIntConstant) {
                GeneralConstant.GeneralIntConstant intConstant = (GeneralConstant.GeneralIntConstant) node;
                if (!mutationApplied && Randomly.getBoolean()) {
                    return mutateIntConstant(intConstant);
                }
            }

            if (node instanceof GeneralConstant.GeneralDoubleConstant) {
                GeneralConstant.GeneralDoubleConstant doubleConstant = (GeneralConstant.GeneralDoubleConstant) node;
                if (!mutationApplied && Randomly.getBoolean()) {
                    return mutateDoubleConstant(doubleConstant);
                }
            }

            if (node instanceof GeneralConstant.GeneralTextConstant) {
                GeneralConstant.GeneralTextConstant textConstant = (GeneralConstant.GeneralTextConstant) node;
                if (!mutationApplied && Randomly.getBoolean()) {
                    return mutateTextConstant(textConstant);
                }
            }

            if (node instanceof NewBinaryOperatorNode) {
                NewBinaryOperatorNode<GeneralExpression> binaryNode = 
                    (NewBinaryOperatorNode<GeneralExpression>) node;

                Node<GeneralExpression> newLeft = visit(binaryNode.getLeft());
                Node<GeneralExpression> newRight = visit(binaryNode.getRight());

                if (newLeft != binaryNode.getLeft() || newRight != binaryNode.getRight()) {
                    return new NewBinaryOperatorNode<>(newLeft, newRight, binaryNode.getOp());
                }
            }

            return node;
        }

        private Node<GeneralExpression> mutateIntConstant(GeneralConstant.GeneralIntConstant constant) {
            mutationApplied = true;
            long value = constant.getValue();
            // Add or subtract a random value to the constant
            long newValue = value + r.getInteger(-100, 100);
            return new GeneralConstant.GeneralIntConstant(newValue);
        }

        private Node<GeneralExpression> mutateDoubleConstant(GeneralConstant.GeneralDoubleConstant constant) {
            mutationApplied = true;
            double value = constant.getValue();
            // Add or subtract a random value to the constant
            double newValue = value + r.getInteger(-100, 100) / 10.0;
            return new GeneralConstant.GeneralDoubleConstant(newValue);
        }

        private Node<GeneralExpression> mutateTextConstant(GeneralConstant.GeneralTextConstant constant) {
            mutationApplied = true;
            String value = constant.getValue();
            // Mutate the string by changing a character or appending one
            if (value.length() > 0 && Randomly.getBoolean()) {
                // Change a random character
                int index = r.getInteger(0, value.length());
                char randomChar = (char) r.getInteger(97, 122);
                String newValue = value.substring(0, index) + randomChar + value.substring(index + 1);
                return new GeneralConstant.GeneralTextConstant(newValue);
            } else {
                // Append a random character
                char randomChar = (char) r.getInteger(97, 122);
                return new GeneralConstant.GeneralTextConstant(value + randomChar);
            }
        }

        public boolean isMutationApplied() {
            return mutationApplied;
        }
    }
}