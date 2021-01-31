package nl.cofx.top10.session.csrf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Set;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import nl.cofx.top10.jwt.Jwt;

class CsrfTokenHandlerTest {

    private static final String ENCODED_SECRET_KEY = "FsJtRGG84NM7BNewGo5AXvg6GJ1DKedDJjkirpDEAOtVgdi6j3f+THdeEika6v3dB8N4DO0fywkd+JK2A5eKLQ==";
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(Decoders.BASE64.decode(ENCODED_SECRET_KEY));
    private static final String TOKEN_HEADER_NAME = "x-csrf-token";
    private static final String CSRF_COOKIE_NAME = "csrf-token";
    private static final String CSRF_TOKEN_CLAIM_NAME = "csrfToken";

    private final Jwt jwt = new Jwt(SECRET_KEY);
    private final CsrfTokenHandler csrfHeaderChecker = new CsrfTokenHandler(jwt, SECRET_KEY);
    private final RoutingContext routingContext = mock(RoutingContext.class);
    private final HttpServerRequest request = mock(HttpServerRequest.class);
    private final HttpServerResponse response = mock(HttpServerResponse.class);

    @BeforeEach
    public void setUpMocks() {
        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(response.putHeader(anyString(), anyString())).thenReturn(response);

        when(routingContext.request()).thenReturn(request);
        when(routingContext.response()).thenReturn(response);
    }

    @Test
    public void setsTokensGivenMethodToIgnore() {
        Set.of(HttpMethod.GET, HttpMethod.OPTIONS).forEach(method -> {

            reset(request, response, routingContext);
            setUpMocks();

            when(request.method()).thenReturn(method);
            csrfHeaderChecker.handle(routingContext);

            var stringCaptor = ArgumentCaptor.forClass(String.class);
            verify(response).putHeader(eq(TOKEN_HEADER_NAME), stringCaptor.capture());
            var cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());
            verify(routingContext).next();

            var headerValue = stringCaptor.getValue();
            assertThat(headerValue).isNotBlank();
            var cookieValue = cookieCaptor.getValue().getValue();
            var jws = jwt.getJws(cookieValue);
            assertThat(jws.getBody().get(CSRF_TOKEN_CLAIM_NAME)).isEqualTo(headerValue);
        });
    }

    @Test
    public void setsNewTokensGivenValidTokens() {
        when(request.method()).thenReturn(HttpMethod.POST);
        var token = "abcdefg12345";
        var cookieValue = Jwts.builder()
                .claim(CSRF_TOKEN_CLAIM_NAME, token)
                .signWith(SECRET_KEY, SignatureAlgorithm.HS512)
                .compact();
        when(request.getCookie(CSRF_COOKIE_NAME)).thenReturn(Cookie.cookie(CSRF_COOKIE_NAME, cookieValue));
        when(request.getHeader(TOKEN_HEADER_NAME)).thenReturn(token);
        csrfHeaderChecker.handle(routingContext);

        var stringCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).putHeader(eq(TOKEN_HEADER_NAME), stringCaptor.capture());
        var cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        verify(response).addCookie(cookieCaptor.capture());
        verify(routingContext).next();

        var headerValue = stringCaptor.getValue();
        assertThat(headerValue).isNotBlank();
        var newCookieValue = cookieCaptor.getValue().getValue();
        var jws = jwt.getJws(newCookieValue);
        var tokenInCookie = jws.getBody().get(CSRF_TOKEN_CLAIM_NAME);
        assertThat(tokenInCookie).isEqualTo(headerValue);

        assertThat(headerValue).isNotEqualTo(token);
        assertThat(tokenInCookie).isNotEqualTo(token);
    }

    @Test
    public void rejectsRequestWithoutCookie() {
        when(request.method()).thenReturn(HttpMethod.POST);
        csrfHeaderChecker.handle(routingContext);

        verify(response).setStatusCode(400);
        var bufferCaptor = ArgumentCaptor.forClass(Buffer.class);
        verify(response).end(bufferCaptor.capture());
        var actualResponse = new JsonObject(bufferCaptor.getValue());
        assertThat(actualResponse.getString("error")).isEqualTo("Invalid CSRF token");
    }

    @Test
    public void rejectsRequestWithInvalidCookie() {
        when(request.getCookie(CSRF_COOKIE_NAME)).thenReturn(Cookie.cookie(CSRF_COOKIE_NAME, "invalid value"));
        when(request.method()).thenReturn(HttpMethod.POST);
        csrfHeaderChecker.handle(routingContext);

        verify(response).setStatusCode(400);
        var bufferCaptor = ArgumentCaptor.forClass(Buffer.class);
        verify(response).end(bufferCaptor.capture());
        var actualResponse = new JsonObject(bufferCaptor.getValue());
        assertThat(actualResponse.getString("error")).isEqualTo("Invalid CSRF token");
    }

    @Test
    public void rejectsRequestWithCookieWithMissingClaim() {
        var cookieValue = Jwts.builder()
                .setSubject("abcde5678")
                .signWith(SECRET_KEY, SignatureAlgorithm.HS512)
                .compact();
        when(request.getCookie(CSRF_COOKIE_NAME)).thenReturn(Cookie.cookie(CSRF_COOKIE_NAME, cookieValue));
        when(request.method()).thenReturn(HttpMethod.POST);
        csrfHeaderChecker.handle(routingContext);

        verify(response).setStatusCode(400);
        var bufferCaptor = ArgumentCaptor.forClass(Buffer.class);
        verify(response).end(bufferCaptor.capture());
        var actualResponse = new JsonObject(bufferCaptor.getValue());
        assertThat(actualResponse.getString("error")).isEqualTo("Invalid CSRF token");
    }
}
