/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.nodes.unary;

import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.runtime.BigInt;
import com.oracle.truffle.js.runtime.JSConfig;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.Symbol;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.builtins.JSFunctionObject;
import com.oracle.truffle.js.runtime.builtins.JSProxyObject;

/**
 * Represents abstract operation IsConstructor.
 *
 * @see JSRuntime#isConstructor(Object)
 */
@ImportStatic({JSConfig.class})
@GenerateUncached
public abstract class IsConstructorNode extends JavaScriptBaseNode {

    protected IsConstructorNode() {
    }

    public abstract boolean executeBoolean(Object operand);

    @Specialization
    protected static boolean doJSFunction(JSFunctionObject function) {
        return JSFunction.isConstructor(function);
    }

    @Specialization
    protected static boolean doJSProxy(JSProxyObject proxy) {
        return JSRuntime.isConstructorProxy(proxy);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"isJSDynamicObject(other)", "!isJSFunction(other)", "!isJSProxy(other)"})
    protected static boolean doOther(Object other) {
        return false;
    }

    @Specialization
    protected static boolean doString(@SuppressWarnings("unused") TruffleString string) {
        return false;
    }

    @Specialization
    protected static boolean doBoolean(@SuppressWarnings("unused") boolean value) {
        return false;
    }

    @Specialization
    protected static boolean doNumber(@SuppressWarnings("unused") Number number) {
        return false;
    }

    @Specialization
    protected static boolean doSymbol(@SuppressWarnings("unused") Symbol symbol) {
        return false;
    }

    @Specialization
    protected static boolean doBigInt(@SuppressWarnings("unused") BigInt bigInt) {
        return false;
    }

    @Specialization(guards = "isForeignObject(obj)", limit = "InteropLibraryLimit")
    protected static boolean doTruffleObject(Object obj,
                    @CachedLibrary("obj") InteropLibrary interop) {
        return interop.isInstantiable(obj);
    }

    @NeverDefault
    public static IsConstructorNode create() {
        return IsConstructorNodeGen.create();
    }
}
