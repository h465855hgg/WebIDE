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

package com.android.apksig.apk;

/**
 * Indicates that there was an issue determining the minimum Android platform version supported by
 * an APK because the version is specified as a codename, rather than as API Level number, and the
 * codename is in an unexpected format.
 */
public class CodenameMinSdkVersionException extends MinSdkVersionException {

    private static final long serialVersionUID = 1L;

    /**
     * Encountered codename.
     */
    private final String mCodename;

    /**
     * Constructs a new {@code MinSdkVersionCodenameException} with the provided message and
     * codename.
     */
    public CodenameMinSdkVersionException(String message, String codename) {
        super(message);
        mCodename = codename;
    }

    /**
     * Returns the codename.
     */
    public String getCodename() {
        return mCodename;
    }
}
