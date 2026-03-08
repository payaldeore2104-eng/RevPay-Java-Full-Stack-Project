package com.revpay.service;

import com.revpay.dto.UserRegistrationDto;

public interface UserService {
    Long registerUser(UserRegistrationDto dto) throws Exception;

    boolean validateLogin(String loginId, String plainPassword) throws Exception;

    void resetPassword(Long userId, String newPassword) throws Exception;

    boolean validatePin(Long userId, String pin) throws Exception;

    void updatePin(Long userId, String pin) throws Exception;

    void updateProfile(Long userId, com.revpay.dto.ProfileUpdateDto dto) throws Exception;

    void updateNotificationPreferences(Long userId, Integer prefTransactions, Integer prefRequests, Integer prefAlerts)
            throws Exception;

    void changePassword(Long userId, String oldPassword, String newPassword) throws Exception;

    void setupSecurityQuestions(Long userId, java.util.List<com.revpay.dto.SecurityAnswerDto> answers) throws Exception;

    boolean verifySecurityAnswers(String loginId, java.util.List<com.revpay.dto.SecurityAnswerDto> answers)
            throws Exception;

    void resetPasswordWithAnswers(String loginId, String newPassword) throws Exception;

    com.revpay.model.User getUserByLoginId(String loginId);
}
