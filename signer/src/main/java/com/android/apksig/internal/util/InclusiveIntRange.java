/*
 * TreeCompose - A tree-structured file viewer built with Jetpack Compose
 * Copyright (C) 2025  如日中天  <3382198490@qq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.android.apksig.internal.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Inclusive interval of integers.
 */
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
