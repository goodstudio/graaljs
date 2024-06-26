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
package com.oracle.truffle.js.builtins.helper;

import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.runtime.BigInt;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSConfig;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.Symbol;
import com.oracle.truffle.js.runtime.builtins.JSSet;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.Null;

/**
 * This implements behavior for Collections of ES6. Instead of adhering to the SameValueNull
 * algorithm, we normalize the key (e.g., transform the double value 1.0 to an integer value of 1).
 */
@ImportStatic({JSConfig.class})
@GenerateUncached
public abstract class JSCollectionsNormalizeNode extends JavaScriptBaseNode {

    public abstract Object execute(Object operand);

    @NeverDefault
    public static JSCollectionsNormalizeNode create() {
        return JSCollectionsNormalizeNodeGen.create();
    }

    @Specialization
    static int doInt(int value) {
        return value;
    }

    @Specialization
    static Object doDouble(double value) {
        return JSSet.normalizeDouble(value);
    }

    @Specialization
    static TruffleString doString(TruffleString value) {
        return value;
    }

    @Specialization
    static boolean doBoolean(boolean value) {
        return value;
    }

    @Specialization
    static Object doDynamicObject(JSDynamicObject object) {
        return object;
    }

    @Specialization
    static Symbol doSymbol(Symbol value) {
        return value;
    }

    @Specialization
    static BigInt doBigInt(BigInt value) {
        return value;
    }

    @Specialization
    static Object doLong(long value) {
        if (JSRuntime.longFitsInDouble(value)) {
            return doDouble(value);
        } else {
            return BigInt.valueOf(value);
        }
    }

    @InliningCutoff
    @Specialization(guards = "isForeignObject(value)", limit = "InteropLibraryLimit")
    final Object doForeignObject(Object value,
                    @CachedLibrary("value") InteropLibrary interop) {
        if (interop.isNull(value)) {
            return Null.instance;
        }
        try {
            if (interop.isBoolean(value)) {
                return doBoolean(interop.asBoolean(value));
            } else if (interop.isString(value)) {
                return doString(interop.asTruffleString(value));
            } else if (interop.isNumber(value)) {
                if (interop.fitsInInt(value)) {
                    return doInt(interop.asInt(value));
                } else if (interop.fitsInDouble(value)) {
                    return doDouble(interop.asDouble(value));
                } else if (interop.fitsInLong(value)) {
                    return doLong(interop.asLong(value));
                } else if (interop.fitsInBigInteger(value)) {
                    return doBigInt(BigInt.fromBigInteger(interop.asBigInteger(value)));
                } else {
                    assert value instanceof TruffleObject;
                    return value;
                }
            } else {
                return value;
            }
        } catch (UnsupportedMessageException e) {
            throw Errors.createTypeErrorUnboxException(value, e, this);
        }
    }

}
