import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class RemoveLogo {
    public static void main(String[] args) throws Exception {
        String dir = "d:/copy/jjhoi/Revaturepay/Revpaywebapplication/sai project/RevPay/src/main/resources/templates";
        Pattern pApp = Pattern.compile("(?s)<img[^>]*class=\"app-logo\"[^>]*>");
        Pattern pAuth = Pattern.compile("(?s)<img[^>]*class=\"auth-logo\"[^>]*>");

        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(dir), "*.html")) {
            for (Path file : stream) {
                String content = new String(Files.readAllBytes(file), "UTF-8");
                boolean changed = false;

                Matcher mApp = pApp.matcher(content);
                if (mApp.find()) {
                    content = mApp.replaceAll("<div class=\"brand-icon\">\uD83D\uDCB3</div>RevPay");
                    changed = true;
                }

                Matcher mAuth = pAuth.matcher(content);
                if (mAuth.find()) {
                    content = mAuth.replaceAll("<div style=\"font-size:48px;margin-bottom:16px;\">\uD83D\uDCB3</div>");
                    changed = true;
                }

                if (changed) {
                    Files.write(file, content.getBytes("UTF-8"));
                    count++;
                }
            }
        }
        System.out.println("Replaced text in " + count + " templates.");

        // Also clean up theme.css
        Path cssPath = Paths.get(
                "d:/copy/jjhoi/Revaturepay/Revpaywebapplication/sai project/RevPay/src/main/resources/static/css/theme.css");
        if (Files.exists(cssPath)) {
            String cssContent = new String(Files.readAllBytes(cssPath), "UTF-8");
            String originalCss = cssContent;

            // Remove .app-logo blocks
            cssContent = cssContent.replaceAll("(?s)\\.app-logo\\s*\\{[^}]*\\}", "");
            // Remove .auth-logo blocks
            cssContent = cssContent.replaceAll("(?s)\\.auth-logo\\s*\\{[^}]*\\}", "");

            if (!originalCss.equals(cssContent)) {
                Files.write(cssPath, cssContent.getBytes("UTF-8"));
                System.out.println("Cleaned up theme.css");
            }
        }
    }
}
