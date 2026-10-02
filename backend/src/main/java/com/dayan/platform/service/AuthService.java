package com.dayan.platform.service;

import com.dayan.platform.dto.AuthDtos.LoginRequest;
import com.dayan.platform.dto.AuthDtos.PasswordChangeRequest;
import com.dayan.platform.dto.AuthDtos.ProfileUpdateRequest;
import com.dayan.platform.vo.AuthViews.TokenView;
import com.dayan.platform.vo.AuthViews.UserView;

public interface AuthService {

    TokenView login(LoginRequest request, ClientMetadata client);

    TokenView refresh(String refreshToken, ClientMetadata client);

    void logout(long userId, String refreshToken);

    UserView currentUser(long userId);

    UserView updateProfile(long userId, ProfileUpdateRequest request);

    void changePassword(long userId, long currentSessionId, PasswordChangeRequest request);

    record ClientMetadata(String ipAddress, String userAgent) {
    }
}
