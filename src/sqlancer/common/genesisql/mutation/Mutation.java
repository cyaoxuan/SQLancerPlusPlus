package sqlancer.common.genesisql.mutation;

import sqlancer.general.ast.GeneralSelect;

/**
 * Abstract base class for mutation operations in the genetic algorithm.
 * Mutations modify a single query to produce variations.
 * 
 * This follows the same extensible design pattern as Crossover.java,
 * allowing multiple mutation strategies to be implemented and plugged in.
 */
public abstract class Mutation {

    protected String description;

    /**
     * Constructor for a mutation strategy.
     * 
     * @param description A human-readable description of this mutation strategy
     */
    public Mutation(String description) {
        this.description = description;
    }

    /**
     * Perform in-place mutation of a query.
     * 
     * @param select The GeneralSelect query to mutate in place
     * @return true if a mutation was applied, false otherwise
     * @throws Exception If an error occurs during mutation
     */
    public abstract boolean mutate(GeneralSelect select) throws Exception;

    /**
     * Get the description of this mutation strategy.
     * 
     * @return The description string
     */
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }
}
