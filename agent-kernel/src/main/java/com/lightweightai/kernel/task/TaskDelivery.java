package com.lightweightai.kernel.task;

public record TaskDelivery(String deliveryId, String taskId, String principalId, String channel,
                           String payload, boolean delivered, long version, long updatedAt) {}
