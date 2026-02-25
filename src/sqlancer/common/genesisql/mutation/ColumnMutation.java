package sqlancer.common.genesisql.mutation;

import sqlancer.Randomly;
import sqlancer.common.ast.newast.NewBinaryOperatorNode;
import sqlancer.common.ast.newast.Node;
import sqlancer.general.ast.GeneralColumnReference;
import sqlancer.general.ast.GeneralExpression;
import sqlancer.general.ast.GeneralSelect;

/**
 * Mutation that replaces column references with other valid columns.
 * For example, changes "WHERE x = 5" to "WHERE y = 5", where x and y are columns of the same or compatible type.
 */
public class ColumnMutation extends Mutation {

    public ColumnMutation() {
        super("Column Mutation - replace column references in WHERE clause");
    }

    @Override
    public boolean mutate(GeneralSelect select) throws Exception {
        if (select == null || select.getWhereClause() == null) {
            return false;
        }

        Node<GeneralExpression> originalWhereClause = 
            (Node<GeneralExpression>) select.getWhereClause();
        
        ColumnMutationVisitor visitor = new ColumnMutationVisitor();
        Node<GeneralExpression> mutatedWhereClause = visitor.visit(originalWhereClause);

        if (visitor.isMutationApplied()) {
            select.setWhereClause(mutatedWhereClause);
            return true;
        }
        
        return false;
    }

    private static class ColumnMutationVisitor {
        private boolean mutationApplied = false;

        public Node<GeneralExpression> visit(Node<GeneralExpression> node) {
            if (node == null) {
                return null;
            }

            if (node instanceof GeneralColumnReference) {
                GeneralColumnReference colRef = (GeneralColumnReference) node;
                if (!mutationApplied && Randomly.getBoolean()) {
                    // Try to get a different column from the same table
                    GeneralColumnReference newColRef = getRandomDifferentColumn(colRef);
                    if (newColRef != null) {
                        mutationApplied = true;
                        return newColRef;
                    }
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

        private GeneralColumnReference getRandomDifferentColumn(GeneralColumnReference currentCol) {
            // Get all columns from the same table
            try {
                var table = currentCol.getColumn().getTable();
                var columns = table.getColumns();
                
                if (columns.size() <= 1) {
                    return null; // No other columns to choose from
                }
                
                // Select a random different column from the same table
                GeneralColumnReference newColRef;
                do {
                    var randomColumn = Randomly.fromList(columns);
                    newColRef = new GeneralColumnReference(randomColumn);
                } while (newColRef.getColumn().equals(currentCol.getColumn()));
                
                return newColRef;
            } catch (Exception e) {
                return null;
            }
        }

        public boolean isMutationApplied() {
            return mutationApplied;
        }
    }
}