package com.lightweightai.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Agent Web Application
 *
 * Web interface for testing and debugging Lightweight AI Kernel
 *
 * Features:
 * - Test Claude Skills and Instruction Packages
 * - Debug Tool Calling
 * - Monitor async operations
 * - Test WebSocket LLM Provider
 * - Interactive playground
 */
@SpringBootApplication
@EnableScheduling
public class AgentWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentWebApplication.class, args);
    }
}
