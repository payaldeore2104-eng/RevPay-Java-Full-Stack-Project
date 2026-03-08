package com.revpay.controller;

import com.revpay.model.User;
import com.revpay.service.EmailService;
import com.revpay.service.UserService;
import com.revpay.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;

@Controller
@RequestMapping("/profile/transaction-pin")
public class TransactionPinController {

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    private User getLoggedInUser(HttpSession session) {
        String loginId = (String) session.getAttribute("loggedInUser");
        if (loginId == null)
            return null;
        return userService.getUserByLoginId(loginId);
    }

    // ── 1. Show Setup UI ────────────────────────────────────────────────────────
    @GetMapping
    public String showPinSetup(HttpSession session, Model model) {
        User user = getLoggedInUser(session);
        if (user == null)
            return "redirect:/login";

        model.addAttribute("hasExistingPin", user.getTransactionPin() != null && !user.getTransactionPin().isEmpty());
        return "set_transaction_pin";
    }

    // ── 2. Handle Initial Form & Send OTP ───────────────────────────────────────
    @PostMapping("/send-otp")
    public String sendOtp(
            @RequestParam(value = "currentPin", required = false) String currentPin,
            @RequestParam("newPin") String newPin,
            HttpSession session, Model model) {

        User user = getLoggedInUser(session);
        if (user == null)
            return "redirect:/login";

        // Validate current PIN if user already has one
        if (user.getTransactionPin() != null && !user.getTransactionPin().isEmpty()) {
            if (currentPin == null || !SecurityUtil.checkPassword(currentPin, user.getTransactionPin())) {
                model.addAttribute("error", "Current PIN is incorrect.");
                model.addAttribute("hasExistingPin", true);
                return "set_transaction_pin";
            }
        }

        // Generate 6-digit OTP
        int otp = 100000 + new java.util.Random().nextInt(900000);
        String otpStr = String.valueOf(otp);

        // Store secure state in session mimicking AuthController 2FA
        session.setAttribute("pendingNewPin", newPin);
        session.setAttribute("pinOtpCode", otpStr);
        session.setAttribute("pinOtpExpiry", System.currentTimeMillis() + (5 * 60 * 1000)); // 5 mins

        // Send Email
        emailService.sendOtp(user.getEmail(), otpStr);

        return "redirect:/profile/transaction-pin/verify-otp";
    }

    // ── 3. Show OTP Entry Form ──────────────────────────────────────────────────
    @GetMapping("/verify-otp")
    public String showOtpForm(HttpSession session) {
        if (session.getAttribute("pendingNewPin") == null)
            return "redirect:/profile/transaction-pin";
        return "verify_pin_otp";
    }

    // ── 4. Verify OTP & Save PIN ────────────────────────────────────────────────
    @PostMapping("/verify-otp")
    public String verifyOtp(@RequestParam("code") String code, HttpSession session) {
        User user = getLoggedInUser(session);
        if (user == null)
            return "redirect:/login";

        String expectedCode = (String) session.getAttribute("pinOtpCode");
        String pendingNewPin = (String) session.getAttribute("pendingNewPin");
        Long expiry = (Long) session.getAttribute("pinOtpExpiry");

        if (expectedCode == null || pendingNewPin == null)
            return "redirect:/profile/transaction-pin";

        // Check Expiry
        if (expiry != null && System.currentTimeMillis() > expiry) {
            clearSessionAttributes(session);
            return "redirect:/profile/transaction-pin/verify-otp?error=expired";
        }

        // Verify Code
        if (expectedCode.equals(code.trim())) {
            try {
                // OTP verified! Update PIN in database
                userService.updatePin(user.getId(), pendingNewPin);
                clearSessionAttributes(session);
                return "redirect:/profile?success=pin";
            } catch (Exception e) {
                clearSessionAttributes(session);
                return "redirect:/profile/transaction-pin?error=true";
            }
        }

        return "redirect:/profile/transaction-pin/verify-otp?error=true";
    }

    private void clearSessionAttributes(HttpSession session) {
        session.removeAttribute("pendingNewPin");
        session.removeAttribute("pinOtpCode");
        session.removeAttribute("pinOtpExpiry");
    }
}
