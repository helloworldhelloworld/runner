package com.lightweightai.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamCallback — streaming interface contract")
class StreamCallbackTest {

    @Test
    @DisplayName("default methods are no-ops (no exception)")
    void defaultMethodsShouldBeNoOps() {
        StreamCallback callback = new StreamCallback() {};

        assertDoesNotThrow(() -> callback.onToken("hello"));
        assertDoesNotThrow(() -> callback.onComplete("full text"));
        assertDoesNotThrow(() -> callback.onError(new RuntimeException("test")));
    }

    @Test
    @DisplayName("onToken can be selectively overridden")
    void shouldAllowSelectiveOverride() {
        StringBuilder captured = new StringBuilder();
        StreamCallback callback = new StreamCallback() {
            @Override
            public void onToken(String token) {
                captured.append(token);
            }
        };

        callback.onToken("Hello ");
        callback.onToken("World");

        assertEquals("Hello World", captured.toString());
        assertDoesNotThrow(() -> callback.onComplete("done"));
    }

    @Test
    @DisplayName("onComplete receives full text")
    void shouldReceiveFullText() {
        String[] result = {null};
        StreamCallback callback = new StreamCallback() {
            @Override
            public void onComplete(String fullText) {
                result[0] = fullText;
            }
        };

        callback.onComplete("The complete response");
        assertEquals("The complete response", result[0]);
    }

    @Test
    @DisplayName("onError receives throwable")
    void shouldReceiveError() {
        Throwable[] captured = {null};
        StreamCallback callback = new StreamCallback() {
            @Override
            public void onError(Throwable error) {
                captured[0] = error;
            }
        };

        RuntimeException ex = new RuntimeException("boom");
        callback.onError(ex);
        assertSame(ex, captured[0]);
    }
}
