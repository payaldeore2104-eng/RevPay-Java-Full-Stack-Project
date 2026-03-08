import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class ReplaceLogo {
    public static void main(String[] args) throws Exception {
        String dir = "d:/copy/jjhoi/Revaturepay/Revpaywebapplication/sai project/RevPay/src/main/resources/templates";
        Pattern p = Pattern.compile("<div class=\"brand\">\\s*<div class=\"brand-icon\">.*?</div>.*?\\s*</div>",
                Pattern.DOTALL);
        String replacement = "<div class=\"brand\">\n                <img th:src=\"@{/images/revpay-logo.png}\" alt=\"RevPay Logo\" class=\"app-logo\" />\n            </div>";
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(dir), "*.html")) {
            for (Path file : stream) {
                String content = new String(Files.readAllBytes(file), "UTF-8");
                Matcher m = p.matcher(content);
                if (m.find()) {
                    String newContent = m.replaceAll(replacement);
                    Files.write(file, newContent.getBytes("UTF-8"));
                    count++;
                }
            }
        }
        System.out.println("Replaced text in " + count + " files.");
    }
}
