package ai.mlc.mlcllm;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

public class JSONFFIEngine {
    private static final String TVM_MISSING_MESSAGE =
            "MLC TVM bindings are not available in this build. "
                    + "Enable -PenableBundledTvm4j=true with a compatible tvm4j_core.jar "
                    + "to use the native engine, or keep using the fake inference engine.";

    private final Object jsonFFIEngine;
    private final Object initBackgroundEngineFunc;
    private final Object reloadFunc;
    private final Object unloadFunc;
    private final Object resetFunc;
    private final Object chatCompletionFunc;
    private final Object abortFunc;
    private final Object getLastErrorFunc;
    private final Object runBackgroundLoopFunc;
    private final Object runBackgroundStreamBackLoopFunc;
    private final Object exitBackgroundLoopFunc;
    private Object requestStreamCallback;

    public JSONFFIEngine() {
        Object createFunc = getGlobalFunction("mlc.json_ffi.CreateJSONFFIEngine");
        if (createFunc == null) {
            throw new IllegalStateException("MLC JSON FFI entry point was not found.");
        }
        jsonFFIEngine = asModule(call(createFunc, "invoke"));
        initBackgroundEngineFunc = getModuleFunction(jsonFFIEngine, "init_background_engine");
        reloadFunc = getModuleFunction(jsonFFIEngine, "reload");
        unloadFunc = getModuleFunction(jsonFFIEngine, "unload");
        resetFunc = getModuleFunction(jsonFFIEngine, "reset");
        chatCompletionFunc = getModuleFunction(jsonFFIEngine, "chat_completion");
        abortFunc = getModuleFunction(jsonFFIEngine, "abort");
        getLastErrorFunc = getModuleFunction(jsonFFIEngine, "get_last_error");
        runBackgroundLoopFunc = getModuleFunction(jsonFFIEngine, "run_background_loop");
        runBackgroundStreamBackLoopFunc = getModuleFunction(jsonFFIEngine, "run_background_stream_back_loop");
        exitBackgroundLoopFunc = getModuleFunction(jsonFFIEngine, "exit_background_loop");
    }

    public void initBackgroundEngine(KotlinFunction callback) {
        Object device = openClDevice();
        requestStreamCallback = createStreamCallback(callback);

        invokeFunction(
                initBackgroundEngineFunc,
                readField(device, "deviceType"),
                readField(device, "deviceId"),
                requestStreamCallback
        );
    }

    public void reload(String engineConfigJSONStr) {
        invokeFunction(reloadFunc, engineConfigJSONStr);
    }

    public void chatCompletion(String requestJSONStr, String requestId) {
        invokeFunction(chatCompletionFunc, requestJSONStr, requestId);
    }

    public void runBackgroundLoop() {
        invokeFunction(runBackgroundLoopFunc);
    }

    public void runBackgroundStreamBackLoop() {
        invokeFunction(runBackgroundStreamBackLoopFunc);
    }

    public void exitBackgroundLoop() {
        invokeFunction(exitBackgroundLoopFunc);
    }

    public void unload() {
        invokeFunction(unloadFunc);
    }

    public interface KotlinFunction {
        void invoke(String arg);
    }

    public void reset() {
        invokeFunction(resetFunc);
    }

    private static Object getGlobalFunction(String functionName) {
        Class<?> functionClass = requireClass("org.apache.tvm.Function");
        return callStatic(functionClass, "getFunction", functionName);
    }

    private static Object getModuleFunction(Object module, String functionName) {
        return call(module, "getFunction", functionName);
    }

    private static Object asModule(Object value) {
        return call(value, "asModule");
    }

    private static Object openClDevice() {
        Class<?> deviceClass = requireClass("org.apache.tvm.Device");
        return callStatic(deviceClass, "opencl");
    }

    private static Object createStreamCallback(KotlinFunction callback) {
        Class<?> functionClass = requireClass("org.apache.tvm.Function");
        Class<?> callbackClass = requireClass("org.apache.tvm.Function$Callback");
        if (!callbackClass.isInterface()) {
            throw new IllegalStateException("Unsupported TVM callback type: " + callbackClass.getName());
        }

        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "toString":
                        return "JSONFFIEngineCallback";
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == (args != null && args.length > 0 ? args[0] : null);
                    default:
                        return null;
                }
            }

            if ("invoke".equals(method.getName())) {
                Object[] values = unpackVarArgs(args);
                if (values.length > 0 && values[0] != null) {
                    String json = String.valueOf(call(values[0], "asString"));
                    callback.invoke(json);
                } else {
                    callback.invoke("");
                }
                return 1;
            }

            return null;
        };

        Object callbackProxy = Proxy.newProxyInstance(
                callbackClass.getClassLoader(),
                new Class<?>[]{callbackClass},
                handler
        );
        return callStatic(functionClass, "convertFunc", callbackProxy);
    }

    private static Object[] unpackVarArgs(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return new Object[0];
        }
        if (args[0] instanceof Object[]) {
            return (Object[]) args[0];
        }
        return args;
    }

    private static void invokeFunction(Object function, Object... args) {
        Object current = function;
        for (Object arg : args) {
            Object next = call(current, "pushArg", arg);
            current = next != null ? next : current;
        }
        call(current, "invoke");
    }

    private static Object readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getField(fieldName);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to read field '" + fieldName + "'", e);
        }
    }

    private static Class<?> requireClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(TVM_MISSING_MESSAGE, e);
        }
    }

    private static Object call(Object target, String methodName, Object... args) {
        Method method = findMethod(target.getClass(), methodName, false, args);
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to call " + target.getClass().getName() + "." + methodName, e);
        }
    }

    private static Object callStatic(Class<?> type, String methodName, Object... args) {
        Method method = findMethod(type, methodName, true, args);
        try {
            return method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to call static " + type.getName() + "." + methodName, e);
        }
    }

    private static Method findMethod(Class<?> type, String methodName, boolean requireStatic, Object... args) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            if (Modifier.isStatic(method.getModifiers()) != requireStatic) {
                continue;
            }
            if (!parametersMatch(method.getParameterTypes(), args)) {
                continue;
            }
            return method;
        }
        throw new IllegalStateException("Unable to resolve method " + type.getName() + "." + methodName);
    }

    private static boolean parametersMatch(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length) {
            return false;
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!isCompatible(parameterTypes[i], args[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCompatible(Class<?> parameterType, Object arg) {
        if (arg == null) {
            return !parameterType.isPrimitive();
        }
        if (parameterType.isInstance(arg)) {
            return true;
        }
        if (!parameterType.isPrimitive()) {
            return parameterType.isAssignableFrom(arg.getClass());
        }
        if (parameterType == boolean.class) {
            return arg instanceof Boolean;
        }
        if (parameterType == char.class) {
            return arg instanceof Character;
        }
        return arg instanceof Number;
    }
}
