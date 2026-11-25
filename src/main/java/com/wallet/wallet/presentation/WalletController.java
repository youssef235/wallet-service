package com.wallet.wallet.presentation;

import com.wallet.shared.presentation.ApiResponse;
import com.wallet.shared.presentation.CurrentUser;
import com.wallet.wallet.application.GetMyWalletUseCase;
import com.wallet.wallet.application.TransactionHistoryUseCase;
import com.wallet.wallet.application.TransferCommand;
import com.wallet.wallet.application.TransferUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/wallet")
class WalletController {

    private final GetMyWalletUseCase getMyWalletUseCase;
    private final TransferUseCase transferUseCase;
    private final TransactionHistoryUseCase transactionHistoryUseCase;

    WalletController(GetMyWalletUseCase getMyWalletUseCase,
                     TransferUseCase transferUseCase,
                     TransactionHistoryUseCase transactionHistoryUseCase) {
        this.getMyWalletUseCase = getMyWalletUseCase;
        this.transferUseCase = transferUseCase;
        this.transactionHistoryUseCase = transactionHistoryUseCase;
    }

    @GetMapping
    ApiResponse<WalletResponse> myWallet(Authentication authentication) {
        return ApiResponse.of(WalletResponse.from(getMyWalletUseCase.execute(CurrentUser.id(authentication))));
    }

    @PostMapping("/transfers")
    ApiResponse<TransferResponse> transfer(Authentication authentication,
                                           @Valid @RequestBody TransferRequest request) {
        var result = transferUseCase.execute(new TransferCommand(CurrentUser.id(authentication),
                request.recipientEmail(), request.amountMinor(), request.idempotencyKey()));
        return ApiResponse.of(TransferResponse.from(result));
    }

    @GetMapping("/transactions")
    ApiResponse<TransactionHistoryResponse> transactions(
            Authentication authentication,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        var result = transactionHistoryUseCase.execute(CurrentUser.id(authentication), beforeId, limit);
        return ApiResponse.of(TransactionHistoryResponse.from(result));
    }
}
