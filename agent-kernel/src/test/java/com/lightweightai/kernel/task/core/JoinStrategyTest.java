package com.lightweightai.kernel.task.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JoinStrategyTest {

    private static final TaskResult OK = TaskResult.success("ok");
    private static final TaskResult ERR = TaskResult.error("nope");
    private static final TaskResult SK = TaskResult.skipped("guard");

    @Test
    void allSuccessRequiresNonEmpty() {
        assertFalse(JoinStrategy.ALL_SUCCESS.test(List.of()),
            "empty upstream is not ALL_SUCCESS");
        assertTrue(JoinStrategy.ALL_SUCCESS.test(List.of(OK, OK)));
        assertFalse(JoinStrategy.ALL_SUCCESS.test(List.of(OK, ERR)));
        assertFalse(JoinStrategy.ALL_SUCCESS.test(List.of(OK, SK)));
    }

    @Test
    void allCompleteAcceptsAnyTerminalState() {
        assertTrue(JoinStrategy.ALL_COMPLETE.test(List.of(OK, ERR, SK)));
        assertTrue(JoinStrategy.ALL_COMPLETE.test(List.of()));
    }

    @Test
    void anySuccessNeedsOne() {
        assertTrue(JoinStrategy.ANY_SUCCESS.test(List.of(ERR, OK)));
        assertFalse(JoinStrategy.ANY_SUCCESS.test(List.of(ERR, SK)));
        assertFalse(JoinStrategy.ANY_SUCCESS.test(List.of()));
    }

    @Test
    void firstCompleteAlwaysTrue() {
        assertTrue(JoinStrategy.FIRST_COMPLETE.test(List.of()));
        assertTrue(JoinStrategy.FIRST_COMPLETE.test(List.of(ERR)));
    }

    @Test
    void quorumCountsSuccesses() {
        JoinStrategy q2 = JoinStrategy.quorum(2);
        assertFalse(q2.test(List.of(OK, ERR, ERR)));
        assertTrue(q2.test(List.of(OK, OK, ERR)));
        assertTrue(q2.test(List.of(OK, OK, OK)));
    }

    @Test
    void quorumRejectsNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> JoinStrategy.quorum(0));
        assertThrows(IllegalArgumentException.class, () -> JoinStrategy.quorum(-1));
    }
}
