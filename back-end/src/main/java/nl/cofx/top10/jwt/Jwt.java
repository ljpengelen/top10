package nl.cofx.top10.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;

public class Jwt {

    private final JwtParser jwtParser;

    public Jwt(SecretKey secretKey) {
        jwtParser = Jwts.parser().verifyWith(secretKey).build();
    }

    public Jws<Claims> getJws(String token) {
        if (token == null) {
            return null;
        }

        try {
            return jwtParser.parseSignedClaims(token);
        } catch (Exception e) {
            return null;
        }
    }
}
