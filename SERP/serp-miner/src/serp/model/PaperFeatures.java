package serp.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * The immutable outcome of running feature extraction over a single {@link Paper}:
 * the paper plus the set of {@link Feature}s detected in it.
 *
 * <p>This is the value each extraction task returns. Because it is immutable and
 * carries only an unmodifiable {@link EnumSet}, it can be handed back from a worker
 * thread to the collector thread with no synchronisation beyond the safe
 * publication the {@code Future}/{@code CompletionService} already provides
 * (JCiP 5.5.1, 16.2.3).
 */
public record PaperFeatures(Paper paper, Set<Feature> features) {

    public PaperFeatures {
        // Defensive, unmodifiable copy so the record is genuinely immutable.
        features = (features == null || features.isEmpty())
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(features));
    }

    public boolean has(Feature f) {
        return features.contains(f);
    }
}
