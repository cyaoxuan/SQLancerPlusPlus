package sqlancer.common.genesisql.crossover;

import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.ast.GeneralSelect;

/**
 * Abstract base class for crossover operations in the genetic algorithm.
 * Crossovers combine two parent queries to produce a new offspring query.
 * 
 * This follows the same extensible design pattern as Transformation.java,
 * allowing multiple crossover strategies to be implemented and plugged in.
 */
public abstract class Crossover {

    protected String description;

    /**
     * Constructor for a crossover strategy.
     * 
     * @param description A human-readable description of this crossover strategy
     */
    public Crossover(String description) {
        this.description = description;
    }

    /**
     * Perform crossover between two parent queries to produce an offspring query.
     * 
     * @param parent1Select The first parent GeneralSelect query
     * @param parent2Select The second parent GeneralSelect query
     * @param globalState The global state for query generation context
     * @return A new GeneralSelect representing the offspring from crossover, or null if crossover fails
     * @throws Exception If an error occurs during crossover
     */
    public abstract GeneralSelect crossover(GeneralSelect parent1Select, GeneralSelect parent2Select, 
                                             GeneralGlobalState globalState) throws Exception;

    /**
     * Get the description of this crossover strategy.
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