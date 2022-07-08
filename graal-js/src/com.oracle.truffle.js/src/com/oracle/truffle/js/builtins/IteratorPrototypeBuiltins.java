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
package com.oracle.truffle.js.builtins;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.js.nodes.access.IteratorCloseNode;
import com.oracle.truffle.js.nodes.access.IteratorStepNode;
import com.oracle.truffle.js.nodes.access.IteratorValueNode;
import com.oracle.truffle.js.nodes.cast.JSToBooleanNode;
import com.oracle.truffle.js.nodes.function.JSBuiltin;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.nodes.function.JSFunctionCallNode;
import com.oracle.truffle.js.nodes.unary.IsCallableNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSArguments;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.builtins.BuiltinEnum;
import com.oracle.truffle.js.runtime.builtins.JSArray;
import com.oracle.truffle.js.runtime.objects.IteratorRecord;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.util.SimpleArrayList;

/**
 * Contains builtins for {@linkplain JSArray}.prototype.
 */
public final class IteratorPrototypeBuiltins extends JSBuiltinsContainer.SwitchEnum<IteratorPrototypeBuiltins.IteratorPrototype> {

    public static final JSBuiltinsContainer BUILTINS = new IteratorPrototypeBuiltins();

    protected IteratorPrototypeBuiltins() {
        super(JSArray.PROTOTYPE_NAME, IteratorPrototype.class);
    }

    public enum IteratorPrototype implements BuiltinEnum<IteratorPrototype> {
        toArray(0),
        forEach(1),

        some(1),
        every(1),
        find(1),

        reduce(1);

        private final int length;

        IteratorPrototype(int length) {
            this.length = length;
        }

        @Override
        public int getLength() {
            return length;
        }

        @Override
        public int getECMAScriptVersion() {
            return BuiltinEnum.super.getECMAScriptVersion();
        }
    }

    @Override
    protected Object createNode(JSContext context, JSBuiltin builtin, boolean construct, boolean newTarget, IteratorPrototype builtinEnum) {
        switch (builtinEnum) {
            case toArray:
                return IteratorPrototypeBuiltinsFactory.IteratorToArrayNodeGen.create(context, builtin, args().withThis().varArgs().createArgumentNodes(context));
            case forEach:
                return IteratorPrototypeBuiltinsFactory.IteratorForEachNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case some:
                return IteratorPrototypeBuiltinsFactory.IteratorSomeNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case every:
                return IteratorPrototypeBuiltinsFactory.IteratorEveryNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case find:
                return IteratorPrototypeBuiltinsFactory.IteratorFindNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case reduce:
                return IteratorPrototypeBuiltinsFactory.IteratorReduceNodeGen.create(context, builtin, args().withThis().fixedArgs(1).varArgs().createArgumentNodes(context));
        }
        return null;
    }

    protected abstract static class IteratorWithCallableNode extends JSBuiltinNode {
        @Child protected IsCallableNode isCallableNode;
        @Child private IteratorFunctionBuiltins.GetIteratorDirectNode getIteratorDirectNode;
        @Child private IteratorStepNode iteratorStepNode;
        @Child private IteratorValueNode iteratorValueNode;
        @Child private JSFunctionCallNode callNode;
        @Child private IteratorCloseNode iteratorCloseNode;

        protected final Object CONTINUE = new Object();

        protected IteratorWithCallableNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);

            isCallableNode = IsCallableNode.create();
            getIteratorDirectNode = IteratorFunctionBuiltins.GetIteratorDirectNode.create(context);
            iteratorStepNode = IteratorStepNode.create(context);
            iteratorValueNode = IteratorValueNode.create(context);
            callNode = JSFunctionCallNode.createCall();
        }

        protected void prepare() {

        }

        protected Object end() {
            return Undefined.instance;
        }

        protected Object step(IteratorRecord iterated, Object fn, Object value) {
            return CONTINUE;
        }

        protected Object callMapper(IteratorRecord iterated, Object fn, Object value) {
            try {
                return callNode.executeCall(JSArguments.createOneArg(Undefined.instance, fn, value));
            } catch (AbstractTruffleException ex) {
                if (iteratorCloseNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    iteratorCloseNode = insert(IteratorCloseNode.create(getContext()));
                }
                iteratorCloseNode.executeAbrupt(iterated.getIterator());
                throw ex; // should be executed by iteratorClose
            }
        }

        @Specialization(guards = "isCallableNode.executeBoolean(fn)")
        protected Object compatible(Object thisObj, Object fn) {
            IteratorRecord iterated = getIteratorDirectNode.execute(thisObj);
            prepare();
            while (true) {
                Object next = iteratorStepNode.execute(iterated);
                if (next == (Boolean) false) {
                    return end();
                }

                Object value = iteratorValueNode.execute(next);
                Object result = step(iterated, fn, value);
                if (result != CONTINUE) {
                    if (iteratorCloseNode == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        iteratorCloseNode = insert(IteratorCloseNode.create(getContext()));
                    }
                    return iteratorCloseNode.execute(iterated.getIterator(), result);
                }
            }
        }

        @Specialization(guards = "!isCallableNode.executeBoolean(fn)")
        protected void incompatible(Object thisObj, Object fn) {
            getIteratorDirectNode.execute(thisObj);
            throw Errors.createTypeErrorNotAFunction(fn);
        }
    }

    public abstract static class IteratorToArrayNode extends JSBuiltinNode {
        @Child private IteratorFunctionBuiltins.GetIteratorDirectNode getIteratorDirectNode;
        @Child private IteratorStepNode iteratorStepNode;
        @Child private IteratorValueNode iteratorValueNode;
        private final BranchProfile growProfile = BranchProfile.create();

        protected IteratorToArrayNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);

            getIteratorDirectNode = IteratorFunctionBuiltins.GetIteratorDirectNode.create(context);
            iteratorStepNode = IteratorStepNode.create(context);
            iteratorValueNode = IteratorValueNode.create(context);
        }

        @Specialization
        protected JSDynamicObject toArray(Object thisObj) {
            IteratorRecord iterated = getIteratorDirectNode.execute(thisObj);
            SimpleArrayList<Object> items = new SimpleArrayList<>();

            while (true) {
                Object next = iteratorStepNode.execute(iterated);
                if (next == (Boolean) false) {
                    return JSArray.createConstant(getContext(), getRealm(), items.toArray());
                }

                Object value = iteratorValueNode.execute(next);
                items.add(value, growProfile);
            }
        }
    }

    public abstract static class IteratorForEachNode extends IteratorWithCallableNode {
        protected IteratorForEachNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Override
        protected Object step(IteratorRecord iterated, Object fn, Object value) {
            callMapper(iterated, fn, value);
            return CONTINUE;
        }
    }

    public abstract static class IteratorSomeNode extends IteratorWithCallableNode {
        @Child private JSToBooleanNode toBooleanNode;

        protected IteratorSomeNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);

            toBooleanNode = JSToBooleanNode.create();
        }

        @Override
        protected Object step(IteratorRecord iterated, Object fn, Object value) {
            if (toBooleanNode.executeBoolean(callMapper(iterated, fn, value))) {
                return true;
            }
            return CONTINUE;
        }

        @Override
        protected Object end() {
            return false;
        }
    }

    public abstract static class IteratorEveryNode extends IteratorWithCallableNode {
        @Child private JSToBooleanNode toBooleanNode;

        protected IteratorEveryNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);

            toBooleanNode = JSToBooleanNode.create();
        }

        @Override
        protected Object step(IteratorRecord iterated, Object fn, Object value) {
            if (!toBooleanNode.executeBoolean(callMapper(iterated, fn, value))) {
                return false;
            }
            return CONTINUE;
        }

        @Override
        protected Object end() {
            return true;
        }
    }

    public abstract static class IteratorFindNode extends IteratorWithCallableNode {
        @Child private JSToBooleanNode toBooleanNode;

        protected IteratorFindNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);

            toBooleanNode = JSToBooleanNode.create();
        }

        @Override
        protected Object step(IteratorRecord iterated, Object fn, Object value) {
            if (toBooleanNode.executeBoolean(callMapper(iterated, fn, value))) {
                return value;
            }
            return CONTINUE;
        }
    }

    public abstract static class IteratorReduceNode extends JSBuiltinNode {
        @Child protected IsCallableNode isCallableNode;
        @Child private IteratorFunctionBuiltins.GetIteratorDirectNode getIteratorDirectNode;
        @Child private IteratorStepNode iteratorStepNode;
        @Child private IteratorValueNode iteratorValueNode;
        @Child private JSFunctionCallNode callNode;
        @Child private IteratorCloseNode iteratorCloseNode;

        protected IteratorReduceNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);

            getIteratorDirectNode = IteratorFunctionBuiltins.GetIteratorDirectNode.create(context);
            isCallableNode = IsCallableNode.create();
            iteratorStepNode = IteratorStepNode.create(context);
            iteratorValueNode = IteratorValueNode.create(context);
            callNode = JSFunctionCallNode.createCall();
        }

        private Object callReducer(IteratorRecord iterated, Object reducer, Object accumulator, Object value) {
            try {
                return callNode.executeCall(JSArguments.create(Undefined.instance, reducer, accumulator, value));
            } catch (AbstractTruffleException ex) {
                if (iteratorCloseNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    iteratorCloseNode = insert(IteratorCloseNode.create(getContext()));
                }
                iteratorCloseNode.executeAbrupt(iterated.getIterator());
                throw ex; // should be executed by iteratorClose
            }
        }

        @Specialization(guards = "isCallableNode.executeBoolean(reducer)")
        protected Object reduce(Object thisObj, Object reducer, Object[] args) {
            IteratorRecord iterated = getIteratorDirectNode.execute(thisObj);

            Object initialValue = JSRuntime.getArgOrUndefined(args, 0);

            Object accumulator;
            if (initialValue == Undefined.instance) {
                Object next = iteratorStepNode.execute(iterated);
                if (next == (Boolean) false) {
                    throw Errors.createTypeError("Reduce of empty iterator with no initial value");
                }

                accumulator = iteratorValueNode.execute(next);
            } else {
                accumulator = initialValue;
            }

            while (true) {
                Object next = iteratorStepNode.execute(iterated);
                if (next == (Boolean) false) {
                    return accumulator;
                }
                Object value = iteratorValueNode.execute(next);
                accumulator = callReducer(iterated, reducer, accumulator, value);
            }
        }

        @Specialization(guards = "!isCallableNode.executeBoolean(reducer)")
        protected void incompatible(Object thisObj, Object reducer, Object[] args) {
            getIteratorDirectNode.execute(thisObj);
            throw Errors.createTypeErrorNotAFunction(reducer);
        }
    }
}
