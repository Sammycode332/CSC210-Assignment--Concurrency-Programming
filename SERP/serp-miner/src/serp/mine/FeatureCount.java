package serp.mine;

import serp.model.Feature;

/**
 * How many systems (papers) exhibited a given {@link Feature}. This is the unit of
 * the assignment's required output: "categorise the features in order of the number
 * of systems having the feature."
 */
public record FeatureCount(Feature feature, long systemCount) {
}
