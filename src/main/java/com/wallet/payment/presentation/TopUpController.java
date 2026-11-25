package com.wallet.payment.presentation;

import com.wallet.payment.application.ListMyTopUpsUseCase;
import com.wallet.payment.application.StartTopUpCommand;
import com.wallet.payment.application.StartTopUpUseCase;
import com.wallet.shared.presentation.ApiResponse;
import com.wallet.shared.presentation.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/topups")
class TopUpController {

    private final StartTopUpUseCase startTopUpUseCase;
    private final ListMyTopUpsUseCase listMyTopUpsUseCase;

    TopUpController(StartTopUpUseCase startTopUpUseCase, ListMyTopUpsUseCase listMyTopUpsUseCase) {
        this.startTopUpUseCase = startTopUpUseCase;
        this.listMyTopUpsUseCase = listMyTopUpsUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<StartTopUpResponse> start(Authentication authentication,
                                          @Valid @RequestBody StartTopUpRequest request) {
        var result = startTopUpUseCase.execute(
                new StartTopUpCommand(CurrentUser.id(authentication), request.amountMinor()));
        return ApiResponse.of(StartTopUpResponse.from(result));
    }

    @GetMapping
    ApiResponse<List<TopUpResponse>> myTopUps(Authentication authentication) {
        var results = listMyTopUpsUseCase.execute(CurrentUser.id(authentication)).stream()
                .map(TopUpResponse::from)
                .toList();
        return ApiResponse.of(results);
    }
}
