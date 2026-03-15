package sqlancer.general.oracle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.Reproducer;
import sqlancer.common.ast.newast.Node;
import sqlancer.common.ast.newast.TableReferenceNode;
import sqlancer.common.genesisql.QueryPool;
import sqlancer.common.genesisql.QueryPoolEntry;
import sqlancer.common.genesisql.crossover.Crossover;
import sqlancer.common.genesisql.crossover.SimpleCrossJoinCrossover;
import sqlancer.common.genesisql.crossover.SwapWhereCrossover;
import sqlancer.common.genesisql.mutation.AddCastMutation;
import sqlancer.common.genesisql.mutation.AddNotMutation;
import sqlancer.common.genesisql.mutation.BinaryOperatorMutation;
import sqlancer.common.genesisql.mutation.ColumnMutation;
import sqlancer.common.genesisql.mutation.DeletePartialPredicateMutation;
import sqlancer.common.genesisql.mutation.LogicalOperatorMutation;
import sqlancer.common.genesisql.mutation.Mutation;
import sqlancer.common.genesisql.mutation.ValueMutation;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.general.GeneralErrorHandler.GeneratorNode;
import sqlancer.general.GeneralErrors;
import sqlancer.general.GeneralProvider.GeneralGlobalState;
import sqlancer.general.GeneralSchema.GeneralTable;
import sqlancer.general.GeneralToStringVisitor;
import sqlancer.general.ast.GeneralExpression;
import sqlancer.general.ast.GeneralJoin;
import sqlancer.general.ast.GeneralSelect;
import sqlancer.general.gen.GeneralRandomQuerySynthesizer;

public class GeneralQueryPartitioningWhere extends GeneralQueryPartitioningBase {
    private Reproducer<GeneralGlobalState> reproducer;

    public GeneralQueryPartitioningWhere(GeneralGlobalState state) {
        super(state);
        GeneralErrors.addExpressionErrors(errors);
    }

    private class GeneralQueryPartitioningWhereReproducer implements Reproducer<GeneralGlobalState> {
        final String firstQueryString;
        final String secondQueryString;
        final String thirdQueryString;
        final String originalQueryString;
        final boolean orderBy;
        private String errorMessage;

        GeneralQueryPartitioningWhereReproducer(String firstQueryString, String secondQueryString,
                String thirdQueryString, String originalQueryString, boolean orderBy, String errorMessage) {
            this.firstQueryString = firstQueryString;
            this.secondQueryString = secondQueryString;
            this.thirdQueryString = thirdQueryString;
            this.originalQueryString = originalQueryString;
            this.orderBy = orderBy;
            this.errorMessage = errorMessage;
        }

        @Override
        public String getErrorMessage() {
            return errorMessage;
        }

        @Override
        public boolean bugStillTriggers(GeneralGlobalState globalState) {
            try {
                List<String> resultSet = ComparatorHelper.getResultSetFirstColumnAsString(originalQueryString, errors,
                        globalState);
                List<String> combinedString1 = new ArrayList<>();
                List<String> secondResultSet1 = ComparatorHelper.getCombinedResultSet(firstQueryString,
                        secondQueryString, thirdQueryString, combinedString1, !orderBy, globalState, errors);
                ComparatorHelper.assumeResultSetsAreEqual(resultSet, secondResultSet1, originalQueryString,
                        combinedString1, globalState, ComparatorHelper::canonicalizeResultValue);
            } catch (AssertionError triggeredError) {
                this.errorMessage = triggeredError.getMessage();
                return true;
            } catch (SQLException ignored) {
            }
            return false;
        }
    }

    @Override
    public void check() throws SQLException {
        reproducer = null;
        super.check();
        select.setWhereClause(null);
        String originalQueryString = GeneralToStringVisitor.asString(select);
        List<String> resultSet;
        try {
            resultSet = ComparatorHelper.getResultSetFirstColumnAsString(originalQueryString, errors, state);
        } catch (Exception e) {
            if (select.getJoinList().size() == 0 && select.getFromList().size() <= 2) {
                e.printStackTrace();
                throw new AssertionError(e.getMessage()
                        + "\n You probably triggered an error in the DBMS by the previous query, as the query is a simple select that could not easily have issue. Check the *-cur.log");
            }
            // Noticed that, we would still add some extra information to the generator table. Since the UNION ALL query
            // would not be actually executed but fail due to the previous JOIN query.
            // I think it is fine. We could do dependency analysis later.
            state.getHandler().appendScoreToTable(false, true);
            throw e;
        }

        boolean orderBy = Randomly.getBooleanWithRatherLowProbability();
        if (orderBy) {
            select.setOrderByExpressions(gen.generateOrderBys());
        }
        select.setWhereClause(predicate);
        String firstQueryString = GeneralToStringVisitor.asString(select);
        select.setWhereClause(negatedPredicate);
        String secondQueryString = GeneralToStringVisitor.asString(select);
        select.setWhereClause(isNullPredicate);
        String thirdQueryString = GeneralToStringVisitor.asString(select);
        List<String> combinedString = new ArrayList<>();

        List<String> secondResultSet;
        try {
            secondResultSet = ComparatorHelper.getCombinedResultSet(firstQueryString, secondQueryString,
                    thirdQueryString, combinedString, !orderBy, state, errors);
        } catch (Exception e) {
            state.getHandler().appendScoreToTable(false, true);
            throw e;
        }
        try {
            ComparatorHelper.assumeResultSetsAreEqual(resultSet, secondResultSet, originalQueryString, combinedString,
                    state, ComparatorHelper::canonicalizeResultValue);
        } catch (AssertionError e) {
            // TODO we need to give some information to the handler here
            // state.getHandler().printStatistics();
            state.getHandler().appendScoreToTable(true, true, firstQueryString);
            reproducer = new GeneralQueryPartitioningWhereReproducer(firstQueryString, secondQueryString,
                    thirdQueryString, originalQueryString, orderBy, e.getMessage());
            throw e;
        }
        state.getHandler().appendScoreToTable(true, true, firstQueryString);
    }

    @Override
    public Reproducer<GeneralGlobalState> getLastReproducer() {
        return reproducer;
    }

    @Override
    public QueryPool initialiseQueryPool(GeneralGlobalState globalState) throws SQLException {
        // Generate initial population of queries and add them to the global state
        // This uses the same logic as the check() method to generate random SELECT statements
    	QueryPool queryPool = globalState.initialiseQueryPool();
        int populationSize = globalState.getOptions().getGenesisqlPopulationSize();
        
        for (int i = 0; i < populationSize; i++) {
        	QueryPoolEntry entry = generateSelectStatement(0);
            
            // Regenerate if this query was already generated in this initialisation
            int maxRetries = 10;
            int retryCount = 0;
            while (queryPool.hasQueryBeenGenerated(GeneralToStringVisitor.asString(entry.getFirstQuery())) && retryCount < maxRetries) {
                entry = generateSelectStatement(0);
                retryCount++;
            }
            
            queryPool.addQueryPoolEntry(entry);
        }
        
        return queryPool;
    }
    
    private QueryPoolEntry generateSelectStatement(int generation) throws SQLException {
    	s = state.getSchema();
        targetTables = s.getRandomTableNonEmptyTables();
        gen = GeneralRandomQuerySynthesizer.getExpressionGenerator(state, targetTables.getColumns());
        // gen = new
        // GeneralExpressionGenerator(state).setColumns(targetTables.getColumns());
        initializeTernaryPredicateVariants();
        
        // Create the base select statement, from the base oracle class
        select = new GeneralSelect();
        select.setFetchColumns(generateFetchColumns());
        List<GeneralTable> tables = targetTables.getTables();
        List<TableReferenceNode<GeneralExpression, GeneralTable>> tableList = tables.stream()
                .map(t -> new TableReferenceNode<GeneralExpression, GeneralTable>(t)).collect(Collectors.toList());
        List<Node<GeneralExpression>> joins;
        if (Randomly.getBoolean() || !state.getHandler().getOption(GeneratorNode.SUBQUERY)) {
            joins = GeneralJoin.getJoins(tableList, state);
        } else {
            joins = GeneralJoin.getJoinsWithSubquery(tableList, state);
        }
        select.setJoinList(joins.stream().collect(Collectors.toList()));
        select.setFromList(tableList.stream().collect(Collectors.toList()));
        boolean orderBy = Randomly.getBooleanWithRatherLowProbability();
        if (orderBy) {
            select.setOrderByExpressions(gen.generateOrderBys());
        }
        select.setWhereClause(predicate);
        
        return new QueryPoolEntry(select, errors, 0, generation);
    }

    @Override
    public void evaluateQueryFitnessAndOracleValidation(QueryPoolEntry entry, GeneralGlobalState globalState) throws SQLException {
    	// Get the query strings and context from the QueryPoolEntry
    	String originalQueryString = GeneralToStringVisitor.asString(entry.getOriginalQuery());
    	String firstQueryString = GeneralToStringVisitor.asString(entry.getFirstQuery());
    	String secondQueryString = GeneralToStringVisitor.asString(entry.getSecondQuery());
    	String thirdQueryString = GeneralToStringVisitor.asString(entry.getThirdQuery());
    	boolean orderBy = entry.hasOrderBy();
    	ExpectedErrors errors = entry.getErrors();
    	
    	// Execute original query to get baseline result set
        List<String> resultSet;
        try {
            resultSet = ComparatorHelper.getResultSetFirstColumnAsString(originalQueryString, errors, globalState);
        } catch (Exception e) {
            globalState.getHandler().appendScoreToTable(false, true);
            throw e;
        }
        
        // Execute first query and measure execution time for fitness calculation
        List<String> firstResultSet;
        long firstQueryExecutionTime;
        try {
            long startTime = System.currentTimeMillis();
            firstResultSet = ComparatorHelper.getResultSetFirstColumnAsString(firstQueryString, errors, globalState);
            firstQueryExecutionTime = System.currentTimeMillis() - startTime;
        } catch (Exception e) {
            globalState.getHandler().appendScoreToTable(false, true);
            throw e;
        }
        
        // Execute the remaining two queries for oracle validation
        List<String> combinedString = new ArrayList<>();
        List<String> secondResultSet;
        try {
            secondResultSet = ComparatorHelper.getCombinedResultSet(firstQueryString, secondQueryString,
                    thirdQueryString, combinedString, !orderBy, globalState, errors);
        } catch (Exception e) {
            globalState.getHandler().appendScoreToTable(false, true);
            throw e;
        }
        
        // Validate using the oracle
        try {
            ComparatorHelper.assumeResultSetsAreEqual(resultSet, secondResultSet, originalQueryString, combinedString,
                    globalState, ComparatorHelper::canonicalizeResultValue);
        } catch (AssertionError e) {
            globalState.getHandler().appendScoreToTable(true, true, firstQueryString);
            reproducer = new GeneralQueryPartitioningWhereReproducer(firstQueryString, secondQueryString,
                    thirdQueryString, originalQueryString, orderBy, e.getMessage());
            throw e;
        }
        globalState.getHandler().appendScoreToTable(true, true, firstQueryString);
        
        // Calculate fitness score based on execution time and partitioning effectiveness
        double fitnessScore = calculateFitnessScore(firstQueryExecutionTime, resultSet.size(), firstResultSet.size());
        entry.setFitnessScore(fitnessScore);
    }
    
    private double calculateFitnessScore(long executionTimeMs, int originalResultSetSize, int firstQueryResultSetSize) {
    	// Vibecoded weights and calculations for now
        // Weight factors (these can be adjusted)
        final double EXECUTION_TIME_WEIGHT = 0.3; // Lower execution time is better
        final double PARTITIONING_WEIGHT = 0.7; // Good partitioning is more important
        final long MAX_ACCEPTABLE_EXECUTION_TIME_MS = 5000; // 5 seconds is considered "slow"
        
        // Calculate execution time score (0-100)
        // Lower execution time = higher score
        double executionTimeScore = 100.0 * Math.exp(-executionTimeMs / (double) MAX_ACCEPTABLE_EXECUTION_TIME_MS);
        executionTimeScore = Math.min(100.0, Math.max(0.0, executionTimeScore));
        
        // Calculate partitioning effectiveness score (0-100)
        // Best partitioning is when we get roughly 30-70% of the original result set
        // Worst partitioning is when we get 0% or 100% (i.e., no effective filtering)
        double partitioningScore = 0.0;
        if (originalResultSetSize == 0) {
            // If original query returns nothing, any first query result is bad
            partitioningScore = 0.0;
        } else {
            double ratio = (double) firstQueryResultSetSize / originalResultSetSize;
            // Score is high when ratio is between 0.3 and 0.7
            // Score is low when ratio is close to 0 or 1
            if (ratio <= 0.0 || ratio >= 1.0) {
                // No partitioning or returns all results - very bad
                partitioningScore = 0.0;
            } else if (ratio >= 0.3 && ratio <= 0.7) {
                // Good partitioning - score based on how close to 0.5 (optimal split)
                double distanceFromOptimal = Math.abs(ratio - 0.5);
                partitioningScore = 100.0 * (1.0 - (distanceFromOptimal / 0.5));
            } else if (ratio < 0.3) {
                // Partial but low ratio - score based on how close to 0.3
                partitioningScore = 100.0 * (ratio / 0.3);
            } else {
                // Partial but high ratio - score based on how close to 0.7
                partitioningScore = 100.0 * ((1.0 - ratio) / 0.3);
            }
        }
        
        // Combine scores with weights
        double totalScore = (executionTimeScore * EXECUTION_TIME_WEIGHT) + 
                           (partitioningScore * PARTITIONING_WEIGHT);
        
        return totalScore;
    }
    
    @Override
	public QueryPoolEntry mutateQuery(QueryPoolEntry entry, GeneralGlobalState globalState, int generation) throws Exception {
		GeneralSelect mutatedFirstQuery = deepCopySelect(entry.getFirstQuery());
		
		List<Mutation> mutations = getMutations();
		boolean anyMutationApplied = false;
		
		// Apply each mutation with a certain probability
		for (Mutation mutation : mutations) {
			// Apply mutation with 50% probability
			if (Randomly.getBoolean()) {
				try {
					boolean mutationApplied = mutation.mutate(mutatedFirstQuery);
					if (mutationApplied) {
						anyMutationApplied = true;
					}
				} catch (Exception e) {
					continue;
				}
			}
		}
		
		if (anyMutationApplied) {
			QueryPoolEntry mutatedQuery = new QueryPoolEntry(mutatedFirstQuery, entry.getErrors(), 0, generation);
//			System.out.println("Original query: " + entry);
//			System.out.println("Mutated query:  " + mutatedQuery);
			return mutatedQuery;
		}
		
		return null;
	}
	
	protected List<Mutation> getMutations() {
		List<Mutation> mutations = new ArrayList<>();
		mutations.add(new BinaryOperatorMutation());
		mutations.add(new LogicalOperatorMutation());
		mutations.add(new ValueMutation());
		mutations.add(new ColumnMutation());
		mutations.add(new DeletePartialPredicateMutation());
		mutations.add(new AddNotMutation());
		mutations.add(new AddCastMutation());
		// Add more mutations here as they are implemented
		return mutations;
	}
	
	private GeneralSelect deepCopySelect(GeneralSelect original) {
		GeneralSelect copy = new GeneralSelect();
		copy.setFetchColumns(new ArrayList<>(original.getFetchColumns()));
		copy.setJoinList(new ArrayList<>(original.getJoinList()));
		copy.setFromList(new ArrayList<>(original.getFromList()));
		copy.setOrderByExpressions(new ArrayList<>(original.getOrderByExpressions()));
		copy.setWhereClause(original.getWhereClause());
		return copy;
	}
	
    @Override
    public QueryPoolEntry crossoverQueries(QueryPoolEntry entry1, QueryPoolEntry entry2, GeneralGlobalState globalState, int generation) throws Exception {
    	List<Crossover> crossovers = getCrossovers();
    	// randomly get 1 crossover to apply
    	Crossover crossover = Randomly.fromList(crossovers);
    	
		try {
			GeneralSelect offspringSelect = crossover.crossover(
				entry1.getFirstQuery(),
				entry2.getFirstQuery(),
				globalState
			);
			
			if (offspringSelect != null) {
				QueryPoolEntry offspring = new QueryPoolEntry(offspringSelect, entry1.getErrors(), 0, generation);
//				System.out.println("Parent 1:  " + entry1);
//				System.out.println("Parent 2:  " + entry2);
//				System.out.println("Offspring: " + offspring);
				return offspring;
			}
		} catch (Exception e) {
			return null;
		}
		
		return null;
	}
	
	/**
	 * Get the list of available crossover strategies for this oracle.
	 * Can be overridden in subclasses to provide different crossover strategies.
	 * 
	 * @return A list of Crossover objects to apply
	 */
	protected List<Crossover> getCrossovers() {
		List<Crossover> crossovers = new ArrayList<>();
		crossovers.add(new SimpleCrossJoinCrossover());
//		crossovers.add(new ExistCrossover());
		crossovers.add(new SwapWhereCrossover());
		// Add more crossovers here as they are implemented
		return crossovers;
	}
	
    @Override
    public QueryPoolEntry generateRandomQueryPoolEntry(GeneralGlobalState globalState, int generation) throws Exception {
    	return generateSelectStatement(generation);
	}
}
