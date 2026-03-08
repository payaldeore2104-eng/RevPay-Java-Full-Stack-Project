import java.io.*;
import java.nio.file.*;

public class ReplaceLogo2 {
    public static void main(String[] args) throws Exception {
        String dir = "d:/copy/jjhoi/Revaturepay/Revpaywebapplication/sai project/RevPay/src/main/resources/templates";
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(dir), "*.html")) {
            for (Path file : stream) {
                String content = new String(Files.readAllBytes(file), "UTF-8");
                boolean changed = false;

                int startIdx;
                while ((startIdx = content.indexOf("<div class=\"brand\">")) != -1) {
                    int firstClose = content.indexOf("</div>", startIdx + 19);
                    if (firstClose != -1) {
                        int secondClose = content.indexOf("</div>", firstClose + 6);
                        if (secondClose != -1) {
                            String sub = content.substring(startIdx, secondClose + 6);
                            if (sub.contains("brand-icon")) {
                                String replacement = "<div class=\"brand\">\n                <img th:src=\"@{/images/revpay-logo.png}\" alt=\"RevPay Logo\" class=\"app-logo\" />\n            </div>";
                                content = content.substring(0, startIdx) + replacement
                                        + content.substring(secondClose + 6);
                                changed = true;
                                continue;
                            }
                        }
                    }
                    break;
                }

                if (changed) {
                    Files.write(file, content.getBytes("UTF-8"));
                    count++;
                }
            }
        }
        System.out.println("Replaced text in " + count + " files.");
    }
}
