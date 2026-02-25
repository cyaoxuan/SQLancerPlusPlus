package sqlancer.common.genesisql.mutation;

import sqlancer.common.ast.newast.NewUnaryPrefixOperatorNode;
import sqlancer.common.ast.newast.Node;
import sqlancer.general.ast.GeneralExpression;
import sqlancer.general.ast.GeneralSelect;
import sqlancer.general.ast.GeneralUnaryPrefixOperator;

/**
 * Mutation that adds a NOT operator to an existing predicate.
 * For example, changes "WHERE x = 5" to "WHERE NOT (x = 5)", or "WHERE y < 10" to "WHERE NOT (y < 10)".
 */
public class AddNotMutation extends Mutation {

    public AddNotMutation() {
        super("Add NOT Mutation - wrap predicates with NOT operator");
    }

    @Override
    public boolean mutate(GeneralSelect select) throws Exception {
        if (select == null || select.getWhereClause() == null) {
            return false;
        }

        Node<GeneralExpression> whereClause = (Node<GeneralExpression>) select.getWhereClause();
        
        // Wrap the entire WHERE clause with NOT
        Node<GeneralExpression> notWrapped = new NewUnaryPrefixOperatorNode<>(
            whereClause,
            GeneralUnaryPrefixOperator.NOT
        );
        select.setWhereClause(notWrapped);
        return true;
    }
}