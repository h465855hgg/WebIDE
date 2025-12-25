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

/**
 * Android SDK version / API Level constants.
 */
public abstract class AndroidSdkVersion {

    /**
     * Android 2.3.
     */
    public static final int GINGERBREAD = 9;
    /**
     * Android 4.3. The revenge of the beans.
     */
    public static final int JELLY_BEAN_MR2 = 18;
    /**
     * Android 4.4. KitKat, another tasty treat.
     */
    public static final int KITKAT = 19;
    /**
     * Android 5.0. A flat one with beautiful shadows. But still tasty.
     */
    public static final int LOLLIPOP = 21;
    /**
     * Android 6.0. M is for Marshmallow!
     */
    public static final int M = 23;
    /**
     * Android 7.0. N is for Nougat.
     */
    public static final int N = 24;
    /**
     * Android O.
     */
    public static final int O = 26;
    /**
     * Android P.
     */
    public static final int P = 28;
    /**
     * Android R.
     */
    public static final int R = 30;

    /**
     * Hidden constructor to prevent instantiation.
     */
    private AndroidSdkVersion() {
    }
}
