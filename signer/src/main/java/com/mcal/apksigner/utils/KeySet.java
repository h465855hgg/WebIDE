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

package com.mcal.apksigner.utils;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class KeySet {
    String name;
    X509Certificate publicKey = null;
    PrivateKey privateKey = null;
    byte[] sigBlockTemplate = null;

    String signatureAlgorithm = "SHA1withRSA";

    public KeySet() {
    }

    public KeySet(String name, X509Certificate publicKey, PrivateKey privateKey, byte[] sigBlockTemplate) {
        this.name = name;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.sigBlockTemplate = sigBlockTemplate;
    }

    public KeySet(String name, X509Certificate publicKey, PrivateKey privateKey, String signatureAlgorithm, byte[] sigBlockTemplate) {
        this.name = name;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        if (signatureAlgorithm != null) {
            this.signatureAlgorithm = signatureAlgorithm;
        }
        this.sigBlockTemplate = sigBlockTemplate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public X509Certificate getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(X509Certificate publicKey) {
        this.publicKey = publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public byte[] getSigBlockTemplate() {
        return sigBlockTemplate;
    }

    public void setSigBlockTemplate(byte[] sigBlockTemplate) {
        this.sigBlockTemplate = sigBlockTemplate;
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    public void setSignatureAlgorithm(String signatureAlgorithm) {
        if (signatureAlgorithm == null) {
            this.signatureAlgorithm = "SHA1withRSA";
        } else {
            this.signatureAlgorithm = signatureAlgorithm;
        }
    }
}
