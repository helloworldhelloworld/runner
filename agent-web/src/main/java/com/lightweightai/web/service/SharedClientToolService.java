package com.lightweightai.web.service;

import com.lightweightai.web.model.SharedClientTool;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SharedClientToolService {

    private final Map<String, SharedClientTool> tools = new ConcurrentHashMap<>();

    public List<SharedClientTool> list() {
        List<SharedClientTool> values = new ArrayList<>(tools.values());
        values.sort(Comparator.comparingLong(SharedClientTool::getUpdatedAt).reversed());
        return values;
    }

    public SharedClientTool upsert(String key, SharedClientTool payload, String actorUserId) {
        long now = System.currentTimeMillis();
        SharedClientTool existing = tools.get(key);
        SharedClientTool target = existing == null ? new SharedClientTool() : existing;

        target.setKey(key);
        target.setNamespace(payload.getNamespace());
        target.setName(payload.getName());
        target.setDescription(payload.getDescription());
        target.setOwner(payload.getOwner());
        target.setVersion(payload.getVersion());
        target.setInputSchema(payload.getInputSchema());
        target.setEnabled(payload.isEnabled());
        target.setMockResponse(payload.getMockResponse());

        if (existing == null) {
            target.setCreatedBy(actorUserId);
            target.setCreatedAt(now);
        }
        target.setUpdatedBy(actorUserId);
        target.setUpdatedAt(now);

        tools.put(key, target);
        return target;
    }

    public boolean remove(String key) {
        return tools.remove(key) != null;
    }
}
