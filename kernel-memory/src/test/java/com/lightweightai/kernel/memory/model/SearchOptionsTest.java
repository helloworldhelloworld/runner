package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchOptions - 搜索选项配置")
class SearchOptionsTest {

    @Test
    @DisplayName("默认值：topK=10, vectorWeight=0.7, snippetLength=700")
    void defaultValues() {
        SearchOptions opts = SearchOptions.defaults();

        assertEquals(10, opts.getTopK());
        assertEquals(0.7f, opts.getVectorWeight(), 0.001f);
        assertNotNull(opts.getMemoryTypes());
        assertEquals(3, opts.getMemoryTypes().size());
        assertNull(opts.getSessionId());
        assertEquals(700, opts.getSnippetLength());
    }

    @Test
    @DisplayName("Builder 自定义所有字段")
    void customValues() {
        SearchOptions opts = SearchOptions.builder()
            .topK(5)
            .vectorWeight(0.3f)
            .memoryTypes(Set.of(MemoryType.DURABLE))
            .sessionId("sess-123")
            .snippetLength(500)
            .build();

        assertEquals(5, opts.getTopK());
        assertEquals(0.3f, opts.getVectorWeight(), 0.001f);
        assertEquals(Set.of(MemoryType.DURABLE), opts.getMemoryTypes());
        assertEquals("sess-123", opts.getSessionId());
        assertEquals(500, opts.getSnippetLength());
    }

    @Test
    @DisplayName("vectorWeight 被 clamp 到 [0, 1] 范围")
    void vectorWeightClamped() {
        SearchOptions tooHigh = SearchOptions.builder().vectorWeight(1.5f).build();
        assertEquals(1.0f, tooHigh.getVectorWeight(), 0.001f);

        SearchOptions tooLow = SearchOptions.builder().vectorWeight(-0.5f).build();
        assertEquals(0.0f, tooLow.getVectorWeight(), 0.001f);
    }
}
