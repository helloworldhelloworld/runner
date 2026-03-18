package com.lightweightai.kernel.agent.annotation;

import com.lightweightai.kernel.agent.Tool;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 扫描对象中标注了 @ToolFunction 的方法，生成 Tool 列表
 *
 * <p>使用示例：</p>
 * <pre>
 * public class MyTools {
 *     &#64;ToolFunction(name = "greet", description = "Say hello")
 *     public String greet(&#64;ToolParam(name = "name", required = true) String name) {
 *         return "Hello, " + name + "!";
 *     }
 *
 *     &#64;ToolFunction(description = "Get server status")
 *     public String getServerStatus() {
 *         return "OK";
 *     }
 * }
 *
 * List&lt;Tool&gt; tools = AnnotatedToolScanner.scan(new MyTools());
 * // tools = [Tool("greet"), Tool("get_server_status")]
 * </pre>
 */
public class AnnotatedToolScanner {

    private AnnotatedToolScanner() {
        // 工具类，禁止实例化
    }

    /**
     * 扫描对象中所有 @ToolFunction 方法，返回 Tool 列表
     *
     * @param target 包含 @ToolFunction 方法的对象
     * @return Tool 列表
     */
    public static List<Tool> scan(Object target) {
        Objects.requireNonNull(target, "Target object cannot be null");

        List<Tool> tools = new ArrayList<>();
        Class<?> clazz = target.getClass();

        // 扫描所有声明的方法（包括 private）
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(ToolFunction.class)) {
                tools.add(new AnnotatedToolWrapper(target, method));
            }
        }

        // 扫描父类的 public 方法（继承的 @ToolFunction）
        for (Method method : clazz.getMethods()) {
            if (method.isAnnotationPresent(ToolFunction.class)
                && method.getDeclaringClass() != clazz) {
                tools.add(new AnnotatedToolWrapper(target, method));
            }
        }

        return tools;
    }
}
