package com.revpay.admin.controller;

import com.revpay.admin.dto.AdminStatsDto;
import com.revpay.admin.dto.AdminTransactionDto;
import com.revpay.admin.dto.AdminUserDto;
import com.revpay.admin.dto.AdminLoanDto;
import com.revpay.admin.service.AdminService;
import com.revpay.dto.LoginDto;
import com.revpay.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/login")
    public String adminLoginForm(Model model) {
        model.addAttribute("loginDto", new LoginDto());
        return "admin/admin-login";
    }

    @GetMapping("/test")
    @ResponseBody
    public String test() {
        return "Admin controller working";
    }

    @PostMapping("/login")
    public String processAdminLogin(@ModelAttribute("loginDto") LoginDto loginDto, HttpSession session, Model model) {
        try {
            boolean isValid = userService.validateLogin(loginDto.getLoginId(), loginDto.getPassword());
            if (!isValid) {
                model.addAttribute("error", "Invalid credentials.");
                return "admin/admin-login";
            }

            String role = null;
            try {
                role = jdbcTemplate.queryForObject(
                        "SELECT role FROM users WHERE email = ? OR phone = ?",
                        String.class, loginDto.getLoginId(), loginDto.getLoginId());
            } catch (Exception ignored) {
            }

            if (!"ROLE_ADMIN".equals(role)) {
                model.addAttribute("error", "Access denied: Admin accounts only.");
                return "admin/admin-login";
            }

            // Admin login bypasses OTP: set session and redirect directly
            session.setAttribute("loggedInUser", loginDto.getLoginId());
            session.setAttribute("userRole", "ROLE_ADMIN");
            session.setMaxInactiveInterval(10 * 60);
            return "redirect:/admin/dashboard";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "admin/admin-login";
        }
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        String loginId = (String) session.getAttribute("loggedInUser");
        model.addAttribute("userRole", "ROLE_ADMIN");
        model.addAttribute("user", loginId != null ? loginId : "Admin");
        model.addAttribute("stats", adminService.getSystemStats());
        return "admin/admin-dashboard";
    }

    @GetMapping("/users")
    public String usersPage(@RequestParam(value = "q", required = false) String q, Model model, HttpSession session) {
        String loginId = (String) session.getAttribute("loggedInUser");
        model.addAttribute("userRole", "ROLE_ADMIN");
        model.addAttribute("user", loginId != null ? loginId : "Admin");

        List<AdminUserDto> users = adminService.getAllUsers(q);
        model.addAttribute("users", users);
        model.addAttribute("q", q != null ? q : "");
        return "admin/admin-users";
    }

    @GetMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<AdminUserDto> usersJson(@RequestParam(value = "q", required = false) String q) {
        return adminService.getAllUsers(q);
    }

    @PutMapping("/users/{id}/block")
    @ResponseBody
    public String blockUserPut(@PathVariable("id") Long id) {
        adminService.blockUser(id);
        return "OK";
    }

    @PostMapping("/users/{id}/block")
    public String blockUserPost(@PathVariable("id") Long id,
            @RequestParam(value = "q", required = false) String q) {
        adminService.blockUser(id);
        return "redirect:/admin/users" + (q != null && !q.trim().isEmpty() ? "?q=" + q : "");
    }

    @PutMapping("/users/{id}/unblock")
    @ResponseBody
    public String unblockUserPut(@PathVariable("id") Long id) {
        adminService.unblockUser(id);
        return "OK";
    }

    @PostMapping("/users/{id}/unblock")
    public String unblockUserPost(@PathVariable("id") Long id,
            @RequestParam(value = "q", required = false) String q) {
        adminService.unblockUser(id);
        return "redirect:/admin/users" + (q != null && !q.trim().isEmpty() ? "?q=" + q : "");
    }

    @GetMapping("/transactions")
    public String transactionsPage(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            Model model,
            HttpSession session) {
        String loginId = (String) session.getAttribute("loggedInUser");
        model.addAttribute("userRole", "ROLE_ADMIN");
        model.addAttribute("user", loginId != null ? loginId : "Admin");

        List<AdminTransactionDto> txns = adminService.getAllTransactions(q, status, type, startDate, endDate);
        model.addAttribute("transactions", txns);
        model.addAttribute("q", q != null ? q : "");
        model.addAttribute("status", status != null ? status : "");
        model.addAttribute("type", type != null ? type : "");
        model.addAttribute("startDate", startDate != null ? startDate : "");
        model.addAttribute("endDate", endDate != null ? endDate : "");
        return "admin/admin-transactions";
    }

    @GetMapping(value = "/transactions", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<AdminTransactionDto> transactionsJson(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        return adminService.getAllTransactions(q, status, type, startDate, endDate);
    }

    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public AdminStatsDto stats() {
        return adminService.getSystemStats();
    }

    @GetMapping("/loans")
    public String loansPage(Model model, HttpSession session) {
        String loginId = (String) session.getAttribute("loggedInUser");
        model.addAttribute("userRole", "ROLE_ADMIN");
        model.addAttribute("user", loginId != null ? loginId : "Admin");

        List<AdminLoanDto> loans = adminService.getAllLoanRequests();
        model.addAttribute("loans", loans);
        return "admin/admin-loans";
    }

    @PostMapping("/loans/{id}/approve")
    public String approveLoan(@PathVariable("id") Long id) {
        adminService.approveLoan(id);
        return "redirect:/admin/loans";
    }

    @PostMapping("/loans/{id}/reject")
    public String rejectLoan(@PathVariable("id") Long id) {
        adminService.rejectLoan(id);
        return "redirect:/admin/loans";
    }
}

