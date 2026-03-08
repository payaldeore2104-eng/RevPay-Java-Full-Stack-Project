package com.revpay.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class TransactionIdGenerator {

	
    public static String generateTransactionId() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        int random = new Random().nextInt(9000) + 1000; // 1000 to 9999
        return "TXN-" + timestamp + "-" + random;
    }
}
