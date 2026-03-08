import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.*;

public class ReplaceCurrency {
    public static void main(String[] args) throws IOException {
        String baseDir = "d:/jjhoi/Revaturepay/Revpaywebapplication/sai project/RevPay/src/main";
        System.out.println("Starting replacement in: " + baseDir);

        Files.walk(Paths.get(baseDir))
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".html") || p.toString().endsWith(".java")
                        || p.toString().endsWith(".sql"))
                .forEach(p -> {
                    try {
                        String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                        boolean modified = false;

                        // Replace "USD" with "INR"
                        if (content.contains("USD")) {
                            content = content.replace("USD", "INR");
                            modified = true;
                        }

                        // Replace "$" with "₹" EXCEPT when followed by "{" (Thymeleaf expressions)
                        // We also don't want to replace "$2a" which is part of bcrypt hashes
                        Pattern pattern = Pattern.compile("\\$(?!\\{|2a)");
                        Matcher matcher = pattern.matcher(content);
                        StringBuffer sb = new StringBuffer();
                        while (matcher.find()) {
                            matcher.appendReplacement(sb, "₹");
                            modified = true;
                        }
                        matcher.appendTail(sb);
                        String newContent = sb.toString();

                        if (!newContent.equals(content)) {
                            content = newContent;
                            modified = true;
                        }

                        if (modified) {
                            Files.write(p, content.getBytes(StandardCharsets.UTF_8));
                            System.out.println("Updated: " + p.getFileName());
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing " + p + ": " + e.getMessage());
                    }
                });

        System.out.println("Done.");
    }
}
