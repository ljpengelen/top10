package nl.cofx.top10.session.csrf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

class CsrfHeaderCheckerTest {

    private static final String TARGET = "https://www.example.com";
    private static final String ORIGIN_HEADER_NAME = "Origin";
    private static final String REFERER_HEADER_NAME = "Referer";

    private final CsrfHeaderChecker csrfHeaderChecker = new CsrfHeaderChecker(TARGET);
    private final RoutingContext routingContext = mock(RoutingContext.class);
    private final HttpServerRequest request = mock(HttpServerRequest.class);
    private final HttpServerResponse response = mock(HttpServerResponse.class);

    @BeforeEach
    public void setUp() {
        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(response.putHeader(anyString(), anyString())).thenReturn(response);

        when(routingContext.request()).thenReturn(request);
        when(routingContext.response()).thenReturn(response);
    }

    @Test
    public void rejectsRequestWithoutHeaders() {
        csrfHeaderChecker.handle(routingContext);

        verify(response).setStatusCode(400);
        var bufferCaptor = ArgumentCaptor.forClass(Buffer.class);
        verify(response).end(bufferCaptor.capture());
        var actualResponse = new JsonObject(bufferCaptor.getValue());
        assertThat(actualResponse.getString("error")).isEqualTo("Origin and referer do not match \"https://www.example.com\"");
    }

    @Test
    public void rejectsRequestWithMalformedHeaders() {
        when(request.getHeader(ORIGIN_HEADER_NAME)).thenReturn("bogus");
        when(request.getHeader(REFERER_HEADER_NAME)).thenReturn("bogus");
        csrfHeaderChecker.handle(routingContext);

        verify(response).setStatusCode(400);
        var bufferCaptor = ArgumentCaptor.forClass(Buffer.class);
        verify(response).end(bufferCaptor.capture());
        var actualResponse = new JsonObject(bufferCaptor.getValue());
        assertThat(actualResponse.getString("error")).isEqualTo("Origin and referer do not match \"https://www.example.com\"");
    }

    @Test
    public void acceptsRequestWithMatchingOriginHeader() {
        when(request.getHeader(ORIGIN_HEADER_NAME)).thenReturn(TARGET);
        csrfHeaderChecker.handle(routingContext);

        verifyNoInteractions(response);
        verify(routingContext).next();
    }

    @Test
    public void acceptsRequestWithMatchingRefererHeader() {
        when(request.getHeader(REFERER_HEADER_NAME)).thenReturn(TARGET);
        csrfHeaderChecker.handle(routingContext);

        verifyNoInteractions(response);
        verify(routingContext).next();
    }
}
