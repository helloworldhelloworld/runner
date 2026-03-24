package com.lightweightai.kernel.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 语义版本范围匹配器
 *
 * 支持的格式：
 * - "*"                匹配所有版本
 * - "=2.1.0"           精确匹配
 * - ">=2.0.0"          大于等于
 * - ">=2.0.0,<3.0.0"   多约束（AND）
 *
 * 支持的运算符：>=, >, <=, <, =
 *
 * 当客户端未提供版本号（null）时，只有 all() 匹配。
 */
public final class VersionRange {

    private static final VersionRange ALL = new VersionRange(List.of(), true);

    private final List<Constraint> constraints;
    private final boolean matchAll;

    private VersionRange(List<Constraint> constraints, boolean matchAll) {
        this.constraints = constraints;
        this.matchAll = matchAll;
    }

    /** 匹配所有版本（包括 null 版本） */
    public static VersionRange all() {
        return ALL;
    }

    /** 精确匹配指定版本 */
    public static VersionRange exact(String version) {
        if (version == null || version.isBlank()) {
            return ALL;
        }
        return new VersionRange(List.of(new Constraint(Operator.EQ, parseVersion(version))), false);
    }

    /**
     * 解析版本范围表达式
     *
     * @param expression 如 ">=2.0.0", ">=2.0.0,<3.0.0", "*", "=2.1.0"
     */
    public static VersionRange parse(String expression) {
        if (expression == null || expression.isBlank() || "*".equals(expression.trim())) {
            return ALL;
        }

        String[] parts = expression.split(",");
        List<Constraint> constraints = new ArrayList<>();
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            constraints.add(parseConstraint(part));
        }

        if (constraints.isEmpty()) {
            return ALL;
        }
        return new VersionRange(constraints, false);
    }

    /**
     * 检查版本是否匹配此范围
     *
     * @param version 版本字符串，如 "2.1.0"。null 只匹配 all()。
     */
    public boolean matches(String version) {
        if (matchAll) {
            return true;
        }
        if (version == null || version.isBlank()) {
            return false;
        }
        int[] v = parseVersion(version);
        for (Constraint c : constraints) {
            if (!c.matches(v)) {
                return false;
            }
        }
        return true;
    }

    public boolean isAll() {
        return matchAll;
    }

    // ==================== Internal ====================

    private static Constraint parseConstraint(String expr) {
        Operator op;
        String versionStr;

        if (expr.startsWith(">=")) {
            op = Operator.GTE;
            versionStr = expr.substring(2).trim();
        } else if (expr.startsWith(">")) {
            op = Operator.GT;
            versionStr = expr.substring(1).trim();
        } else if (expr.startsWith("<=")) {
            op = Operator.LTE;
            versionStr = expr.substring(2).trim();
        } else if (expr.startsWith("<")) {
            op = Operator.LT;
            versionStr = expr.substring(1).trim();
        } else if (expr.startsWith("=")) {
            op = Operator.EQ;
            versionStr = expr.substring(1).trim();
        } else {
            // No operator means exact match
            op = Operator.EQ;
            versionStr = expr.trim();
        }

        return new Constraint(op, parseVersion(versionStr));
    }

    static int[] parseVersion(String version) {
        String[] parts = version.split("\\.");
        int[] result = new int[3];
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            try {
                result[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }

    private static int compare(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            int cmp = Integer.compare(a[i], b[i]);
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    private enum Operator {
        EQ, GT, GTE, LT, LTE
    }

    private record Constraint(Operator op, int[] version) {
        boolean matches(int[] candidate) {
            int cmp = compare(candidate, version);
            return switch (op) {
                case EQ -> cmp == 0;
                case GT -> cmp > 0;
                case GTE -> cmp >= 0;
                case LT -> cmp < 0;
                case LTE -> cmp <= 0;
            };
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VersionRange that)) return false;
        if (matchAll != that.matchAll) return false;
        if (constraints.size() != that.constraints.size()) return false;
        for (int i = 0; i < constraints.size(); i++) {
            Constraint a = constraints.get(i);
            Constraint b = that.constraints.get(i);
            if (a.op != b.op || compare(a.version, b.version) != 0) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchAll, constraints.size());
    }

    @Override
    public String toString() {
        if (matchAll) return "VersionRange{*}";
        StringBuilder sb = new StringBuilder("VersionRange{");
        for (int i = 0; i < constraints.size(); i++) {
            if (i > 0) sb.append(",");
            Constraint c = constraints.get(i);
            sb.append(switch (c.op) {
                case EQ -> "=";
                case GT -> ">";
                case GTE -> ">=";
                case LT -> "<";
                case LTE -> "<=";
            });
            sb.append(c.version[0]).append('.').append(c.version[1]).append('.').append(c.version[2]);
        }
        sb.append('}');
        return sb.toString();
    }
}
