package com.lightweightai.kernel.task;

import java.util.List;

public interface TaskDeliveryStore {
    TaskDelivery enqueue(String taskId, String principalId, String channel, String payload);
    List<TaskDelivery> pending();
    TaskDelivery markDelivered(TaskDelivery expected);
}
