package nl.cofx.top10.oauth;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class OAuthState {

    String codeChallenge;
    String redirectUrl;
    String state;
    String token;
}
