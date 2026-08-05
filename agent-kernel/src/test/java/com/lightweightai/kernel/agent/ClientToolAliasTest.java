package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClientToolAlias - 构造验证")
class ClientToolAliasTest {

    @Test
    @DisplayName("构造函数拒绝 null aliasName")
    void rejectsNullAliasName() {
        assertThrows(NullPointerException.class,
                () -> new ClientToolAlias(null, null));
    }

    @Test
    @DisplayName("构造函数拒绝 null primary（先于 blank 校验）")
    void rejectsNullPrimary() {
        assertThrows(NullPointerException.class,
                () -> new ClientToolAlias("valid_name", null));
    }
}
