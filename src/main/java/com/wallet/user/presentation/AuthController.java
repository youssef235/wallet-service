package com.wallet.user.presentation;

import com.wallet.shared.presentation.ApiResponse;
import com.wallet.user.application.AuthResult;
import com.wallet.user.application.LoginUserCommand;
import com.wallet.user.application.LoginUserUseCase;
import com.wallet.user.application.LogoutUseCase;
import com.wallet.user.application.RefreshSessionUseCase;
import com.wallet.user.application.RegisterUserCommand;
import com.wallet.user.application.RegisterUserUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUserUseCase loginUserUseCase;
    private final RefreshSessionUseCase refreshSessionUseCase;
    private final LogoutUseCase logoutUseCase;

    AuthController(RegisterUserUseCase registerUserUseCase,
                   LoginUserUseCase loginUserUseCase,
                   RefreshSessionUseCase refreshSessionUseCase,
                   LogoutUseCase logoutUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUserUseCase = loginUserUseCase;
        this.refreshSessionUseCase = refreshSessionUseCase;
        this.logoutUseCase = logoutUseCase;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        AuthResult result = registerUserUseCase.execute(
                new RegisterUserCommand(request.email(), request.fullName(), request.password()));
        return ApiResponse.of(AuthResponse.from(result));
    }

    @PostMapping("/login")
    ApiResponse<AuthResponse> login(@Valid @RequestBody LoginUserRequest request) {
        AuthResult result = loginUserUseCase.execute(
                new LoginUserCommand(request.email(), request.password()));
        return ApiResponse.of(AuthResponse.from(result));
    }

    @PostMapping("/refresh")
    ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResult result = refreshSessionUseCase.execute(request.refreshToken());
        return ApiResponse.of(AuthResponse.from(result));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshTokenRequest request) {
        logoutUseCase.execute(request.refreshToken());
    }
}
