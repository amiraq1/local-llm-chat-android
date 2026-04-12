package ai.mlc.mlcllm;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

public class JSONFFIEngine {
    private static final String TAG = "JSONFFIEngine";
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
        DeviceSelection device = openPreferredDevice();
        requestStreamCallback = createStreamCallback(callback);
        Object deviceType = readField(device.device, "deviceType");
        Object deviceId = readField(device.device, "deviceId");

        Log.i(
                TAG,
                "Initializing background engine with backend="
                        + device.backend
                        + " deviceType="
                        + deviceType
                        + " deviceId="
                        + deviceId
        );

        invokeFunction(
                "init_background_engine",
                initBackgroundEngineFunc,
                deviceType,
                deviceId,
                requestStreamCallback
        );
    }

    public void reload(String engineConfigJSONStr) {
        invokeFunction("reload", reloadFunc, engineConfigJSONStr);
    }

    public void chatCompletion(String requestJSONStr, String requestId) {
        invokeFunction("chat_completion", chatCompletionFunc, requestJSONStr, requestId);
    }

    public void runBackgroundLoop() {
        invokeFunction("run_background_loop", runBackgroundLoopFunc);
    }

    public void runBackgroundStreamBackLoop() {
        invokeFunction("run_background_stream_back_loop", runBackgroundStreamBackLoopFunc);
    }

    public void exitBackgroundLoop() {
        invokeFunction("exit_background_loop", exitBackgroundLoopFunc);
    }

    public void unload() {
        invokeFunction("unload", unloadFunc);
    }

    public interface KotlinFunction {
        void invoke(String arg);
    }

    public void reset() {
        invokeFunction("reset", resetFunc);
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

    private static DeviceSelection openPreferredDevice() {
        Class<?> deviceClass = requireClass("org.apache.tvm.Device");
        String[] backends = {"vulkan", "opencl", "cpu"};
        Throwable lastFailure = null;

        for (String backend : backends) {
            try {
                Object device = callStatic(deviceClass, backend);
                Object exists = call(device, "exist");
                Log.i(TAG, "TVM backend probe " + backend + " exist=" + exists);
                if (Boolean.TRUE.equals(exists)) {
                    return new DeviceSelection(backend, device);
                }
            } catch (Throwable error) {
                lastFailure = error;
                Log.w(
                        TAG,
                        "TVM backend probe failed for " + backend + ": " + rootCauseMessage(error),
                        error
                );
            }
        }

        throw new IllegalStateException(
                "No usable TVM device was found. Tried: vulkan, opencl, cpu.",
                lastFailure
        );
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

    private void invokeFunction(String functionName, Object function, Object... args) {
        Object current = function;
        try {
            for (Object arg : args) {
                Object next = call(current, "pushArg", arg);
                current = next != null ? next : current;
            }
            call(current, "invoke");
        } catch (IllegalStateException error) {
            String lastError = readLastErrorSafely();
            String message = "Failed to invoke MLC function " + functionName;
            if (lastError != null && !lastError.isEmpty()) {
                message += ". TVM last error: " + lastError;
            }
            throw new IllegalStateException(message, error);
        }
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
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException(
                    "Failed to call " + target.getClass().getName() + "." + methodName
                            + ": " + rootCauseMessage(cause),
                    cause
            );
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to call " + target.getClass().getName() + "." + methodName, e);
        }
    }

    private static Object callStatic(Class<?> type, String methodName, Object... args) {
        Method method = findMethod(type, methodName, true, args);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException(
                    "Failed to call static " + type.getName() + "." + methodName
                            + ": " + rootCauseMessage(cause),
                    cause
            );
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to call static " + type.getName() + "." + methodName, e);
        }
    }

    private String readLastErrorSafely() {
        try {
            Object value = call(getLastErrorFunc, "invoke");
            return valueToString(value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String valueToString(Object value) {
        if (value == null) {
            return null;
        }
        try {
            Object asString = call(value, "asString");
            return asString != null ? String.valueOf(asString) : null;
        } catch (Throwable ignored) {
            return String.valueOf(value);
        }
    }

    private static String rootCauseMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message != null && !message.isEmpty()
                ? current.getClass().getSimpleName() + ": " + message
                : current.getClass().getSimpleName();
    }

    private static final class DeviceSelection {
        final String backend;
        final Object device;

        DeviceSelection(String backend, Object device) {
            this.backend = backend;
            this.device = device;
        }
    }

    private static Method findMethod(Class<?> type, String methodName, boolean requireStatic, Object... args) {
        Method bestMethod = null;
        int bestScore = Integer.MIN_VALUE;

        for (Method method : type.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            if (Modifier.isStatic(method.getModifiers()) != requireStatic) {
                continue;
            }
            int score = parametersMatchScore(method.getParameterTypes(), args);
            if (score < 0) {
                continue;
            }
            if (score > bestScore) {
                bestMethod = method;
                bestScore = score;
            }
        }

        if (bestMethod != null) {
            return bestMethod;
        }

        throw new IllegalStateException(
                "Unable to resolve method " + type.getName() + "." + methodName
        );
    }

    private static int parametersMatchScore(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length) {
            return -1;
        }

        int totalScore = 0;
        for (int i = 0; i < parameterTypes.length; i++) {
            int argumentScore = compatibilityScore(parameterTypes[i], args[i]);
            if (argumentScore < 0) {
                return -1;
            }
            totalScore += argumentScore;
        }

        return totalScore;
    }

    private static int compatibilityScore(Class<?> parameterType, Object arg) {
        if (arg == null) {
            return parameterType.isPrimitive() ? -1 : 1;
        }
        if (parameterType.isInstance(arg)) {
            return parameterType == arg.getClass() ? 100 : 90;
        }
        if (!parameterType.isPrimitive()) {
            return parameterType.isAssignableFrom(arg.getClass()) ? 80 : -1;
        }

        if (parameterType == boolean.class) {
            return arg instanceof Boolean ? 100 : -1;
        }
        if (parameterType == char.class) {
            return arg instanceof Character ? 100 : -1;
        }

        if (!(arg instanceof Number)) {
            return -1;
        }

        Class<?> wrapperType = arg.getClass();

        if (parameterType == byte.class) {
            return wrapperType == Byte.class ? 100 : -1;
        }
        if (parameterType == short.class) {
            return wrapperType == Short.class ? 100 : wrapperType == Byte.class ? 90 : -1;
        }
        if (parameterType == int.class) {
            if (wrapperType == Integer.class) return 100;
            if (wrapperType == Short.class || wrapperType == Byte.class) return 90;
            return -1;
        }
        if (parameterType == long.class) {
            if (wrapperType == Long.class) return 100;
            if (wrapperType == Integer.class) return 80;
            if (wrapperType == Short.class || wrapperType == Byte.class) return 70;
            return -1;
        }
        if (parameterType == float.class) {
            if (wrapperType == Float.class) return 100;
            if (wrapperType == Integer.class || wrapperType == Short.class || wrapperType == Byte.class) return 30;
            return -1;
        }
        if (parameterType == double.class) {
            if (wrapperType == Double.class) return 100;
            if (wrapperType == Float.class) return 80;
            if (wrapperType == Long.class) return 50;
            if (wrapperType == Integer.class || wrapperType == Short.class || wrapperType == Byte.class) return 20;
            return -1;
        }

        return -1;
    }
}
