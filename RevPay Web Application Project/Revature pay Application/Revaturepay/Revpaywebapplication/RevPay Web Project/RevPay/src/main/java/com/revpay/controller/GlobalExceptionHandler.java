package com.revpay.controller;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleAllExceptions(Exception ex, HttpServletRequest request, Model model) {
        logger.error("System Error caught at URI: {}", request.getRequestURI(), ex);
        String message = ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred.";

        // Provide friendlier messages for common DB errors
        if (message.contains("ORA-") || message.contains("oracle")) {
            message = "Database error: Please ensure the Oracle database is running and tables are created. " +
                    "Contact your administrator if this persists.";
        } else if (message.contains("Connection refused") || message.contains("Unable to acquire JDBC")) {
            message = "Cannot connect to the database. Please check your database connection settings.";
        }

        model.addAttribute("errorTitle", "Something went wrong");
        model.addAttribute("errorMessage", message);
        model.addAttribute("requestedUrl", request.getRequestURI());
        return "error-page";
    }
}
