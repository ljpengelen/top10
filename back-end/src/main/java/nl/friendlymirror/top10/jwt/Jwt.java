package nl.friendlymirror.top10.jwt;

import javax.crypto.SecretKey;

import io.jsonwebtoken.*;

public class Jwt {

    private final JwtParser jwtParser;

    public Jwt(SecretKey secretKey) {
        jwtParser = Jwts.parserBuilder().setSigningKey(secretKey).build();
    }

    public Jws<Claims> getJws(String token) {
        if (token == null) {
            return null;
        }

        try {
            return jwtParser.parseClaimsJws(token);
        } catch (Exception e) {
            return null;
        }
    }
}
