package com.revpay.util;

import org.mindrot.jbcrypt.BCrypt;

public class SecurityUtil {

    public static String hashPassword(String plainText) {
        return BCrypt.hashpw(plainText, BCrypt.gensalt(12));
    }

    public static boolean checkPassword(String plainText, String hashedPassword) {
        if (hashedPassword == null || !hashedPassword.startsWith("$2a$")) {
            return false;
        }
        return BCrypt.checkpw(plainText, hashedPassword);
    }
}
