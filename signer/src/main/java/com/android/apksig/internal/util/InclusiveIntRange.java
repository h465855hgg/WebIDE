


package com.android.apksig.internal.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class InclusiveIntRange {
    private final int min;
    private final int max;

    private InclusiveIntRange(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public static InclusiveIntRange fromTo(int min, int max) {
        return new InclusiveIntRange(min, max);
    }

    public static InclusiveIntRange from(int min) {
        return new InclusiveIntRange(min, Integer.MAX_VALUE);
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public List<InclusiveIntRange> getValuesNotIn(
            List<InclusiveIntRange> sortedNonOverlappingRanges) {
        if (sortedNonOverlappingRanges.isEmpty()) {
            return Collections.singletonList(this);
        }

        int testValue = min;
        List<InclusiveIntRange> result = null;
        for (InclusiveIntRange range : sortedNonOverlappingRanges) {
            int rangeMax = range.max;
            if (testValue > rangeMax) {
                continue;
            }
            int rangeMin = range.min;
            if (testValue < range.min) {
                if (result == null) {
                    result = new ArrayList<>();
                }
                result.add(fromTo(testValue, rangeMin - 1));
            }
            if (rangeMax >= max) {
                return (result != null) ? result : Collections.emptyList();
            }
            testValue = rangeMax + 1;
        }
        if (testValue <= max) {
            if (result == null) {
                result = new ArrayList<>(1);
            }
            result.add(fromTo(testValue, max));
        }
        return (result != null) ? result : Collections.emptyList();
    }

    @Override
    public String toString() {
        return "[" + min + ", " + ((max < Integer.MAX_VALUE) ? (max + "]") : "\u221e)");
    }
}
