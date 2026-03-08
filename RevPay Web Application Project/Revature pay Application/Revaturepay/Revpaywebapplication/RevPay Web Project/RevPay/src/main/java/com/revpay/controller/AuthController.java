package com.revpay.controller;

import com.revpay.dto.LoginDto;
import com.revpay.dto.SecurityAnswerDto;
import com.revpay.dto.UserRegistrationDto;
import com.revpay.service.EmailService;
import com.revpay.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.upload.dir:uploads/verification-docs}")
    private String uploadDir;

    @GetMapping("/login")
    public String showLoginForm(Model model) {
        model.addAttribute("loginDto", new LoginDto());
        return "login";
    }

    @PostMapping("/login")
    public String processLogin(@ModelAttribute("loginDto") LoginDto loginDto, HttpSession session, Model model) {
        try {
            boolean isValid = userService.validateLogin(loginDto.getLoginId(), loginDto.getPassword());
            if (isValid) {
                // Generate a random 6-digit OTP
                int otp = 100000 + new java.util.Random().nextInt(900000);
                String otpStr = String.valueOf(otp);
                session.setAttribute("pending2FA", loginDto.getLoginId());
                session.setAttribute("twoFACode", otpStr);
                session.setAttribute("twoFAExpiry", System.currentTimeMillis() + (5 * 60 * 1000)); // 5 min
                // Send real OTP email (falls back to console if SMTP not configured)
                emailService.sendOtp(loginDto.getLoginId(), otpStr);
                return "redirect:/verify-2fa";
            } else {
                model.addAttribute("error", "Invalid credentials.");
                return "login";
            }
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "login";
        }
    }

    @GetMapping("/verify-2fa")
    public String show2FAForm(HttpSession session) {
        if (session.getAttribute("pending2FA") == null)
            return "redirect:/login";
        return "verify-2fa";
    }

    @PostMapping("/verify-2fa")
    public String process2FA(@RequestParam("code") String code, HttpSession session) {
        String loginId = (String) session.getAttribute("pending2FA");
        String expectedCode = (String) session.getAttribute("twoFACode");
        Long expiry = (Long) session.getAttribute("twoFAExpiry");

        if (loginId == null || expectedCode == null)
            return "redirect:/login";

        // Check 5-minute expiry
        if (expiry != null && System.currentTimeMillis() > expiry) {
            session.removeAttribute("pending2FA");
            session.removeAttribute("twoFACode");
            session.removeAttribute("twoFAExpiry");
            return "redirect:/verify-2fa?error=expired";
        }

        if (expectedCode.equals(code.trim())) {
            session.setAttribute("loggedInUser", loginId);
            session.removeAttribute("pending2FA");
            session.removeAttribute("twoFACode");
            session.removeAttribute("twoFAExpiry");
            // Set session timeout (10 minutes)
            session.setMaxInactiveInterval(10 * 60);
            return "redirect:/dashboard";
        }
        return "redirect:/verify-2fa?error=true";
    }

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("regDto", new UserRegistrationDto());
        return "register";
    }

    @PostMapping("/register")
    public String processRegistration(
            @ModelAttribute("regDto") UserRegistrationDto dto,
            @RequestParam(value = "docFile", required = false) MultipartFile docFile,
            Model model) {
        try {
            Long userId = userService.registerUser(dto);

            // If registering as a business and a document type was provided, save it
            if ("BUSINESS".equalsIgnoreCase(dto.getRoleName())
                    && dto.getDocType() != null && !dto.getDocType().isEmpty()) {
                try {
                    String bizName = (dto.getBusinessName() != null && !dto.getBusinessName().isEmpty())
                            ? dto.getBusinessName()
                            : dto.getFullName();

                    // Handle optional file upload
                    String savedPath = null;
                    if (docFile != null && !docFile.isEmpty()) {
                        savedPath = saveUploadedFile(docFile, userId);
                    }

                    jdbcTemplate.update(
                            "INSERT INTO business_verification_docs " +
                                    "(user_id, business_name, doc_type, doc_description, doc_path, status, submitted_at) "
                                    +
                                    "VALUES (?, ?, ?, ?, ?, 'PENDING', SYSTIMESTAMP)",
                            userId, bizName,
                            dto.getDocType(),
                            dto.getDocDescription() != null ? dto.getDocDescription() : "",
                            savedPath);
                } catch (Exception docEx) {
                    System.err.println(
                            "[RevPay] Could not save verification doc during registration: " + docEx.getMessage());
                }
            }

            return "redirect:/login?registered=true";
        } catch (Exception e) {
            model.addAttribute("error", "Registration failed: " + e.getMessage());
            return "register";
        }
    }

    /** Saves uploaded file to disk and returns the stored relative path */
    private String saveUploadedFile(MultipartFile file, Long userId) throws Exception {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
        Files.createDirectories(uploadPath);
        String originalName = file.getOriginalFilename();
        String ext = (originalName != null && originalName.contains("."))
                ? originalName.substring(originalName.lastIndexOf("."))
                : "";
        String fileName = "user" + userId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ext;
        Path destination = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        return uploadDir + "/" + fileName;
    }

    // ─────────────────────────────────────────────────────────────
    // FORGOT PASSWORD FLOW
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/forgot-password")
    public String showForgotPassword(Model model) {
        try {
            List<Map<String, Object>> questions = jdbcTemplate
                    .queryForList("SELECT id, question FROM security_questions ORDER BY id");
            model.addAttribute("questions", questions);
        } catch (Exception e) {
            model.addAttribute("error", "Security questions not available.");
        }
        return "forgot-password";
    }

    @PostMapping("/forgot-password/verify")
    public String verifyForgotPassword(@RequestParam("loginId") String loginId,
            @RequestParam("q1Id") Long q1Id, @RequestParam("a1") String a1,
            @RequestParam("q2Id") Long q2Id, @RequestParam("a2") String a2,
            HttpSession session, Model model) {
        List<SecurityAnswerDto> answers = new ArrayList<>();
        SecurityAnswerDto ans1 = new SecurityAnswerDto();
        ans1.setQuestionId(q1Id);
        ans1.setAnswer(a1);
        SecurityAnswerDto ans2 = new SecurityAnswerDto();
        ans2.setQuestionId(q2Id);
        ans2.setAnswer(a2);
        answers.add(ans1);
        answers.add(ans2);

        try {
            boolean valid = userService.verifySecurityAnswers(loginId, answers);
            if (valid) {
                session.setAttribute("resetPasswordUser", loginId);
                return "redirect:/forgot-password?step=reset";
            } else {
                return "redirect:/forgot-password?error=true";
            }
        } catch (Exception e) {
            return "redirect:/forgot-password?error=true";
        }
    }

    @PostMapping("/forgot-password/reset")
    public String resetForgotPassword(@RequestParam("newPassword") String newPassword, HttpSession session) {
        String loginId = (String) session.getAttribute("resetPasswordUser");
        if (loginId == null)
            return "redirect:/login";

        try {
            userService.resetPasswordWithAnswers(loginId, newPassword);
            session.removeAttribute("resetPasswordUser");
            return "redirect:/login?reset=true";
        } catch (Exception e) {
            return "redirect:/forgot-password?step=reset&error=true";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login?logout=true";
    }
}
