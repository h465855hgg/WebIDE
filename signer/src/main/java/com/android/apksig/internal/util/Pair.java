/*
 * WebIDE - A powerful IDE for Android web development.
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

/**
 * Pair of two elements.
 */
public final class Pair<A, B> {
    private final A mFirst;
    private final B mSecond;

    private Pair(A first, B second) {
        mFirst = first;
        mSecond = second;
    }

    public static <A, B> Pair<A, B> of(A first, B second) {
        return new Pair<A, B>(first, second);
    }

    public A getFirst() {
        return mFirst;
    }

    public B getSecond() {
        return mSecond;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mFirst == null) ? 0 : mFirst.hashCode());
        result = prime * result + ((mSecond == null) ? 0 : mSecond.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        @SuppressWarnings("rawtypes")
        Pair other = (Pair) obj;
        if (mFirst == null) {
            if (other.mFirst != null) {
                return false;
            }
        } else if (!mFirst.equals(other.mFirst)) {
            return false;
        }
        if (mSecond == null) {
            if (other.mSecond != null) {
                return false;
            }
        } else if (!mSecond.equals(other.mSecond)) {
            return false;
        }
        return true;
    }
}
