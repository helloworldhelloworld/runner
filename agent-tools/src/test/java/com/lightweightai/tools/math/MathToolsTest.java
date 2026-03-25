package com.lightweightai.tools.math;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MathTools")
class MathToolsTest {

    private MathTools mathTools;

    @BeforeEach
    void setUp() {
        mathTools = new MathTools();
    }

    @Test
    @DisplayName("add should sum two integers")
    void addIntegers() {
        String result = mathTools.add(2, 3);
        assertEquals("5", result);
    }

    @Test
    @DisplayName("add should sum two decimals")
    void addDecimals() {
        String result = mathTools.add(1.5, 2.3);
        assertEquals("3.8", result);
    }

    @Test
    @DisplayName("add should handle negative numbers")
    void addNegativeNumbers() {
        String result = mathTools.add(-5, 3);
        assertEquals("-2", result);
    }

    @Test
    @DisplayName("multiply should return product of two numbers")
    void multiplyNumbers() {
        String result = mathTools.multiply(4, 5);
        assertEquals("20", result);
    }

    @Test
    @DisplayName("multiply should handle decimals")
    void multiplyDecimals() {
        String result = mathTools.multiply(2.5, 4);
        assertEquals("10", result);
    }

    @Test
    @DisplayName("multiply by zero should return zero")
    void multiplyByZero() {
        String result = mathTools.multiply(42, 0);
        assertEquals("0", result);
    }

    @Test
    @DisplayName("divide should return quotient")
    void divideNumbers() {
        ToolResult result = mathTools.divide(10, 2);
        assertFalse(result.isError());
        assertEquals("5", result.getContent());
    }

    @Test
    @DisplayName("divide should handle decimal results")
    void divideDecimalResult() {
        ToolResult result = mathTools.divide(7, 2);
        assertFalse(result.isError());
        assertEquals("3.5", result.getContent());
    }

    @Test
    @DisplayName("divide by zero should return error")
    void divideByZero() {
        ToolResult result = mathTools.divide(10, 0);
        assertTrue(result.isError());
        assertEquals("Division by zero", result.getContent());
    }

    @Test
    @DisplayName("formatNumber should return integer format for whole numbers")
    void formatNumberWholeNumber() {
        // Verify through add: 2.0 + 3.0 = 5.0 should display as "5"
        String result = mathTools.add(2.0, 3.0);
        assertEquals("5", result);
    }

    @Test
    @DisplayName("formatNumber should return decimal format for non-whole numbers")
    void formatNumberDecimal() {
        String result = mathTools.add(1.1, 2.2);
        // 1.1 + 2.2 = 3.3000000000000003 due to floating point
        assertTrue(result.contains("3.3"));
    }
}
