/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringIterator;
import com.oracle.truffle.js.builtins.StringFunctionBuiltinsFactory.DedentTemplateStringsArrayNodeGen;
import com.oracle.truffle.js.builtins.StringFunctionBuiltinsFactory.JSFromCharCodeNodeGen;
import com.oracle.truffle.js.builtins.StringFunctionBuiltinsFactory.JSFromCodePointNodeGen;
import com.oracle.truffle.js.builtins.StringFunctionBuiltinsFactory.StringDedentNodeGen;
import com.oracle.truffle.js.builtins.StringFunctionBuiltinsFactory.StringRawNodeGen;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.nodes.access.PropertyGetNode;
import com.oracle.truffle.js.nodes.access.PropertySetNode;
import com.oracle.truffle.js.nodes.access.ReadElementNode;
import com.oracle.truffle.js.nodes.array.JSGetLengthNode;
import com.oracle.truffle.js.nodes.cast.JSToNumberNode;
import com.oracle.truffle.js.nodes.cast.JSToObjectNode;
import com.oracle.truffle.js.nodes.cast.JSToStringNode;
import com.oracle.truffle.js.nodes.cast.JSToUInt16Node;
import com.oracle.truffle.js.nodes.function.JSBuiltin;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.nodes.function.JSFunctionCallNode;
import com.oracle.truffle.js.runtime.Boundaries;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSArguments;
import com.oracle.truffle.js.runtime.JSConfig;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSFrameUtil;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.JavaScriptRootNode;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.builtins.BuiltinEnum;
import com.oracle.truffle.js.runtime.builtins.JSArray;
import com.oracle.truffle.js.runtime.builtins.JSArrayObject;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.builtins.JSFunctionData;
import com.oracle.truffle.js.runtime.builtins.JSFunctionObject;
import com.oracle.truffle.js.runtime.builtins.JSString;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.PropertyDescriptor;
import com.oracle.truffle.js.runtime.util.SimpleArrayList;

/**
 * Contains builtins for {@linkplain JSString} function (constructor).
 */
public final class StringFunctionBuiltins extends JSBuiltinsContainer.SwitchEnum<StringFunctionBuiltins.StringFunction> {

    public static final JSBuiltinsContainer BUILTINS = new StringFunctionBuiltins();

    protected StringFunctionBuiltins() {
        super(JSString.CLASS_NAME, StringFunction.class);
    }

    public enum StringFunction implements BuiltinEnum<StringFunction> {
        fromCharCode(1),

        // ES6
        fromCodePoint(1),
        raw(1),

        // staging
        dedent(1);

        private final int length;

        StringFunction(int length) {
            this.length = length;
        }

        @Override
        public int getLength() {
            return length;
        }

        @Override
        public int getECMAScriptVersion() {
            if (this == fromCodePoint) {
                return 6;
            } else if (this == dedent) {
                return JSConfig.StagingECMAScriptVersion;
            }
            return BuiltinEnum.super.getECMAScriptVersion();
        }
    }

    @Override
    protected Object createNode(JSContext context, JSBuiltin builtin, boolean construct, boolean newTarget, StringFunction builtinEnum) {
        switch (builtinEnum) {
            case fromCharCode:
                return JSFromCharCodeNodeGen.create(context, builtin, args().varArgs().createArgumentNodes(context));
            case fromCodePoint:
                return JSFromCodePointNodeGen.create(context, builtin, args().varArgs().createArgumentNodes(context));
            case raw:
                return StringRawNodeGen.create(context, builtin, args().fixedArgs(1).varArgs().createArgumentNodes(context));
            case dedent:
                return StringDedentNodeGen.create(context, builtin, args().fixedArgs(1).varArgs().createArgumentNodes(context));
        }
        return null;
    }

    public abstract static class JSFromCharCodeNode extends JSBuiltinNode {

        public JSFromCharCodeNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization(guards = "args.length == 0")
        protected Object fromCharCode(@SuppressWarnings("unused") Object[] args) {
            return Strings.EMPTY_STRING;
        }

        @Specialization(guards = "args.length == 1")
        protected Object fromCharCodeOneArg(Object[] args,
                        @Shared @Cached JSToUInt16Node toUint16,
                        @Cached TruffleString.FromCodePointNode fromCodePointNode) {
            return Strings.fromCodePoint(fromCodePointNode, toUint16.executeChar(args[0]));
        }

        @Specialization(guards = "args.length >= 2")
        protected Object fromCharCodeTwoOrMore(Object[] args,
                        @Shared @Cached JSToUInt16Node toUint16,
                        @Cached TruffleString.FromCharArrayUTF16Node fromCharArrayNode) {
            char[] chars = new char[args.length];
            for (int i = 0; i < args.length; i++) {
                chars[i] = toUint16.executeChar(args[i]);
            }
            return Strings.fromCharArray(fromCharArrayNode, chars);
        }
    }

    public abstract static class JSFromCodePointNode extends JSBuiltinNode {

        public JSFromCodePointNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Object fromCodePoint(Object[] args,
                        @Cached JSToNumberNode toNumberNode,
                        @Cached TruffleString.FromCodePointNode fromCodePointNode,
                        @Cached TruffleString.ConcatNode concatNode) {
            TruffleString st = Strings.EMPTY_STRING;
            for (Object arg : args) {
                Number value = toNumberNode.executeNumber(arg);
                double valueDouble = JSRuntime.doubleValue(value);
                int valueInt = JSRuntime.intValue(value);
                if (JSRuntime.isNegativeZero(valueDouble)) {
                    valueInt = 0;
                } else if (!JSRuntime.doubleIsRepresentableAsInt(valueDouble) || (valueInt < 0) || (0x10FFFF < valueInt)) {
                    throwRangeError(value);
                }
                st = Strings.concat(concatNode, st, Strings.fromCodePoint(fromCodePointNode, valueInt));
            }
            return st;
        }

        @TruffleBoundary
        private static void throwRangeError(Number value) {
            throw Errors.createRangeError("Invalid code point " + value);
        }
    }

    public abstract static class StringRawNode extends JSBuiltinNode {
        @Child private JSToObjectNode templateToObjectNode;
        @Child private JSToObjectNode rawToObjectNode;
        @Child private PropertyGetNode getRawNode;
        @Child private JSGetLengthNode getRawLengthNode;
        @Child private JSToStringNode segToStringNode;
        @Child private JSToStringNode subToStringNode;
        @Child private ReadElementNode readRawElementNode;
        @Child private TruffleStringBuilder.AppendStringNode appendStringNode;
        @Child private TruffleStringBuilder.ToStringNode builderToStringNode;

        public StringRawNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
            this.templateToObjectNode = JSToObjectNode.create();
            this.rawToObjectNode = JSToObjectNode.create();
            this.getRawNode = PropertyGetNode.create(Strings.RAW, false, context);
            this.getRawLengthNode = JSGetLengthNode.create(context);
            this.segToStringNode = JSToStringNode.create();
            this.subToStringNode = JSToStringNode.create();
            this.readRawElementNode = ReadElementNode.create(context);
            this.appendStringNode = TruffleStringBuilder.AppendStringNode.create();
            this.builderToStringNode = TruffleStringBuilder.ToStringNode.create();
        }

        @Specialization
        protected Object raw(Object template, Object[] substitutions,
                        @Cached InlinedConditionProfile emptyProf) {
            int numberOfSubstitutions = substitutions.length;
            Object cooked = templateToObjectNode.execute(template);
            Object raw = rawToObjectNode.execute(getRawNode.getValue(cooked));

            int literalSegments = getRawLength(raw);
            if (emptyProf.profile(this, literalSegments <= 0)) {
                return Strings.EMPTY_STRING;
            }

            TruffleStringBuilder result = Strings.builderCreate();
            for (int i = 0;; i++) {
                Object rawElement = readRawElementNode.executeWithTargetAndIndex(raw, i);
                TruffleString nextSeg = segToStringNode.executeString(rawElement);
                appendChecked(result, nextSeg);
                if (i + 1 == literalSegments) {
                    break;
                }
                if (i < numberOfSubstitutions) {
                    TruffleString nextSub = subToStringNode.executeString(substitutions[i]);
                    appendChecked(result, nextSub);
                }
            }
            return Strings.builderToString(builderToStringNode, result);
        }

        private int getRawLength(Object raw) {
            long length = getRawLengthNode.executeLong(raw);
            try {
                return Math.toIntExact(length);
            } catch (ArithmeticException e) {
                return 0;
            }
        }

        private void appendChecked(TruffleStringBuilder result, TruffleString str) {
            if (Strings.builderLength(result) + Strings.length(str) > getContext().getStringLengthLimit()) {
                CompilerDirectives.transferToInterpreter();
                throw Errors.createRangeErrorInvalidStringLength();
            }
            Strings.builderAppend(appendStringNode, result, str);
        }
    }

    @ImportStatic(StringDedentNode.class)
    public abstract static class StringDedentNode extends JSBuiltinNode {

        static final HiddenKey TAG_KEY = new HiddenKey("TagKey");

        public StringDedentNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization(guards = "isCallable(callback)")
        protected Object dedentCallback(Object callback, @SuppressWarnings("unused") Object[] substitutions,
                        @Cached("createSetHidden(TAG_KEY, getContext())") PropertySetNode setArgs) {
            JSFunctionData functionData = getContext().getOrCreateBuiltinFunctionData(JSContext.BuiltinFunctionKey.DedentCallback, (c) -> callbackBody(c));
            JSFunctionObject function = JSFunction.create(getRealm(), functionData);
            setArgs.setValue(function, callback);
            return function;
        }

        private static JSFunctionData callbackBody(JSContext context) {
            class CallbackBody extends JavaScriptRootNode {
                @Child private DedentTemplateStringsArrayNode dedentTemplateStringsArray = DedentTemplateStringsArrayNodeGen.create(context);
                @Child private PropertyGetNode getArgs = PropertyGetNode.createGetHidden(TAG_KEY, context);
                @Child private JSFunctionCallNode callResolve = JSFunctionCallNode.createCall();

                @Override
                public Object execute(VirtualFrame frame) {
                    JSDynamicObject functionObject = JSFrameUtil.getFunctionObject(frame);
                    Object tag = getArgs.getValue(functionObject);
                    Object r = JSFrameUtil.getThisObj(frame);

                    Object[] args = JSFrameUtil.getArgumentsArray(frame);
                    if (args.length < 1) {
                        throw Errors.createTypeError("Expected at least one argument");
                    }
                    Object template = args[0];
                    JSArrayObject dedentedArray = dedentTemplateStringsArray.execute(template);
                    Object[] callbackArgs = Arrays.copyOf(args, args.length);
                    callbackArgs[0] = dedentedArray;
                    return callResolve.executeCall(JSArguments.create(r, tag, callbackArgs));
                }
            }
            return JSFunctionData.createCallOnly(context, new CallbackBody().getCallTarget(), 2, Strings.EMPTY_STRING);
        }

        @Specialization(guards = "!isCallable(template)")
        protected Object dedent(Object template, Object[] substitutions,
                        @Cached("create(getContext())") DedentTemplateStringsArrayNode dedentTemplateStringsArray,
                        @Cached("create(getContext())") ReadElementNode readElementNode,
                        @Cached JSToStringNode subToStringNode,
                        @Cached InlinedBranchProfile errorBranch,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode) {
            int numberOfSubstitutions = substitutions.length;
            JSArrayObject dedentedArray = dedentTemplateStringsArray.execute(template);
            TruffleStringBuilder result = Strings.builderCreate();
            for (int i = 0; i < dedentedArray.getArraySize(); i++) {
                TruffleString nextSeg = (TruffleString) readElementNode.executeWithTargetAndIndex(dedentedArray, i);
                appendChecked(result, nextSeg, errorBranch, appendStringNode);
                if (i < numberOfSubstitutions) {
                    TruffleString nextSub = subToStringNode.executeString(substitutions[i]);
                    appendChecked(result, nextSub, errorBranch, appendStringNode);
                }
            }

            return Strings.builderToString(result);
        }

        private void appendChecked(TruffleStringBuilder result, TruffleString str,
                        InlinedBranchProfile errorBranch,
                        TruffleStringBuilder.AppendStringNode appendStringNode) {
            checkLengthAllowed(Strings.builderLength(result) + Strings.length(str), errorBranch);
            Strings.builderAppend(appendStringNode, result, str);
        }

        private void checkLengthAllowed(int length,
                        InlinedBranchProfile errorBranch) {
            if (length > getContext().getStringLengthLimit()) {
                errorBranch.enter(this);
                throw Errors.createRangeErrorInvalidStringLength();
            }
        }
    }

    public abstract static class DedentTemplateStringsArrayNode extends JavaScriptBaseNode {
        public static final String MISSING_START_NEWLINE_MESSAGE = "Template should contain a trailing newline.";
        public static final String MISSING_END_NEWLINE_MESSAGE = "Template should contain a closing newline.";

        @Child private PropertyGetNode getRawNode;
        @Child private JSGetLengthNode getLengthNode;
        @Child private JSToStringNode segToStringNode;
        @Child private ReadElementNode readRawElementNode;
        @Child private TruffleStringBuilder.AppendStringNode appendStringNode;
        private final JSContext context;

        DedentTemplateStringsArrayNode(JSContext context) {
            this.context = context;
            this.getLengthNode = JSGetLengthNode.create(context);
            this.getRawNode = PropertyGetNode.create(Strings.RAW, context);
            this.readRawElementNode = ReadElementNode.create(context);
            this.segToStringNode = JSToStringNode.create();
            this.appendStringNode = TruffleStringBuilder.AppendStringNode.create();
        }

        protected abstract JSArrayObject execute(Object template);

        @Specialization
        protected final JSArrayObject dedentTemplateStringsArray(Object template,
                        @Cached JSToObjectNode rawToObjectNode,
                        @Cached InlinedConditionProfile emptyProf,
                        @Cached InlinedBranchProfile errorBranch,
                        @Cached InlinedBranchProfile growBranch,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIterator) {
            Object rawInput = rawToObjectNode.execute(getRawNode.getValue(template));
            JSRealm realm = getRealm();
            Map<Object, JSDynamicObject> dedentMap = realm.getDedentMap();
            JSArrayObject cached = (JSArrayObject) Boundaries.mapGet(dedentMap, rawInput);
            if (cached != null) {
                return cached;
            }

            TruffleString[] dedentedList = dedentStringsArray(rawInput, emptyProf, errorBranch, growBranch);

            JSArrayObject rawArr = JSArray.createConstant(context, realm, dedentedList);
            JSArrayObject cookedArr = JSArray.createConstant(context, realm, cookStrings(dedentedList, createCodePointIterator));
            JSRuntime.definePropertyOrThrow(
                            cookedArr,
                            Strings.RAW,
                            PropertyDescriptor.createData(rawArr, false, false, false));
            JSObject.setIntegrityLevel(rawArr, true);
            JSObject.setIntegrityLevel(cookedArr, true);
            Boundaries.mapPut(dedentMap, rawInput, cookedArr);

            return cookedArr;
        }

        private TruffleString[] dedentStringsArray(Object template,
                        InlinedConditionProfile emptyProf,
                        InlinedBranchProfile errorBranch,
                        InlinedBranchProfile growBranch) {
            int literalSegments = getLength(template);
            if (emptyProf.profile(this, literalSegments <= 0)) {
                // Note: Well-formed template strings arrays always contain at least 1 string.
                throw Errors.createTypeError("Template raw array must contain at least 1 string");
            }

            SegmentRecord[][] blocks = splitTemplatesIntoBlockLines(template, literalSegments, errorBranch, growBranch);
            emptyWhiteSpaceLines(blocks);
            removeOpeningAndClosingLines(blocks);

            TruffleString indent = determineCommonLeadingIndentation(blocks);
            int indentLength = Strings.length(indent);

            TruffleString[] dedented = new TruffleString[blocks.length];
            for (int j = 0; j < blocks.length; j++) {
                SegmentRecord[] lines = blocks[j];
                TruffleStringBuilder partialResult = Strings.builderCreate();
                for (int i = 0; i < lines.length; i++) {
                    SegmentRecord line = lines[i];
                    int currentIndentation = i == 0 || Strings.isEmpty(line.substr) ? 0 : indentLength;
                    Strings.builderAppendLen(partialResult, line.substr, currentIndentation, Strings.length(line.substr) - currentIndentation);
                    Strings.builderAppend(appendStringNode, partialResult, line.newline);
                }
                dedented[j] = Strings.builderToString(partialResult);
            }

            return dedented;
        }

        private static final class SegmentRecord {
            TruffleString substr;
            TruffleString newline;
            boolean lineEndsWithSubstitution;

            SegmentRecord(TruffleString substr, TruffleString newline, boolean lineEndsWithSubstitution) {
                this.substr = substr;
                this.newline = newline;
                this.lineEndsWithSubstitution = lineEndsWithSubstitution;
            }
        }

        private SegmentRecord[][] splitTemplatesIntoBlockLines(Object raw, int len,
                        InlinedBranchProfile errorBranch,
                        InlinedBranchProfile growBranch) {
            SegmentRecord[][] blocks = new SegmentRecord[len][];
            int totalLength = 0;
            for (int k = 0; k < len; k++) {
                Object rawElement = readRawElementNode.executeWithTargetAndIndex(raw, k);
                TruffleString nextSeg = segToStringNode.executeString(rawElement);
                int segLength = Strings.length(nextSeg);
                totalLength += segLength;
                checkLengthAllowed(totalLength, errorBranch);
                int start = 0;
                SimpleArrayList<SegmentRecord> lines = new SimpleArrayList<>(segLength + 1);
                for (int i = 0; i < segLength;) {
                    char c = Strings.charAt(nextSeg, i);
                    int n = (c == '\r' && i + 1 < segLength && Strings.charAt(nextSeg, i + 1) == '\n') ? 2 : 1;
                    if (JSRuntime.isLineTerminator(c)) {
                        TruffleString substr = Strings.substring(context, nextSeg, start, i - start);
                        TruffleString newline = Strings.substring(context, nextSeg, i, n);
                        lines.add(new SegmentRecord(substr, newline, false), this, growBranch);
                        start = i + n;
                    }
                    i = i + n;
                }
                TruffleString tail = Strings.substring(context, nextSeg, start, segLength - start);
                boolean lineEndsWithSubstitution = k + 1 < len;
                lines.add(new SegmentRecord(tail, Strings.EMPTY_STRING, lineEndsWithSubstitution), this, growBranch);
                blocks[k] = lines.toArray(new SegmentRecord[0]);
            }
            return blocks;
        }

        private static void emptyWhiteSpaceLines(SegmentRecord[][] blocks) {
            for (SegmentRecord[] lines : blocks) {
                for (int i = 1; i < lines.length; i++) {
                    SegmentRecord line = lines[i];
                    if (!line.lineEndsWithSubstitution && onlyWhitespace(line.substr)) {
                        line.substr = Strings.EMPTY_STRING;
                    }
                }
            }
        }

        private static boolean onlyWhitespace(TruffleString str) {
            for (int i = 0; i < Strings.length(str); i++) {
                if (!isWhiteSpace(Strings.charAt(str, i))) {
                    return false;
                }
            }
            return true;
        }

        private static void removeOpeningAndClosingLines(SegmentRecord[][] blocks) {
            SegmentRecord[] firstBlock = blocks[0];
            if (firstBlock.length == 1 || !Strings.isEmpty(firstBlock[0].substr)) {
                throw Errors.createTypeError(MISSING_START_NEWLINE_MESSAGE);
            }
            firstBlock[0].newline = Strings.EMPTY_STRING;
            SegmentRecord[] lastBlock = blocks[blocks.length - 1];
            if (lastBlock.length == 1 || !Strings.isEmpty(lastBlock[lastBlock.length - 1].substr)) {
                throw Errors.createTypeError(MISSING_END_NEWLINE_MESSAGE);
            }
            SegmentRecord preceding = lastBlock[lastBlock.length - 2];
            lastBlock[lastBlock.length - 1].substr = Strings.EMPTY_STRING;
            preceding.newline = Strings.EMPTY_STRING;
        }

        private TruffleString determineCommonLeadingIndentation(SegmentRecord[][] blocks) {
            TruffleString indent = null;
            for (SegmentRecord[] lines : blocks) {
                for (int i = 1; i < lines.length; i++) {
                    SegmentRecord line = lines[i];
                    if (line.lineEndsWithSubstitution || !Strings.isEmpty(line.substr)) {
                        TruffleString leading = leadingWhitespaceSubstring(line.substr);
                        indent = indent == null ? leading : longestMatchingLeadingSubstring(indent, leading);
                    }
                }
            }
            return indent;
        }

        private TruffleString leadingWhitespaceSubstring(TruffleString str) {
            for (int i = 0; i < Strings.length(str); i++) {
                if (!isWhiteSpace(Strings.charAt(str, i))) {
                    return Strings.substring(context, str, 0, i);
                }
            }
            return str;
        }

        private static boolean isWhiteSpace(char c) {
            // according to the specification 0x2028 and 0x2029 aren't whitespaces
            return c != 0x2028 && c != 0x2029 && JSRuntime.isWhiteSpace(c);
        }

        private TruffleString longestMatchingLeadingSubstring(TruffleString strA, TruffleString strB) {
            int len = Math.min(Strings.length(strA), Strings.length(strB));
            for (int i = 0; i < len; i++) {
                if (Strings.charAt(strA, i) != Strings.charAt(strB, i)) {
                    return Strings.substring(context, strA, 0, i);
                }
            }
            return Strings.substring(context, strA, 0, len);
        }

        private static TruffleString[] cookStrings(TruffleString[] raw,
                        TruffleString.CreateCodePointIteratorNode createCodePointIterator) {
            TruffleString[] cooked = new TruffleString[raw.length];
            for (int i = 0; i < raw.length; i++) {
                TruffleString str = raw[i];
                TruffleStringIterator iterator = createCodePointIterator.execute(str, TruffleString.Encoding.UTF_16);
                cooked[i] = parseText(iterator);
            }
            return cooked;
        }

        private static TruffleString parseText(TruffleStringIterator iterator) {
            TruffleStringBuilder partialResult = Strings.builderCreate();
            while (iterator.hasNext()) {
                int ch = iterator.nextUncached();
                if (ch == '\\' && iterator.hasNext()) {
                    final int next = iterator.nextUncached();
                    // Special characters.
                    switch (next) {
                        case 'n':
                            Strings.builderAppend(partialResult, '\n');
                            break;
                        case 't':
                            Strings.builderAppend(partialResult, '\t');
                            break;
                        case 'b':
                            Strings.builderAppend(partialResult, '\b');
                            break;
                        case 'f':
                            Strings.builderAppend(partialResult, '\f');
                            break;
                        case 'r':
                            Strings.builderAppend(partialResult, '\r');
                            break;
                        case '\'':
                            Strings.builderAppend(partialResult, '\'');
                            break;
                        case '\"':
                            Strings.builderAppend(partialResult, '\"');
                            break;
                        case '\\':
                            Strings.builderAppend(partialResult, '\\');
                            break;
                        case '\r': // CR | CRLF
                            if (iterator.nextUncached() != '\n') {
                                iterator.previousUncached();
                            }
                            break;
                        case '\n':
                        case '\u2028':
                        case '\u2029':
                            break;
                        case 'x': {
                            // Hex sequence.
                            final int asciiCh = hexSequence(iterator, 2);
                            Strings.builderAppend(partialResult, (char) asciiCh);
                            break;
                        }
                        case 'u': {
                            final int unicodeChar = unicodeEscapeSequence(iterator);
                            if (unicodeChar < 0) {
                                Strings.builderAppend(partialResult, "\\u");
                            } else if (unicodeChar <= 0xffff && Character.isSurrogate((char) unicodeChar)) {
                                Strings.builderAppend(partialResult, (char) unicodeChar);
                            } else {
                                Strings.builderAppend(partialResult, Strings.fromCodePoint(unicodeChar));
                            }
                            break;
                        }
                        default:
                            if (next <= 0xffff && Character.isSurrogate((char) next)) {
                                Strings.builderAppend(partialResult, (char) next);
                            } else {
                                Strings.builderAppend(partialResult, Strings.fromCodePoint(next));
                            }
                            break;
                    }
                } else {
                    if (ch <= 0xffff && Character.isSurrogate((char) ch)) {
                        Strings.builderAppend(partialResult, (char) ch);
                    } else {
                        Strings.builderAppend(partialResult, Strings.fromCodePoint(ch));
                    }
                }
            }
            return Strings.builderToString(partialResult);
        }

        private static int unicodeEscapeSequence(TruffleStringIterator iterator) {
            int ch = iterator.nextUncached();
            iterator.previousUncached();
            if (ch == '{') {
                return varlenHexSequence(iterator);
            } else {
                return hexSequence(iterator, 4);
            }
        }

        private static int varlenHexSequence(TruffleStringIterator iterator) {
            int ch = iterator.nextUncached();
            assert ch == '{';

            int value = 0;
            boolean firstIteration = true;
            while (iterator.hasNext()) {
                ch = iterator.nextUncached();
                if (ch == '}') {
                    if (!firstIteration) {
                        break;
                    } else {
                        throw Errors.createSyntaxError("Invalid Unicode escape sequence");
                    }
                }

                final int digit = convertDigit(ch, 16);

                if (digit == -1) {
                    throw Errors.createSyntaxError("Invalid Unicode escape sequence");
                }

                value = digit | value << 4;

                if (value > 1114111) {
                    throw Errors.createSyntaxError("Invalid Unicode escape sequence");
                }
                firstIteration = false;
            }

            return value;
        }

        private static int hexSequence(TruffleStringIterator iterator, int length) {
            int value = 0;
            int i;
            for (i = 0; i < length && iterator.hasNext(); i++) {
                int ch = iterator.nextUncached();
                int digit = convertDigit(ch, 16);
                if (digit == -1) {
                    throw Errors.createSyntaxError("Invalid hex digit");
                }
                value = digit | value << 4;
            }

            if (i != length) {
                throw Errors.createSyntaxError("Invalid hex length");
            }

            return value;
        }

        protected static int convertDigit(final int ch, final int base) {
            int digit;

            if ('0' <= ch && ch <= '9') {
                digit = ch - '0';
            } else if ('A' <= ch && ch <= 'Z') {
                digit = ch - 'A' + 10;
            } else if ('a' <= ch && ch <= 'z') {
                digit = ch - 'a' + 10;
            } else {
                return -1;
            }

            return digit < base ? digit : -1;
        }

        private int getLength(Object raw) {
            long length = getLengthNode.executeLong(raw);
            try {
                return Math.toIntExact(length);
            } catch (ArithmeticException e) {
                return 0;
            }
        }

        private void checkLengthAllowed(int length, InlinedBranchProfile errorBranch) {
            if (length > context.getStringLengthLimit()) {
                errorBranch.enter(this);
                throw Errors.createRangeErrorInvalidStringLength();
            }
        }
    }
}
