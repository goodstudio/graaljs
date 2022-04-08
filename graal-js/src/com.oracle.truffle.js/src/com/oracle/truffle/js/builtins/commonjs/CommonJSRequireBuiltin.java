/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.builtins.commonjs;

import static com.oracle.truffle.js.builtins.commonjs.CommonJSResolution.isCoreModule;

import java.util.Map;
import java.util.Objects;
import java.util.Stack;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.builtins.GlobalBuiltins;
import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.nodes.function.JSBuiltin;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSArguments;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSErrorType;
import com.oracle.truffle.js.runtime.JSException;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.builtins.JSFunctionData;
import com.oracle.truffle.js.runtime.builtins.JSFunctionObject;
import com.oracle.truffle.js.runtime.builtins.JSOrdinary;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.JSObject;

public abstract class CommonJSRequireBuiltin extends GlobalBuiltins.JSFileLoadingOperation {

    private static final boolean LOG_REQUIRE_PATH_RESOLUTION = false;
    private static final Stack<TruffleString> requireDebugStack;

    public static final TruffleString UNSUPPORTED_NODE_FILE = Strings.constant("Unsupported .node file: ");

    static {
        requireDebugStack = LOG_REQUIRE_PATH_RESOLUTION ? new Stack<>() : null;
    }

    public static void log(Object... message) {
        if (LOG_REQUIRE_PATH_RESOLUTION) {
            StringBuilder s = new StringBuilder("['.'");
            for (TruffleString module : requireDebugStack) {
                s.append(" '").append(module).append("'");
            }
            s.append("] ");
            for (Object m : message) {
                String desc;
                if (m == null) {
                    desc = "null";
                } else if (m instanceof JSDynamicObject) {
                    desc = "   APIs: {" + JSObject.enumerableOwnNames((JSDynamicObject) m) + "}";
                } else {
                    desc = m.toString();
                }
                s.append(desc);
            }
            System.err.println(s.toString());
        }
    }

    private static void debugStackPush(TruffleString moduleIdentifier) {
        if (LOG_REQUIRE_PATH_RESOLUTION) {
            requireDebugStack.push(moduleIdentifier);
        }
    }

    private static void debugStackPop() {
        if (LOG_REQUIRE_PATH_RESOLUTION) {
            requireDebugStack.pop();
        }
    }

    private static final String MODULE_END = "\n});";
    private static final String MODULE_PREAMBLE = "(function (exports, require, module, __filename, __dirname) {";

    @TruffleBoundary
    static TruffleFile getModuleResolveCurrentWorkingDirectory(JSContext context, TruffleLanguage.Env env) {
        String currentFileNameFromStack = CommonJSResolution.getCurrentFileNameFromStack();
        if (currentFileNameFromStack == null) {
            String cwdOption = context.getContextOptions().getRequireCwd();
            return cwdOption == null ? env.getCurrentWorkingDirectory() : env.getPublicTruffleFile(cwdOption);
        } else {
            TruffleFile truffleFile = env.getPublicTruffleFile(currentFileNameFromStack);
            assert truffleFile.isRegularFile() && truffleFile.getParent() != null;
            return truffleFile.getParent().normalize();
        }
    }

    CommonJSRequireBuiltin(JSContext context, JSBuiltin builtin) {
        super(context, builtin);
    }

    @Specialization
    protected Object require(JSDynamicObject currentRequire, TruffleString moduleIdentifier) {
        JSRealm realm = getRealm();
        TruffleLanguage.Env env = realm.getEnv();
        TruffleFile resolutionEntryPath = getModuleResolutionEntryPath(currentRequire, env);
        return requireImpl(moduleIdentifier, resolutionEntryPath, realm);
    }

    @TruffleBoundary
    private Object requireImpl(TruffleString moduleIdentifier, TruffleFile entryPath, JSRealm realm) {
        log("required module '", moduleIdentifier, "'                                core:", isCoreModule(moduleIdentifier), " from path ", entryPath);
        if (isCoreModule(moduleIdentifier)) {
            TruffleString moduleReplacementName = Strings.fromJavaString(getContext().getContextOptions().getCommonJSRequireBuiltins().get(Strings.toJavaString(moduleIdentifier)));
            if (moduleReplacementName != null && !moduleReplacementName.isEmpty()) {
                return requireImpl(moduleReplacementName, getModuleResolveCurrentWorkingDirectory(getContext(), realm.getEnv()), realm);
            }
            // no core module replacement alias was found: continue and search in the FS.
        }
        TruffleFile maybeModule = null;
        try {
            maybeModule = CommonJSResolution.resolve(realm, moduleIdentifier, entryPath);
        } catch (SecurityException | IllegalArgumentException | UnsupportedOperationException e) {
            // Module resolution does not execute JS code. Therefore, an exception at this stage is
            // either IO-related (e.g., file not found) or was raised in a custom Truffle FS.
            // We treat any exception as a module loading failure.
            throw fail(moduleIdentifier, Strings.fromJavaString(e.getMessage()));
        }
        log("module ", moduleIdentifier, " resolved to ", maybeModule);
        if (maybeModule == null) {
            throw fail(moduleIdentifier);
        }
        if (isJsFile(maybeModule)) {
            return evalJavaScriptFile(maybeModule, moduleIdentifier);
        } else if (isJsonFile(maybeModule)) {
            return evalJsonFile(maybeModule);
        } else if (isNodeBinFile(maybeModule)) {
            throw fail(UNSUPPORTED_NODE_FILE, moduleIdentifier);
        } else {
            throw fail(moduleIdentifier);
        }
    }

    private Object evalJavaScriptFile(TruffleFile modulePath, TruffleString moduleIdentifier) {
        JSRealm realm = getRealm();
        TruffleFile normalizedPath = modulePath.normalize();
        // If cached, return from cache. This is by design to avoid infinite require loops.
        Map<TruffleFile, JSDynamicObject> commonJSCache = realm.getCommonJSRequireCache();
        if (commonJSCache.containsKey(normalizedPath)) {
            JSDynamicObject moduleBuiltin = commonJSCache.get(normalizedPath);
            Object cached = JSObject.get(moduleBuiltin, Strings.EXPORTS_PROPERTY_NAME);
            log("returning cached '", modulePath, cached);
            return cached;
        }
        // Read the file.
        Source source = sourceFromPath(modulePath.toString(), realm);
        TruffleString filenameBuiltin = Strings.fromJavaString(normalizedPath.toString());
        if (modulePath.getParent() == null) {
            throw fail(moduleIdentifier);
        }
        // Create `require` and other builtins for this module.
        TruffleString dirnameBuiltin = Strings.fromJavaString(modulePath.getParent().getAbsoluteFile().normalize().toString());
        JSObject exportsBuiltin = createExportsBuiltin(realm);
        JSObject moduleBuiltin = createModuleBuiltin(realm, exportsBuiltin, filenameBuiltin);
        JSObject requireBuiltin = createRequireBuiltin(realm, moduleBuiltin, filenameBuiltin);
        JSObject env = JSOrdinary.create(getContext(), getRealm());
        JSObject.set(env, Strings.ENV_PROPERTY_NAME, JSOrdinary.create(getContext(), getRealm()));
        // Parse the module
        CharSequence characters = MODULE_PREAMBLE + source.getCharacters() + MODULE_END;
        Source moduleSources = Source.newBuilder(JavaScriptLanguage.ID, characters, Strings.toJavaString(filenameBuiltin)).mimeType(JavaScriptLanguage.TEXT_MIME_TYPE).build();
        CallTarget moduleCallTarget = realm.getEnv().parsePublic(moduleSources);
        Object moduleExecutableFunction = moduleCallTarget.call();
        // Execute the module.
        if (JSFunction.isJSFunction(moduleExecutableFunction)) {
            commonJSCache.put(normalizedPath, moduleBuiltin);
            try {
                debugStackPush(moduleIdentifier);
                log("executing '", filenameBuiltin, "' for ", moduleIdentifier);
                JSFunction.call(JSArguments.create(moduleExecutableFunction, moduleExecutableFunction, exportsBuiltin, requireBuiltin, moduleBuiltin, filenameBuiltin, dirnameBuiltin, env));
                JSObject.set(moduleBuiltin, Strings.LOADED_PROPERTY_NAME, true);
                return JSObject.get(moduleBuiltin, Strings.EXPORTS_PROPERTY_NAME);
            } catch (Exception e) {
                log("EXCEPTION: '", e.getMessage(), "'");
                throw e;
            } finally {
                debugStackPop();
                Object module = JSObject.get(moduleBuiltin, Strings.EXPORTS_PROPERTY_NAME);
                log("done '", moduleIdentifier, "' module.exports: ", module, module);
            }
        }
        return null;
    }

    private JSDynamicObject evalJsonFile(TruffleFile jsonFile) {
        try {
            if (fileExists(jsonFile)) {
                Source source;
                JSRealm realm = getRealm();
                TruffleFile file = GlobalBuiltins.resolveRelativeFilePath(jsonFile.toString(), realm.getEnv());
                if (file.isRegularFile()) {
                    source = sourceFromTruffleFile(file);
                } else {
                    throw fail(Strings.fromJavaString(jsonFile.toString()));
                }
                JSFunctionObject parse = (JSFunctionObject) realm.getJsonParseFunctionObject();
                assert source != null;
                TruffleString jsonString = Strings.fromJavaString(source.getCharacters().toString());
                Object jsonObj = JSFunction.call(JSArguments.create(parse, parse, jsonString));
                if (JSDynamicObject.isJSDynamicObject(jsonObj)) {
                    return (JSDynamicObject) jsonObj;
                }
            }
            throw fail(Strings.fromJavaString(jsonFile.toString()));
        } catch (SecurityException e) {
            throw Errors.createErrorFromException(e);
        }
    }

    private static JSException fail(TruffleString moduleIdentifier) {
        return JSException.create(JSErrorType.TypeError, "Cannot load CommonJS module: '" + moduleIdentifier + "'");
    }

    private static JSException fail(TruffleString moduleIdentifier, TruffleString extraMessage) {
        return JSException.create(JSErrorType.TypeError, "Cannot load CommonJS module: '" + moduleIdentifier + "': " + extraMessage);
    }

    @TruffleBoundary
    private static JSException fail(TruffleString... message) {
        StringBuilder sb = new StringBuilder();
        for (TruffleString s : message) {
            sb.append(s);
        }
        return JSException.create(JSErrorType.TypeError, sb.toString());
    }

    private static JSObject createModuleBuiltin(JSRealm realm, JSDynamicObject exportsBuiltin, TruffleString fileNameBuiltin) {
        JSObject module = JSOrdinary.create(realm.getContext(), realm);
        JSObject.set(module, Strings.EXPORTS_PROPERTY_NAME, exportsBuiltin);
        JSObject.set(module, Strings.ID_PROPERTY_NAME, fileNameBuiltin);
        JSObject.set(module, Strings.FILENAME_PROPERTY_NAME, fileNameBuiltin);
        JSObject.set(module, Strings.LOADED_PROPERTY_NAME, false);
        return module;
    }

    private static JSObject createRequireBuiltin(JSRealm realm, JSDynamicObject moduleBuiltin, TruffleString fileNameBuiltin) {
        JSFunctionObject mainRequire = (JSFunctionObject) realm.getCommonJSRequireFunctionObject();
        Object mainResolve = JSObject.get(mainRequire, Strings.RESOLVE_PROPERTY_NAME);
        JSFunctionData functionData = JSFunction.getFunctionData(mainRequire);
        JSObject newRequire = JSFunction.create(realm, functionData);
        JSObject.set(newRequire, Strings.MODULE_PROPERTY_NAME, moduleBuiltin);
        JSObject.set(newRequire, Strings.RESOLVE_PROPERTY_NAME, mainResolve);
        // XXX(db) Here, we store the current filename in the (new) require builtin.
        // In this way, we avoid managing a shadow stack to track the current require's parent.
        // In Node.js, this is done using a (closed) level variable.
        JSObject.set(newRequire, Strings.FILENAME_VAR_NAME, fileNameBuiltin);
        return newRequire;
    }

    private static JSObject createExportsBuiltin(JSRealm realm) {
        return JSOrdinary.create(realm.getContext(), realm);
    }

    private static boolean isNodeBinFile(TruffleFile maybeModule) {
        return hasExtension(Objects.requireNonNull(maybeModule.getName()), ".node");
    }

    private static boolean isJsFile(TruffleFile maybeModule) {
        return hasExtension(Objects.requireNonNull(maybeModule.getName()), ".js");
    }

    private static boolean isJsonFile(TruffleFile maybeModule) {
        return hasExtension(Objects.requireNonNull(maybeModule.getName()), ".json");
    }

    private static boolean fileExists(TruffleFile modulePath) {
        return modulePath.isRegularFile();
    }

    private TruffleFile getModuleResolutionEntryPath(JSDynamicObject currentRequire, TruffleLanguage.Env env) {
        if (JSDynamicObject.isJSDynamicObject(currentRequire)) {
            Object maybeFilename = JSObject.get(currentRequire, Strings.FILENAME_VAR_NAME);
            if (Strings.isTString(maybeFilename)) {
                String fileName = Strings.toJavaString(JSRuntime.toStringIsString(maybeFilename));
                if (isFile(env, fileName)) {
                    return getParent(env, fileName);
                }
            }
            // dirname not a string. Use default cwd.
        }
        // This is not a nested `require()` call, so we use the default cwd.
        return getModuleResolveCurrentWorkingDirectory(getContext(), env);
    }

    private static TruffleFile getParent(TruffleLanguage.Env env, String fileName) {
        return env.getPublicTruffleFile(fileName).getParent();
    }

    private static boolean isFile(TruffleLanguage.Env env, String fileName) {
        return env.getPublicTruffleFile(fileName).exists();
    }

    private static boolean hasExtension(String fileName, String ext) {
        return fileName.endsWith(ext);
    }

}
