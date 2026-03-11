package com.lightweightai.kernel.agent.directive;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * 端侧能力清单
 *
 * 端侧连接时一次性上报的完整能力清单。云端根据清单自动注册对应的 Tool，
 * 端侧断开时自动注销。
 *
 * <pre>
 * {
 *   "client_type": "android",
 *   "client_version": "2.1.0",
 *   "capabilities": [
 *     { "namespace": "Camera", "name": "TakePhoto", ... },
 *     { "namespace": "GPS", "name": "GetLocation", ... }
 *   ]
 * }
 * </pre>
 */
public class ClientManifest {

    @JsonProperty("client_type")
    private String clientType;

    @JsonProperty("client_version")
    private String clientVersion;

    @JsonProperty("capabilities")
    private List<ClientCapability> capabilities;

    public ClientManifest() {
    }

    public ClientManifest(String clientType, String clientVersion,
                          List<ClientCapability> capabilities) {
        this.clientType = clientType;
        this.clientVersion = clientVersion;
        this.capabilities = capabilities != null ? capabilities : Collections.emptyList();
    }

    // Getters and Setters

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    public List<ClientCapability> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<ClientCapability> capabilities) {
        this.capabilities = capabilities;
    }

    @Override
    public String toString() {
        return "ClientManifest{" +
            "clientType='" + clientType + '\'' +
            ", clientVersion='" + clientVersion + '\'' +
            ", capabilities=" + (capabilities != null ? capabilities.size() : 0) +
            '}';
    }
}
