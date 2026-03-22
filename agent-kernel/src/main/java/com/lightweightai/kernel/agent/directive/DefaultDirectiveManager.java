package com.lightweightai.kernel.agent.directive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.DirectiveData;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.DirectiveEnvelopeData;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.DirectiveHeader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 默认 DirectiveManager 实现。
 * 返回顺序固定为：primary + common directives。
 */
public class DefaultDirectiveManager implements DirectiveManager {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final CopyOnWriteArrayList<CommonDirectiveProvider> commonProviders = new CopyOnWriteArrayList<>();

    @Override
    public String getResult(String requestId, Directive primary) {
        Directive nonNullPrimary = Objects.requireNonNull(primary, "primary cannot be null");

        List<Directive> directives = new ArrayList<>();
        directives.add(nonNullPrimary);

        for (CommonDirectiveProvider provider : commonProviders) {
            Directive common = provider.provide(nonNullPrimary);
            if (common != null) {
                directives.add(common);
            }
        }
        try {
            List<DirectiveData> items = directives.stream()
                .map(this::toDirectiveData)
                .toList();
            DirectiveEnvelopeData data = new DirectiveEnvelopeData(nonNullPrimary.getDirectiveId(), items);
            WebSocketMessage message = WebSocketMessage.directive(requestId, data);
            return MAPPER.writeValueAsString(message);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize directive message", e);
        }
    }

    public void addCommonProvider(CommonDirectiveProvider provider) {
        commonProviders.add(Objects.requireNonNull(provider, "provider cannot be null"));
    }

    @FunctionalInterface
    public interface CommonDirectiveProvider {
        Directive provide(Directive primary);
    }

    private DirectiveData toDirectiveData(Directive directive) {
        DirectiveHeader header = new DirectiveHeader(directive.getNamespace(), directive.getName());
        return new DirectiveData(header, directive.getPayload());
    }
}
