/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.nodes.module;

import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Executed;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.access.JSReadFrameSlotNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSFrameUtil;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.objects.Dead;
import com.oracle.truffle.js.runtime.objects.ExportResolution;
import com.oracle.truffle.js.runtime.objects.JSModuleRecord;
import com.oracle.truffle.js.runtime.objects.JSModuleRecord.Status;

/**
 * Reads the value of a resolved import binding from a resolved binding record (module, binding
 * name) returned by ResolveExport. Specializes on the imported module's FrameDescriptor.
 */
@ImportStatic(Strings.class)
public abstract class ReadImportBindingNode extends JavaScriptNode {

    @Child @Executed JavaScriptNode resolutionNode;

    ReadImportBindingNode(JavaScriptNode resolutionNode) {
        this.resolutionNode = resolutionNode;
    }

    public static JavaScriptNode create(JavaScriptNode resolutionNode) {
        return ReadImportBindingNodeGen.create(resolutionNode);
    }

    @Specialization(guards = {"frameDescriptor == resolution.getModule().getFrameDescriptor()", "equals(equalNode, bindingName, resolution.getBindingName())"}, limit = "1")
    static Object doCached(@SuppressWarnings("unused") ExportResolution resolutionParam,
                    @Bind("resolutionParam.asResolved()") ExportResolution resolution,
                    @Cached("resolution.getModule().getFrameDescriptor()") @SuppressWarnings("unused") FrameDescriptor frameDescriptor,
                    @Cached("resolution.getBindingName()") @SuppressWarnings("unused") TruffleString bindingName,
                    @Cached("create(frameDescriptor, findImportedSlotIndex(bindingName, resolution.getModule()))") JSReadFrameSlotNode readFrameSlot,
                    @Cached @SuppressWarnings("unused") TruffleString.EqualNode equalNode) {
        JSModuleRecord module = resolution.getModule();
        assert module.getStatus().compareTo(Status.Linked) >= 0 : module.getStatus();
        MaterializedFrame environment = JSFrameUtil.castMaterializedFrame(module.getEnvironment());
        Object value = readFrameSlot.execute(environment);
        assert value != Dead.instance();
        return value;
    }

    @TruffleBoundary
    @Specialization(replaces = {"doCached"})
    final Object doUncached(@SuppressWarnings("unused") ExportResolution resolutionParam,
                    @Bind("resolutionParam.asResolved()") ExportResolution resolution) {
        JSModuleRecord module = resolution.getModule();
        assert module.getStatus().compareTo(Status.Linked) >= 0 : module.getStatus();
        TruffleString bindingName = resolution.getBindingName();
        FrameDescriptor moduleFrameDescriptor = module.getFrameDescriptor();
        int slotIndex = findImportedSlotIndex(bindingName, module);
        boolean hasTemporalDeadZone = JSFrameUtil.hasTemporalDeadZone(moduleFrameDescriptor, slotIndex);
        MaterializedFrame environment = JSFrameUtil.castMaterializedFrame(module.getEnvironment());
        Object value = environment.getValue(slotIndex);
        if (hasTemporalDeadZone) {
            if (value == Dead.instance()) {
                // Uninitialized binding
                throw Errors.createReferenceErrorNotDefined(bindingName, this);
            }
        } else {
            assert value != Dead.instance();
        }
        return value;
    }

    static int findImportedSlotIndex(TruffleString bindingName, JSModuleRecord module) {
        return JSFrameUtil.findRequiredFrameSlotIndex(module.getFrameDescriptor(), bindingName);
    }

    @Specialization(guards = "isJSModuleNamespace(namespace)")
    static Object doNamespace(DynamicObject namespace) {
        return namespace;
    }

    @Override
    protected JavaScriptNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
        return create(cloneUninitialized(resolutionNode, materializedTags));
    }

}
