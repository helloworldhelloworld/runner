package com.lightweightai.web.service;

import com.lightweightai.web.model.SharedClientTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SharedClientToolServiceTest {

    private SharedClientToolService service;

    @BeforeEach
    void setUp() {
        service = new SharedClientToolService();
    }

    private SharedClientTool payload(String name, String ns) {
        SharedClientTool tool = new SharedClientTool();
        tool.setName(name);
        tool.setNamespace(ns);
        tool.setDescription("desc-" + name);
        tool.setOwner("owner");
        tool.setVersion("1.0");
        tool.setEnabled(true);
        tool.setInputSchema(Map.of("type", "object"));
        return tool;
    }

    @Test
    void listEmptyWhenNoTools() {
        assertTrue(service.list().isEmpty());
    }

    @Test
    void upsertCreatesNewTool() {
        SharedClientTool result = service.upsert("tool-1", payload("GetPosition", "GPS"), "alice");

        assertEquals("tool-1", result.getKey());
        assertEquals("GetPosition", result.getName());
        assertEquals("GPS", result.getNamespace());
        assertEquals("alice", result.getCreatedBy());
        assertEquals("alice", result.getUpdatedBy());
        assertTrue(result.getCreatedAt() > 0);
        assertTrue(result.getUpdatedAt() > 0);
    }

    @Test
    void upsertUpdatesExistingToolPreservesCreatedBy() throws InterruptedException {
        service.upsert("tool-1", payload("V1", "ns"), "alice");
        Thread.sleep(10);
        SharedClientTool updated = service.upsert("tool-1", payload("V2", "ns"), "bob");

        assertEquals("V2", updated.getName());
        assertEquals("alice", updated.getCreatedBy());
        assertEquals("bob", updated.getUpdatedBy());
    }

    @Test
    void listReturnsSortedByUpdatedAtDescending() throws InterruptedException {
        service.upsert("a", payload("A", "ns"), "user");
        Thread.sleep(10);
        service.upsert("b", payload("B", "ns"), "user");
        Thread.sleep(10);
        service.upsert("c", payload("C", "ns"), "user");

        List<SharedClientTool> list = service.list();
        assertEquals(3, list.size());
        assertEquals("C", list.get(0).getName());
        assertEquals("B", list.get(1).getName());
        assertEquals("A", list.get(2).getName());
    }

    @Test
    void removeExistingKeyReturnsTrue() {
        service.upsert("tool-1", payload("T", "ns"), "user");
        assertTrue(service.remove("tool-1"));
        assertTrue(service.list().isEmpty());
    }

    @Test
    void removeNonExistentKeyReturnsFalse() {
        assertFalse(service.remove("no-such-key"));
    }

    @Test
    void upsertCopiesAllFields() {
        SharedClientTool p = payload("Tool", "Camera");
        p.setMockResponse(Map.of("result", "ok"));

        SharedClientTool result = service.upsert("k", p, "user");
        assertEquals("Camera", result.getNamespace());
        assertEquals("Tool", result.getName());
        assertEquals("desc-Tool", result.getDescription());
        assertEquals("owner", result.getOwner());
        assertEquals("1.0", result.getVersion());
        assertTrue(result.isEnabled());
        assertNotNull(result.getInputSchema());
        assertEquals("ok", result.getMockResponse().get("result"));
    }

    @Test
    void listReturnsDefensiveCopy() {
        service.upsert("a", payload("A", "ns"), "user");
        List<SharedClientTool> list1 = service.list();
        List<SharedClientTool> list2 = service.list();
        assertNotSame(list1, list2);
    }

    @Test
    void upsertThenRemoveThenListIsEmpty() {
        service.upsert("k", payload("T", "ns"), "user");
        assertEquals(1, service.list().size());
        service.remove("k");
        assertTrue(service.list().isEmpty());
    }
}
