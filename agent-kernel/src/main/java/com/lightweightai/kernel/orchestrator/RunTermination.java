package com.lightweightai.kernel.orchestrator;

/** Immutable terminal information for one execution. */
record RunTermination(CancellationCause cause, long atMillis) {}
