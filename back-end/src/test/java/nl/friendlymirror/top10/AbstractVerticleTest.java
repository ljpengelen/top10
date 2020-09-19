package nl.friendlymirror.top10;

import java.util.List;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.extension.ExtendWith;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.vertx.junit5.VertxExtension;
import nl.friendlymirror.top10.jwt.Jwt;

@ExtendWith(VertxExtension.class)
public abstract class AbstractVerticleTest {

    protected static final String ENCODED_SECRET_KEY = "FsJtRGG84NM7BNewGo5AXvg6GJ1DKedDJjkirpDEAOtVgdi6j3f+THdeEika6v3dB8N4DO0fywkd+JK2A5eKLQ==";
    protected static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(Decoders.BASE64.decode(ENCODED_SECRET_KEY));
    protected final Jwt jwt = new Jwt(SECRET_KEY);

    protected String extractCookie(String cookieName, List<String> cookiesList) {
        if (cookiesList == null) {
            return null;
        }

        for (var cookies : cookiesList) {
            for (var cookie : cookies.split(";")) {
                if (cookie.startsWith(cookieName)) {
                    return cookie.substring(cookieName.length() + 1);
                }
            }
        }

        return null;
    }

    protected String extractCookie(String cookieName, String headerValue) {
        if (headerValue == null) {
            return null;
        }

        for (var headerSegment : headerValue.split(";")) {
            if (headerSegment.startsWith(cookieName)) {
                return headerSegment.substring(cookieName.length() + 1);
            }
        }

        return null;
    }
}
