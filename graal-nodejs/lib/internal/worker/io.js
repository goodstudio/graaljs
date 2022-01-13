'use strict';

const {
  ArrayPrototypeForEach,
  ArrayPrototypeMap,
  ArrayPrototypePush,
  FunctionPrototypeBind,
  FunctionPrototypeCall,
  ObjectAssign,
  ObjectCreate,
  ObjectDefineProperty,
  ObjectDefineProperties,
  ObjectGetOwnPropertyDescriptors,
  ObjectGetPrototypeOf,
  ObjectSetPrototypeOf,
  ReflectApply,
  Symbol,
  SymbolFor,
} = primordials;

const {
  handle_onclose: handleOnCloseSymbol,
  oninit: onInitSymbol,
  no_message_symbol: noMessageSymbol
} = internalBinding('symbols');
const {
  MessagePort,
  MessageChannel,
  broadcastChannel,
  drainMessagePort,
  moveMessagePortToContext,
  receiveMessageOnPort: receiveMessageOnPort_,
  stopMessagePort,
  checkMessagePort,
  DOMException,
} = internalBinding('messaging');
const {
  getEnvMessagePort
} = internalBinding('worker');

const { Readable, Writable } = require('stream');
const {
  Event,
  EventTarget,
  NodeEventTarget,
  defineEventHandler,
  initNodeEventTarget,
  kCreateEvent,
  kNewListener,
  kRemoveListener,
} = require('internal/event_target');
const { inspect } = require('internal/util/inspect');
const {
  codes: {
    ERR_INVALID_ARG_TYPE,
    ERR_INVALID_THIS,
    ERR_MISSING_ARGS,
  }
} = require('internal/errors');

const kData = Symbol('kData');
const kHandle = Symbol('kHandle');
const kIncrementsPortRef = Symbol('kIncrementsPortRef');
const kLastEventId = Symbol('kLastEventId');
const kName = Symbol('kName');
const kOrigin = Symbol('kOrigin');
const kOnMessage = Symbol('kOnMessage');
const kOnMessageError = Symbol('kOnMessageError');
const kPort = Symbol('kPort');
const kPorts = Symbol('kPorts');
const kWaitingStreams = Symbol('kWaitingStreams');
const kWritableCallbacks = Symbol('kWritableCallbacks');
const kSource = Symbol('kSource');
const kStartedReading = Symbol('kStartedReading');
const kStdioWantsMoreDataCallback = Symbol('kStdioWantsMoreDataCallback');
const kCurrentlyReceivingPorts =
  SymbolFor('nodejs.internal.kCurrentlyReceivingPorts');
const kType = Symbol('kType');

const messageTypes = {
  UP_AND_RUNNING: 'upAndRunning',
  COULD_NOT_SERIALIZE_ERROR: 'couldNotSerializeError',
  ERROR_MESSAGE: 'errorMessage',
  STDIO_PAYLOAD: 'stdioPayload',
  STDIO_WANTS_MORE_DATA: 'stdioWantsMoreData',
  LOAD_SCRIPT: 'loadScript'
};

// We have to mess with the MessagePort prototype a bit, so that a) we can make
// it inherit from NodeEventTarget, even though it is a C++ class, and b) we do
// not provide methods that are not present in the Browser and not documented
// on our side (e.g. hasRef).
// Save a copy of the original set of methods as a shallow clone.
const MessagePortPrototype = ObjectCreate(
  ObjectGetPrototypeOf(MessagePort.prototype),
  ObjectGetOwnPropertyDescriptors(MessagePort.prototype));
// Set up the new inheritance chain.
ObjectSetPrototypeOf(MessagePort, NodeEventTarget);
ObjectSetPrototypeOf(MessagePort.prototype, NodeEventTarget.prototype);
// Copy methods that are inherited from HandleWrap, because
// changing the prototype of MessagePort.prototype implicitly removed them.
MessagePort.prototype.ref = MessagePortPrototype.ref;
MessagePort.prototype.unref = MessagePortPrototype.unref;

function validateMessagePort(port, name) {
  if (!checkMessagePort(port))
    throw new ERR_INVALID_ARG_TYPE(name, 'MessagePort', port);
}

function isMessageEvent(value) {
  return value != null && kData in value;
}

class MessageEvent extends Event {
  constructor(type, {
    data = null,
    origin = '',
    lastEventId = '',
    source = null,
    ports = [],
  } = {}) {
    super(type);
    this[kData] = data;
    this[kOrigin] = `${origin}`;
    this[kLastEventId] = `${lastEventId}`;
    this[kSource] = source;
    this[kPorts] = [...ports];

    if (this[kSource] !== null)
      validateMessagePort(this[kSource], 'init.source');
    for (let i = 0; i < this[kPorts].length; i++)
      validateMessagePort(this[kPorts][i], `init.ports[${i}]`);
  }
}

ObjectDefineProperties(MessageEvent.prototype, {
  data: {
    get() {
      if (!isMessageEvent(this))
        throw new ERR_INVALID_THIS('MessageEvent');
      return this[kData];
    },
    enumerable: true,
    configurable: true,
    set: undefined,
  },
  origin: {
    get() {
      if (!isMessageEvent(this))
        throw new ERR_INVALID_THIS('MessageEvent');
      return this[kOrigin];
    },
    enumerable: true,
    configurable: true,
    set: undefined,
  },
  lastEventId: {
    get() {
      if (!isMessageEvent(this))
        throw new ERR_INVALID_THIS('MessageEvent');
      return this[kLastEventId];
    },
    enumerable: true,
    configurable: true,
    set: undefined,
  },
  source: {
    get() {
      if (!isMessageEvent(this))
        throw new ERR_INVALID_THIS('MessageEvent');
      return this[kSource];
    },
    enumerable: true,
    configurable: true,
    set: undefined,
  },
  ports: {
    get() {
      if (!isMessageEvent(this))
        throw new ERR_INVALID_THIS('MessageEvent');
      return this[kPorts];
    },
    enumerable: true,
    configurable: true,
    set: undefined,
  },
});

const originalCreateEvent = EventTarget.prototype[kCreateEvent];
ObjectDefineProperty(
  MessagePort.prototype,
  kCreateEvent,
  {
    value: function(data, type) {
      if (type !== 'message' && type !== 'messageerror') {
        return ReflectApply(originalCreateEvent, this, arguments);
      }
      const ports = this[kCurrentlyReceivingPorts];
      this[kCurrentlyReceivingPorts] = undefined;
      return new MessageEvent(type, { data, ports });
    },
    configurable: false,
    writable: false,
    enumerable: false,
  });

// This is called from inside the `MessagePort` constructor.
function oninit() {
  initNodeEventTarget(this);
  // Graal.js: initialize support for Java objects in messages
  this.sharedMemMessaging = SharedMemMessagingInit();
  setupPortReferencing(this, this, 'message');
  this[kCurrentlyReceivingPorts] = undefined;
}

defineEventHandler(MessagePort.prototype, 'message');
defineEventHandler(MessagePort.prototype, 'messageerror');

ObjectDefineProperty(MessagePort.prototype, onInitSymbol, {
  enumerable: true,
  writable: false,
  value: oninit
});

class MessagePortCloseEvent extends Event {
  constructor() {
    super('close');
  }
}

// This is called after the underlying `uv_async_t` has been closed.
function onclose() {
  // Graal.js: the underlying `uv_async_t` has been closed, so we can
  // discard all pending Java references bound to this port (if any),
  // since the connection is lost and the other side of the channel
  // will not process messages.
  const messagePortData = getMessagePortDataNative.call(this);
  if (messagePortData !== undefined) {
    this.sharedMemMessaging.dispose(messagePortData);
  }
  this.dispatchEvent(new MessagePortCloseEvent());
}

ObjectDefineProperty(MessagePort.prototype, handleOnCloseSymbol, {
  enumerable: false,
  writable: false,
  value: onclose
});

MessagePort.prototype.close = function(cb) {
  if (typeof cb === 'function')
    this.once('close', cb);
  FunctionPrototypeCall(MessagePortPrototype.close, this);
};

ObjectDefineProperty(MessagePort.prototype, inspect.custom, {
  enumerable: false,
  writable: false,
  value: function inspect() {  // eslint-disable-line func-name-matching
    let ref;
    try {
      // This may throw when `this` does not refer to a native object,
      // e.g. when accessing the prototype directly.
      ref = FunctionPrototypeCall(MessagePortPrototype.hasRef, this);
    } catch { return this; }
    return ObjectAssign(ObjectCreate(MessagePort.prototype),
                        ref === undefined ? {
                          active: false,
                        } : {
                          active: true,
                          refed: ref
                        },
                        this);
  }
});

function setupPortReferencing(port, eventEmitter, eventName) {
  // Keep track of whether there are any workerMessage listeners:
  // If there are some, ref() the channel so it keeps the event loop alive.
  // If there are none or all are removed, unref() the channel so the worker
  // can shutdown gracefully.
  port.unref();
  eventEmitter.on('newListener', function(name) {
    if (name === eventName) newListener(eventEmitter.listenerCount(name));
  });
  eventEmitter.on('removeListener', function(name) {
    if (name === eventName) removeListener(eventEmitter.listenerCount(name));
  });
  const origNewListener = eventEmitter[kNewListener];
  eventEmitter[kNewListener] = function(size, type, ...args) {
    if (type === eventName) newListener(size - 1);
    return ReflectApply(origNewListener, this, arguments);
  };
  const origRemoveListener = eventEmitter[kRemoveListener];
  eventEmitter[kRemoveListener] = function(size, type, ...args) {
    if (type === eventName) removeListener(size);
    return ReflectApply(origRemoveListener, this, arguments);
  };

  function newListener(size) {
    if (size === 0) {
      port.ref();
      FunctionPrototypeCall(MessagePortPrototype.start, port);
    }
  }

  function removeListener(size) {
    if (size === 0) {
      stopMessagePort(port);
      port.unref();
    }
  }
}


class ReadableWorkerStdio extends Readable {
  constructor(port, name) {
    super();
    this[kPort] = port;
    this[kName] = name;
    this[kIncrementsPortRef] = true;
    this[kStartedReading] = false;
    this.on('end', () => {
      if (this[kStartedReading] && this[kIncrementsPortRef]) {
        if (--this[kPort][kWaitingStreams] === 0)
          this[kPort].unref();
      }
    });
  }

  _read() {
    if (!this[kStartedReading] && this[kIncrementsPortRef]) {
      this[kStartedReading] = true;
      if (this[kPort][kWaitingStreams]++ === 0)
        this[kPort].ref();
    }

    this[kPort].postMessage({
      type: messageTypes.STDIO_WANTS_MORE_DATA,
      stream: this[kName]
    });
  }
}

class WritableWorkerStdio extends Writable {
  constructor(port, name) {
    super({ decodeStrings: false });
    this[kPort] = port;
    this[kName] = name;
    this[kWritableCallbacks] = [];
  }

  _writev(chunks, cb) {
    this[kPort].postMessage({
      type: messageTypes.STDIO_PAYLOAD,
      stream: this[kName],
      chunks: ArrayPrototypeMap(chunks,
                                ({ chunk, encoding }) => ({ chunk, encoding })),
    });
    ArrayPrototypePush(this[kWritableCallbacks], cb);
    if (this[kPort][kWaitingStreams]++ === 0)
      this[kPort].ref();
  }

  _final(cb) {
    this[kPort].postMessage({
      type: messageTypes.STDIO_PAYLOAD,
      stream: this[kName],
      chunks: [ { chunk: null, encoding: '' } ]
    });
    cb();
  }

  [kStdioWantsMoreDataCallback]() {
    const cbs = this[kWritableCallbacks];
    this[kWritableCallbacks] = [];
    ArrayPrototypeForEach(cbs, (cb) => cb());
    if ((this[kPort][kWaitingStreams] -= cbs.length) === 0)
      this[kPort].unref();
  }
}

function createWorkerStdio() {
  const port = getEnvMessagePort();
  port[kWaitingStreams] = 0;
  return {
    stdin: new ReadableWorkerStdio(port, 'stdin'),
    stdout: new WritableWorkerStdio(port, 'stdout'),
    stderr: new WritableWorkerStdio(port, 'stderr')
  };
}

function receiveMessageOnPort(port) {
  const message = receiveMessageOnPort_(port?.[kHandle] ?? port);
  if (message === noMessageSymbol) return undefined;
  return { message };
}

function onMessageEvent(type, data) {
  this.dispatchEvent(new MessageEvent(type, { data }));
}

function isBroadcastChannel(value) {
  return value?.[kType] === 'BroadcastChannel';
}

class BroadcastChannel extends EventTarget {
  constructor(name) {
    if (arguments.length === 0)
      throw new ERR_MISSING_ARGS('name');
    super();
    this[kType] = 'BroadcastChannel';
    this[kName] = `${name}`;
    this[kHandle] = broadcastChannel(this[kName]);
    this[kOnMessage] = FunctionPrototypeBind(onMessageEvent, this, 'message');
    this[kOnMessageError] =
      FunctionPrototypeBind(onMessageEvent, this, 'messageerror');
    this[kHandle].on('message', this[kOnMessage]);
    this[kHandle].on('messageerror', this[kOnMessageError]);
  }

  [inspect.custom](depth, options) {
    if (!isBroadcastChannel(this))
      throw new ERR_INVALID_THIS('BroadcastChannel');
    if (depth < 0)
      return 'BroadcastChannel';

    const opts = {
      ...options,
      depth: options.depth == null ? null : options.depth - 1
    };

    return `BroadcastChannel ${inspect({
      name: this[kName],
      active: this[kHandle] !== undefined,
    }, opts)}`;
  }

  get name() {
    if (!isBroadcastChannel(this))
      throw new ERR_INVALID_THIS('BroadcastChannel');
    return this[kName];
  }

  close() {
    if (!isBroadcastChannel(this))
      throw new ERR_INVALID_THIS('BroadcastChannel');
    if (this[kHandle] === undefined)
      return;
    this[kHandle].off('message', this[kOnMessage]);
    this[kHandle].off('messageerror', this[kOnMessageError]);
    this[kOnMessage] = undefined;
    this[kOnMessageError] = undefined;
    this[kHandle].close();
    this[kHandle] = undefined;
  }

  postMessage(message) {
    if (!isBroadcastChannel(this))
      throw new ERR_INVALID_THIS('BroadcastChannel');
    if (arguments.length === 0)
      throw new ERR_MISSING_ARGS('message');
    if (this[kHandle] === undefined)
      throw new DOMException('BroadcastChannel is closed.');
    if (this[kHandle].postMessage(message) === undefined)
      throw new DOMException('Message could not be posted.');
  }

  // The ref() method is Node.js specific and not part of the standard
  // BroadcastChannel API definition. Typically we shouldn't extend Web
  // Platform APIs with Node.js specific methods but ref and unref
  // are a bit special.
  ref() {
    if (!isBroadcastChannel(this))
      throw new ERR_INVALID_THIS('BroadcastChannel');
    if (this[kHandle])
      this[kHandle].ref();
    return this;
  }

  // The unref() method is Node.js specific and not part of the standard
  // BroadcastChannel API definition. Typically we shouldn't extend Web
  // Platform APIs with Node.js specific methods but ref and unref
  // are a bit special.
  unref() {
    if (!isBroadcastChannel(this))
      throw new ERR_INVALID_THIS('BroadcastChannel');
    if (this[kHandle])
      this[kHandle].unref();
    return this;
  }
}

const kEnumerableProperty = ObjectCreate(null);
kEnumerableProperty.enumerable = true;

ObjectDefineProperties(BroadcastChannel.prototype, {
  name: kEnumerableProperty,
  close: kEnumerableProperty,
  postMessage: kEnumerableProperty,
});

defineEventHandler(BroadcastChannel.prototype, 'message');
defineEventHandler(BroadcastChannel.prototype, 'messageerror');

module.exports = {
  drainMessagePort,
  messageTypes,
  kPort,
  kIncrementsPortRef,
  kWaitingStreams,
  kStdioWantsMoreDataCallback,
  moveMessagePortToContext,
  MessagePort,
  MessageChannel,
  MessageEvent,
  receiveMessageOnPort,
  setupPortReferencing,
  ReadableWorkerStdio,
  WritableWorkerStdio,
  createWorkerStdio,
  BroadcastChannel,
};

// ##### Graal.js Java interop messages handling

// Passed by Graal.js init phase during global module loading.
const SharedMemMessagingInit = typeof graalExtension === 'undefined' ? arguments[arguments.length - 1] : graalExtension;
if (!SharedMemMessagingInit) {
  throw new Error("Fatal: cannot initialize Worker");
}

const getMessagePortDataNative = MessagePort.prototype.messageData;
delete MessagePort.prototype.messageData;

const originalPostMessage = MessagePort.prototype.postMessage;
MessagePort.prototype.postMessage = function(...args) {
  const messagePortData = getMessagePortDataNative.call(this);
  if (messagePortData === undefined) {
    // Cannot retrieve message internal metadata. The channel is probably
    // closed, so we don't care about encoding Java messages.
    return originalPostMessage.apply(this, args);
  } else {
    try {
      // Signal that we are ready to transfer Java objets.
      this.sharedMemMessaging.enter(messagePortData);
      // Post message: might encode Java objects as a side effect.
      const enqueued = originalPostMessage.apply(this, args);
      const encodedJavaRefs = this.sharedMemMessaging.encodedJavaRefs();

      if (encodedJavaRefs === true && enqueued !== true) {
        // The message was not delivered to any worker threads.
        // In this case, we free any recorded Java reference, as the
        // message will anyway be discarded.
        this.sharedMemMessaging.free();
      }
      return enqueued;
    } finally {
      this.sharedMemMessaging.leave();
    }
  }
}
