package tsml.classifiers.distance_based.proximity;

import java.io.Serializable;
import java.util.Random;

/**
 * Purpose: // todo - docs - type the purpose of the code here
 * <p>
 * Contributors: goastler
 */

public interface ReadOnlyRandomSource extends Serializable {
    Random getRandom();
}