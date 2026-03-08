package com.revpay.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class AESEncryptionUtil {

    private static final String ALGORITHM = "AES";
    // 32 chars for AES-256 (256 bits = 32 bytes)
    private static final byte[] KEY = "RevPaySecretKeyForCardsEncxy1234".getBytes();

    public static String encrypt(String value) throws Exception {
        if (value == null)
            return null;
        SecretKeySpec secretKey = new SecretKeySpec(KEY, ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedValue = cipher.doFinal(value.getBytes());
        return Base64.getEncoder().encodeToString(encryptedValue);
    }

    public static String decrypt(String encryptedValue) throws Exception {
        if (encryptedValue == null)
            return null;
        SecretKeySpec secretKey = new SecretKeySpec(KEY, ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decryptedValue = cipher.doFinal(Base64.getDecoder().decode(encryptedValue));
        return new String(decryptedValue);
    }
}
