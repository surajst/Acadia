package com.concept.config;

import io.sentry.Sentry;
import io.sentry.SentryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Strips the reporter identity from every Sentry event.
 *
 * <p>{@code sentry.send-default-pii=false} already keeps cookies, headers and
 * request bodies out of events, but it does not stop the SDK from setting
 * {@code user.ip_address} to the literal {@code "{{auto}}"} — a marker telling
 * Sentry's ingest server to fill in the caller's IP address itself. Verified by
 * pointing the DSN at a local collector and reading the decoded envelope:
 *
 * <pre>"user":{"ip_address":"{{auto}}"}</pre>
 *
 * <p>An IP address is personal data, and the people using this system are
 * schoolchildren and their families. A stack trace, the URL and the query
 * string are enough to debug a 500; who was holding the phone is not our
 * business to ship to a third party. Dropping the user object entirely leaves
 * Sentry nothing to resolve.
 */
@Configuration
public class SentryPrivacyConfig {

    @Bean
    public Sentry.OptionsConfiguration<SentryOptions> sentryPrivacyOptions() {
        return options -> options.setBeforeSend((event, hint) -> {
            event.setUser(null);
            return event;
        });
    }
}
