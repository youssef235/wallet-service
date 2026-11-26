package com.wallet.user.application;

import com.wallet.shared.domain.TooManyRequestsException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-account brake on password guessing: after several failed logins for the
 * same email, further attempts are refused for the rest of the window. Success
 * clears the slate. In-memory, like the rate limiting filter: right for a
 * single instance.
 *
 * The map is bounded: expired entries are swept when it grows, and once it is
 * still full, emails that are not tracked yet are not added (the per-IP rate
 * limit remains the brake for those). Known trade-off: anyone can lock an
 * account's login for one window by sending wrong passwords for it.
 */
public class LoginAttemptGuard {

    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MS = 10 * 60 * 1000;
    private static final int MAX_TRACKED_EMAILS = 50_000;

    private final Map<String, Failures> failuresByEmail = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptGuard(Clock clock) {
        this.clock = clock;
    }

    public void checkAllowed(String email) {
        Failures failures = failuresByEmail.get(email);
        if (failures != null && failures.count >= MAX_FAILURES && withinWindow(failures)) {
            throw new TooManyRequestsException("Too many failed attempts, try again later");
        }
    }

    public void onFailure(String email) {
        long now = clock.millis();
        if (failuresByEmail.size() >= MAX_TRACKED_EMAILS) {
            failuresByEmail.values().removeIf(f -> now - f.firstFailureMs > WINDOW_MS);
            if (failuresByEmail.size() >= MAX_TRACKED_EMAILS && !failuresByEmail.containsKey(email)) {
                return;
            }
        }
        failuresByEmail.compute(email, (k, existing) ->
                existing == null || now - existing.firstFailureMs > WINDOW_MS ? new Failures(1, now)
                        : new Failures(existing.count + 1, existing.firstFailureMs));
    }

    public void onSuccess(String email) {
        failuresByEmail.remove(email);
    }

    private boolean withinWindow(Failures failures) {
        return clock.millis() - failures.firstFailureMs <= WINDOW_MS;
    }

    private record Failures(int count, long firstFailureMs) {
    }
}
