package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VersionRange - 语义版本范围匹配")
class VersionRangeTest {

    @Nested
    @DisplayName("all() 匹配")
    class AllTests {
        @Test
        void matchesAnyVersion() {
            VersionRange range = VersionRange.all();
            assertTrue(range.matches("1.0.0"));
            assertTrue(range.matches("999.999.999"));
        }

        @Test
        void matchesNullVersion() {
            assertTrue(VersionRange.all().matches(null));
        }

        @Test
        void matchesBlankVersion() {
            assertTrue(VersionRange.all().matches(""));
        }

        @Test
        void isAllTrue() {
            assertTrue(VersionRange.all().isAll());
        }
    }

    @Nested
    @DisplayName("parse() 解析")
    class ParseTests {
        @Test
        void parseStar() {
            VersionRange range = VersionRange.parse("*");
            assertTrue(range.isAll());
            assertTrue(range.matches("1.0.0"));
        }

        @Test
        void parseNull() {
            assertTrue(VersionRange.parse(null).isAll());
        }

        @Test
        void parseBlank() {
            assertTrue(VersionRange.parse("  ").isAll());
        }

        @Test
        void parseGte() {
            VersionRange range = VersionRange.parse(">=2.0.0");
            assertTrue(range.matches("2.0.0"));
            assertTrue(range.matches("2.1.0"));
            assertTrue(range.matches("3.0.0"));
            assertFalse(range.matches("1.9.9"));
        }

        @Test
        void parseGt() {
            VersionRange range = VersionRange.parse(">2.0.0");
            assertFalse(range.matches("2.0.0"));
            assertTrue(range.matches("2.0.1"));
        }

        @Test
        void parseLte() {
            VersionRange range = VersionRange.parse("<=3.0.0");
            assertTrue(range.matches("3.0.0"));
            assertTrue(range.matches("2.9.9"));
            assertFalse(range.matches("3.0.1"));
        }

        @Test
        void parseLt() {
            VersionRange range = VersionRange.parse("<3.0.0");
            assertFalse(range.matches("3.0.0"));
            assertTrue(range.matches("2.9.9"));
        }

        @Test
        void parseExact() {
            VersionRange range = VersionRange.parse("=2.1.0");
            assertTrue(range.matches("2.1.0"));
            assertFalse(range.matches("2.1.1"));
            assertFalse(range.matches("2.0.9"));
        }

        @Test
        void parseNoOperator() {
            // 无运算符 = 精确匹配
            VersionRange range = VersionRange.parse("2.1.0");
            assertTrue(range.matches("2.1.0"));
            assertFalse(range.matches("2.1.1"));
        }

        @Test
        void parseMultiConstraint() {
            VersionRange range = VersionRange.parse(">=2.0.0,<3.0.0");
            assertFalse(range.matches("1.9.9"));
            assertTrue(range.matches("2.0.0"));
            assertTrue(range.matches("2.5.3"));
            assertFalse(range.matches("3.0.0"));
        }
    }

    @Nested
    @DisplayName("exact() 精确匹配")
    class ExactTests {
        @Test
        void exactMatch() {
            VersionRange range = VersionRange.exact("2.1.0");
            assertTrue(range.matches("2.1.0"));
            assertFalse(range.matches("2.1.1"));
        }

        @Test
        void exactNullVersionReturnsAll() {
            assertTrue(VersionRange.exact(null).isAll());
        }

        @Test
        void exactBlankVersionReturnsAll() {
            assertTrue(VersionRange.exact("").isAll());
        }
    }

    @Nested
    @DisplayName("null version 处理")
    class NullVersionTests {
        @Test
        void nonAllRangeDoesNotMatchNull() {
            assertFalse(VersionRange.parse(">=2.0.0").matches(null));
        }

        @Test
        void exactDoesNotMatchNull() {
            assertFalse(VersionRange.exact("2.0.0").matches(null));
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {
        @Test
        void twoPartVersion() {
            VersionRange range = VersionRange.parse(">=2.1");
            assertTrue(range.matches("2.1.0"));
            assertTrue(range.matches("2.2.0"));
            assertFalse(range.matches("2.0.9"));
        }

        @Test
        void singlePartVersion() {
            VersionRange range = VersionRange.parse(">=3");
            assertTrue(range.matches("3.0.0"));
            assertTrue(range.matches("4.0.0"));
            assertFalse(range.matches("2.9.9"));
        }
    }
}
