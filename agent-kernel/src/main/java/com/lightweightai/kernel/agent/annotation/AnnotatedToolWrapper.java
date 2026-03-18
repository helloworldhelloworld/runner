package com.lightweightai.kernel.agent.annotation;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将 @ToolFunction 注解的方法适配为 Tool 接口
 *
 * <p>自动处理：</p>
 * <ul>
 *   <li>从注解生成 ToolSchema（JSON Schema）</li>
 *   <li>从注解提取元数据（分类、标签等）</li>
 *   <li>参数类型转换（Number、Boolean、String）</li>
 *   <li>反射调用方法并返回 ToolResult</li>
 * </ul>
 */
public class AnnotatedToolWrapper implements Tool, ToolMetadata {

    private final Object target;
    private final Method method;
    private final ToolFunction annotation;
    private final String toolName;
    private final List<ParamInfo> paramInfos;

    /**
     * @param target     包含工具方法的对象实例
     * @param method     标注了 @ToolFunction 的方法
     */
    public AnnotatedToolWrapper(Object target, Method method) {
        this.target = Objects.requireNonNull(target, "Target object cannot be null");
        this.method = Objects.requireNonNull(method, "Method cannot be null");
        this.annotation = method.getAnnotation(ToolFunction.class);
        if (this.annotation == null) {
            throw new IllegalArgumentException(
                "Method " + method.getName() + " is not annotated with @ToolFunction");
        }
        this.toolName = resolveName(method, annotation);
        this.paramInfos = resolveParams(method);
        method.setAccessible(true);
    }

    // ==================== Tool 接口 ====================

    @Override
    public String getName() {
        return toolName;
    }

    @Override
    public String getDescription() {
        return annotation.description();
    }

    @Override
    public ToolSchema getSchema() {
        if (paramInfos.isEmpty()) {
            return ToolSchema.empty();
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (ParamInfo info : paramInfos) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", info.jsonType);
            if (!info.description.isEmpty()) {
                prop.put("description", info.description);
            }
            properties.put(info.name, prop);

            if (info.required) {
                required.add(info.name);
            }
        }

        if (required.isEmpty()) {
            return ToolSchema.withProperties(properties);
        }
        return ToolSchema.withRequired(properties, required.toArray(new String[0]));
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            Object[] methodArgs = buildMethodArgs(args);
            Object result = method.invoke(target, methodArgs);

            // 如果方法返回 ToolResult，直接使用
            if (result instanceof ToolResult) {
                return (ToolResult) result;
            }

            // 否则转为 String
            String content = result != null ? result.toString() : "";
            return ToolResult.success(content);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            return ToolResult.error(cause != null ? cause : e);
        } catch (Exception e) {
            return ToolResult.error(e);
        }
    }

    @Override
    public boolean isAutoExecute() {
        return annotation.autoExecute();
    }

    // ==================== ToolMetadata 接口 ====================

    @Override
    public String getCategory() {
        return annotation.category();
    }

    @Override
    public List<String> getTags() {
        return List.of(annotation.tags());
    }

    @Override
    public boolean isReadOnly() {
        return annotation.readOnly();
    }

    @Override
    public boolean isIdempotent() {
        return annotation.idempotent();
    }

    @Override
    public boolean isOpenWorld() {
        return annotation.openWorld();
    }

    @Override
    public boolean isClientSide() {
        return annotation.clientSide();
    }

    // ==================== 内部逻辑 ====================

    /**
     * 构建方法调用参数
     */
    private Object[] buildMethodArgs(Map<String, Object> args) {
        Object[] methodArgs = new Object[paramInfos.size()];
        for (int i = 0; i < paramInfos.size(); i++) {
            ParamInfo info = paramInfos.get(i);
            Object value = args != null ? args.get(info.name) : null;
            methodArgs[i] = convertArg(value, info.javaType);
        }
        return methodArgs;
    }

    /**
     * 参数类型转换
     */
    private Object convertArg(Object value, Class<?> targetType) {
        if (value == null) {
            return getDefaultValue(targetType);
        }

        // 目标类型已经匹配
        if (targetType.isInstance(value)) {
            return value;
        }

        // Number 类型转换
        if (value instanceof Number) {
            Number num = (Number) value;
            if (targetType == int.class || targetType == Integer.class) {
                return num.intValue();
            }
            if (targetType == long.class || targetType == Long.class) {
                return num.longValue();
            }
            if (targetType == double.class || targetType == Double.class) {
                return num.doubleValue();
            }
            if (targetType == float.class || targetType == Float.class) {
                return num.floatValue();
            }
            if (targetType == String.class) {
                return num.toString();
            }
        }

        // String 转换
        if (value instanceof String) {
            String str = (String) value;
            if (targetType == int.class || targetType == Integer.class) {
                return Integer.parseInt(str);
            }
            if (targetType == long.class || targetType == Long.class) {
                return Long.parseLong(str);
            }
            if (targetType == double.class || targetType == Double.class) {
                return Double.parseDouble(str);
            }
            if (targetType == float.class || targetType == Float.class) {
                return Float.parseFloat(str);
            }
            if (targetType == boolean.class || targetType == Boolean.class) {
                return Boolean.parseBoolean(str);
            }
        }

        // Boolean 转换
        if (value instanceof Boolean) {
            if (targetType == boolean.class || targetType == Boolean.class) {
                return value;
            }
            if (targetType == String.class) {
                return value.toString();
            }
        }

        // 其他情况：Map、List 等直接返回
        return value;
    }

    /**
     * 获取基本类型的默认值
     */
    private Object getDefaultValue(Class<?> type) {
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == boolean.class) {
            return false;
        }
        return null;
    }

    /**
     * 解析工具名称
     */
    private static String resolveName(Method method, ToolFunction annotation) {
        if (!annotation.name().isEmpty()) {
            return annotation.name();
        }
        // 驼峰转下划线：getUserName -> get_user_name
        return camelToSnake(method.getName());
    }

    /**
     * 驼峰转下划线
     */
    static String camelToSnake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 解析方法的参数信息
     */
    private static List<ParamInfo> resolveParams(Method method) {
        Parameter[] params = method.getParameters();
        List<ParamInfo> infos = new ArrayList<>();

        for (Parameter param : params) {
            ToolParam annotation = param.getAnnotation(ToolParam.class);
            String name;
            String description;
            boolean required;
            String typeOverride;

            if (annotation != null) {
                name = annotation.name().isEmpty() ? param.getName() : annotation.name();
                description = annotation.description();
                required = annotation.required();
                typeOverride = annotation.type();
            } else {
                // 没有 @ToolParam 注解，使用参数名
                name = param.getName();
                description = "";
                required = false;
                typeOverride = "";
            }

            String jsonType = typeOverride.isEmpty()
                ? javaTypeToJsonType(param.getType())
                : typeOverride;

            infos.add(new ParamInfo(name, description, required, jsonType, param.getType()));
        }

        return infos;
    }

    /**
     * Java 类型映射到 JSON Schema 类型
     */
    static String javaTypeToJsonType(Class<?> type) {
        if (type == String.class) {
            return "string";
        }
        if (type == int.class || type == Integer.class) {
            return "integer";
        }
        if (type == long.class || type == Long.class) {
            return "integer";
        }
        if (type == double.class || type == Double.class) {
            return "number";
        }
        if (type == float.class || type == Float.class) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (type == List.class) {
            return "array";
        }
        if (type == Map.class) {
            return "object";
        }
        return "string"; // 默认 string
    }

    /**
     * 参数信息
     */
    static class ParamInfo {
        final String name;
        final String description;
        final boolean required;
        final String jsonType;
        final Class<?> javaType;

        ParamInfo(String name, String description, boolean required, String jsonType, Class<?> javaType) {
            this.name = name;
            this.description = description;
            this.required = required;
            this.jsonType = jsonType;
            this.javaType = javaType;
        }
    }
}
