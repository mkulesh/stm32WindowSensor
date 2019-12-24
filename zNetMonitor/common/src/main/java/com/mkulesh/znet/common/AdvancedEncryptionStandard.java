/*
 * stm32WindowSensor: RF window sensors: STM32L + RFM69 + Android
 *
 * Copyright (C) 2019. Mikhail Kulesh
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details. You should have received a copy of the GNU General
 * Public License along with this program.
 */

package com.mkulesh.znet.common;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class AdvancedEncryptionStandard
{
    private String encryptionKey;

    public AdvancedEncryptionStandard(String encryptionKey)
    {
        this.encryptionKey = encryptionKey;
    }

    public byte[] encrypt(String plainText) throws Exception
    {
        Cipher cipher = getCipher(Cipher.ENCRYPT_MODE);
        return cipher.doFinal(plainText.getBytes());
    }

    public String decrypt(byte[] encrypted) throws Exception
    {
        Cipher cipher = getCipher(Cipher.DECRYPT_MODE);
        byte[] plainBytes = cipher.doFinal(encrypted);
        return new String(plainBytes);
    }

    private Cipher getCipher(int cipherMode) throws Exception
    {
        String encryptionAlgorithm = "AES";
        SecretKeySpec keySpecification = new SecretKeySpec(encryptionKey.getBytes("UTF-8"), encryptionAlgorithm);
        Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
        cipher.init(cipherMode, keySpecification);
        return cipher;
    }
}
