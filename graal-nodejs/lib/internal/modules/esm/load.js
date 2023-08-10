'use strict';

const {
  ArrayPrototypePush,
  RegExpPrototypeExec,
  decodeURIComponent,
} = primordials;

const { defaultGetFormat } = require('internal/modules/esm/get_format');
const { validateAssertions } = require('internal/modules/esm/assert');
const { getOptionValue } = require('internal/options');
const { fetchModule } = require('internal/modules/esm/fetch_module');

// Do not eagerly grab .manifest, it may be in TDZ
const policy = getOptionValue('--experimental-policy') ?
  require('internal/process/policy') :
  null;
const experimentalNetworkImports =
  getOptionValue('--experimental-network-imports');

const { Buffer: { from: BufferFrom } } = require('buffer');

const { readFile: readFileAsync } = require('internal/fs/promises').exports;
const { URL } = require('internal/url');
const {
  ERR_INVALID_URL,
  ERR_UNSUPPORTED_ESM_URL_SCHEME,
} = require('internal/errors').codes;

const DATA_URL_PATTERN = /^[^/]+\/[^,;]+(?:[^,]*?)(;base64)?,([\s\S]*)$/;

async function getSource(url, context) {
  const parsed = new URL(url);
  let responseURL = url;
  let source;
  if (parsed.protocol === 'file:') {
    source = await readFileAsync(parsed);
  } else if (parsed.protocol === 'data:') {
    const match = RegExpPrototypeExec(DATA_URL_PATTERN, parsed.pathname);
    if (!match) {
      throw new ERR_INVALID_URL(url);
    }
    const { 1: base64, 2: body } = match;
    source = BufferFrom(decodeURIComponent(body), base64 ? 'base64' : 'utf8');
  } else if (experimentalNetworkImports && (
    parsed.protocol === 'https:' ||
    parsed.protocol === 'http:'
  )) {
    const res = await fetchModule(parsed, context);
    source = await res.body;
    responseURL = res.resolvedHREF;
  } else {
    const supportedSchemes = ['file', 'data'];
    if (experimentalNetworkImports) {
      ArrayPrototypePush(supportedSchemes, 'http', 'https');
    }
    throw new ERR_UNSUPPORTED_ESM_URL_SCHEME(parsed, supportedSchemes);
  }
  if (policy?.manifest) {
    policy.manifest.assertIntegrity(parsed, source);
  }
  return { __proto__: null, responseURL, source };
}


/**
 * Node.js default load hook.
 * @param {string} url
 * @param {object} context
 * @returns {object}
 */
async function defaultLoad(url, context) {
  let responseURL = url;
  const { importAssertions } = context;
  let {
    format,
    source,
  } = context;

  const urlInstance = new URL(url);

  throwIfUnsupportedURLScheme(urlInstance, experimentalNetworkImports);

  format ??= await defaultGetFormat(urlInstance, context);

  validateAssertions(url, format, importAssertions);

  if (
    format === 'builtin' ||
    format === 'commonjs'
  ) {
    source = null;
  } else if (source == null) {
    ({ responseURL, source } = await getSource(url, context));
  }

  return {
    __proto__: null,
    format,
    responseURL,
    source,
  };
}

/**
 * throws an error if the protocol is not one of the protocols
 * that can be loaded in the default loader
 * @param {URL} parsed
 * @param {boolean} experimentalNetworkImports
 */
function throwIfUnsupportedURLScheme(parsed, experimentalNetworkImports) {
  // Avoid accessing the `protocol` property due to the lazy getters.
  const protocol = parsed?.protocol;
  if (
    protocol &&
    protocol !== 'file:' &&
    protocol !== 'data:' &&
    protocol !== 'node:' &&
    (
      !experimentalNetworkImports ||
      (
        protocol !== 'https:' &&
        protocol !== 'http:'
      )
    )
  ) {
    const schemes = ['file', 'data', 'node'];
    if (experimentalNetworkImports) {
      ArrayPrototypePush(schemes, 'https', 'http');
    }
    throw new ERR_UNSUPPORTED_ESM_URL_SCHEME(parsed, schemes);
  }
}

module.exports = {
  defaultLoad,
};
