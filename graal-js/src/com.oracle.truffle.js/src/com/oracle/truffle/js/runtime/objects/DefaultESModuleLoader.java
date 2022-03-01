/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.runtime.objects;

import com.oracle.js.parser.ir.Module.ModuleRequest;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSContextOptions;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.UserScriptException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DefaultESModuleLoader implements JSModuleLoader {

    private static final int JS_MODULE_TYPE = 1 << 0;
    private static final int JSON_MODULE_TYPE = 1 << 1;

    protected final JSRealm realm;
    protected final Map<TruffleString, JSModuleRecord> moduleMap = new HashMap<>();

    public static DefaultESModuleLoader create(JSRealm realm) {
        return new DefaultESModuleLoader(realm);
    }

    protected DefaultESModuleLoader(JSRealm realm) {
        this.realm = realm;
    }

    protected URI asURI(TruffleString specifier) {
        assert specifier != null;
        if (Strings.indexOf(specifier, ':') == -1) {
            return null;
        }
        try {
            URI uri = new URI(Strings.toJavaString(specifier));
            return uri.getScheme() != null ? uri : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    @Override
    public JSModuleRecord resolveImportedModule(ScriptOrModule referrer, ModuleRequest moduleRequest) {
        TruffleString refPath = referrer == null ? null : Strings.fromJavaString(referrer.getSource().getPath());
        try {
            TruffleString specifier = moduleRequest.getSpecifier();
            TruffleFile moduleFile;
            TruffleString canonicalPath;
            URI maybeUri = asURI(specifier);
            String specifierJavaString = Strings.toJavaString(specifier);

            TruffleString maybeCustomPath = realm.getCustomEsmPathMapping(refPath, specifier);
            if (maybeCustomPath != null) {
                canonicalPath = maybeCustomPath;
                moduleFile = realm.getEnv().getPublicTruffleFile(Strings.toJavaString(canonicalPath)).getCanonicalFile();
            } else {
                if (refPath == null) {
                    if (maybeUri != null) {
                        moduleFile = realm.getEnv().getPublicTruffleFile(maybeUri).getCanonicalFile();
                    } else {
                        moduleFile = realm.getEnv().getPublicTruffleFile(specifierJavaString).getCanonicalFile();
                    }
                } else {
                    TruffleFile refFile = realm.getEnv().getPublicTruffleFile(Strings.toJavaString(refPath));
                    if (maybeUri != null) {
                        String uriFile = realm.getEnv().getPublicTruffleFile(maybeUri).getCanonicalFile().getPath();
                        moduleFile = refFile.resolveSibling(uriFile).getCanonicalFile();
                    } else {
                        if (bareSpecifierDirectLookup(specifier)) {
                            moduleFile = realm.getEnv().getPublicTruffleFile(specifierJavaString).getCanonicalFile();
                        } else {
                            moduleFile = refFile.resolveSibling(specifierJavaString).getCanonicalFile();
                        }
                    }
                }
                canonicalPath = Strings.fromJavaString(moduleFile.getPath());
            }
            return loadModuleFromUrl(referrer, moduleRequest, moduleFile, canonicalPath);
        } catch (FileSystemException fsex) {
            String fileName = fsex.getFile();
            if (Objects.equals(fsex.getMessage(), fileName)) {
                String message = "Error reading: " + fileName;
                if (realm.getContext().isOptionV8CompatibilityMode()) {
                    // d8 throws string. We don't want to follow this bad practice outside V8
                    // compatibility mode.
                    throw UserScriptException.create(Strings.fromJavaString(message));
                } else {
                    throw Errors.createError(message);
                }
            } else {
                // Use the original message when it doesn't seem useless
                throw Errors.createErrorFromException(fsex);
            }
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            throw Errors.createErrorFromException(e);
        }
    }

    private boolean bareSpecifierDirectLookup(TruffleString specifier) {
        JSContextOptions options = realm.getContext().getContextOptions();
        if (options.isEsmBareSpecifierRelativeLookup()) {
            return false;
        }
        return !(Strings.startsWith(specifier, Strings.SLASH) || Strings.startsWith(specifier, Strings.DOT_SLASH) || Strings.startsWith(specifier, Strings.DOT_DOT_SLASH));
    }

    protected JSModuleRecord loadModuleFromUrl(ScriptOrModule referrer, ModuleRequest moduleRequest, TruffleFile moduleFile, TruffleString canonicalPath) throws IOException {
        JSModuleRecord existingModule = moduleMap.get(canonicalPath);
        if (existingModule != null) {
            return existingModule;
        }
        Source source = Source.newBuilder(JavaScriptLanguage.ID, moduleFile).name(Strings.toJavaString(moduleRequest.getSpecifier())).mimeType(JavaScriptLanguage.MODULE_MIME_TYPE).build();
        Map<TruffleString, TruffleString> assertions = moduleRequest.getAssertions();
        int moduleType = getModuleType(moduleFile.getName());
        TruffleString assertedType = assertions.get(JSContext.getTypeImportAssertion());
        if (!doesModuleTypeMatchAssertionType(assertedType, moduleType)) {
            throw Errors.createTypeError("Invalid module type was asserted");
        }
        JSModuleRecord newModule;
        if (isModuleType(moduleType, JSON_MODULE_TYPE)) {
            newModule = realm.getContext().getEvaluator().parseJSONModule(realm, source);
        } else {
            JSModuleData parsedModule = realm.getContext().getEvaluator().envParseModule(realm, source);
            newModule = new JSModuleRecord(parsedModule, this);
        }
        moduleMap.put(canonicalPath, newModule);

        if (referrer instanceof JSModuleRecord) {
            ((JSModuleRecord) referrer).getModuleData().rememberImportedModuleSource(moduleRequest.getSpecifier(), source);
        }
        return newModule;
    }

    private static boolean doesModuleTypeMatchAssertionType(TruffleString assertedType, int moduleType) {
        if (assertedType == null) {
            return true;
        }
        if (Strings.equals(Strings.JSON, assertedType)) {
            return isModuleType(moduleType, JSON_MODULE_TYPE);
        }
        return false;
    }

    private int getModuleType(String moduleName) {
        if (realm.getContext().getContextOptions().isJsonModules() && moduleName.endsWith(JavaScriptLanguage.JSON_SOURCE_NAME_SUFFIX)) {
            return JSON_MODULE_TYPE;
        }
        return JS_MODULE_TYPE;
    }

    private static boolean isModuleType(int moduleType, int expectedType) {
        return (moduleType & expectedType) != 0;
    }

    @Override
    public JSModuleRecord loadModule(Source source, JSModuleData moduleData) {
        TruffleString canonicalPath = getCanonicalPath(source);
        return moduleMap.computeIfAbsent(canonicalPath, (key) -> new JSModuleRecord(moduleData, this));
    }

    private TruffleString getCanonicalPath(Source source) {
        String path = source.getPath();
        String canonicalPath;
        if (path == null) {
            // Source does not originate from a file.
            canonicalPath = source.getName();
        } else {
            try {
                if (realm.getEnv().getFileNameSeparator().equals("\\") && path.startsWith("/")) {
                    // on Windows, remove first "/" from /c:/test/dir/ style paths
                    path = path.substring(1);
                }
                TruffleFile moduleFile = realm.getEnv().getPublicTruffleFile(path);
                if (moduleFile.exists()) {
                    canonicalPath = moduleFile.getCanonicalFile().getPath();
                } else {
                    // Source with a non-existing path but with a content.
                    canonicalPath = path;
                }
            } catch (IOException | SecurityException e) {
                throw Errors.createErrorFromException(e);
            }
        }
        return Strings.fromJavaString(canonicalPath);
    }
}
