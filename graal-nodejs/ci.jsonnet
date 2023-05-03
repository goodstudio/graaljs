local common = import '../common.jsonnet';
local ci = import '../ci.jsonnet';

{
  local graalNodeJs = ci.jobtemplate + {
    cd:: 'graal-nodejs',
  },

  local ce = ci.ce,
  local ee = ci.ee,

  local vm_env = {
    // too slow on windows and darwin-amd64
    local enabled = 'os' in self && !(self.os == 'windows' || (self.os == 'darwin' && self.arch == 'amd64')),
    artifact:: if enabled then 'nodejs' else '',
    suiteimports+:: if enabled then ['vm', 'substratevm', 'tools'] else [],
    nativeimages+:: if enabled then ['lib:graal-nodejs', 'lib:jvmcicompiler'] else [], // 'js'
  },

  local gateTags(tags) = common.gateTags + {
    environment+: {
      TAGS: tags,
    },
    // increase default timelimit on windows and darwin-amd64
    timelimit: if 'os' in self && (self.os == 'windows' || (self.os == 'darwin' && self.arch == 'amd64')) then '1:15:00' else '45:00',
  },

  local build = {
    run+: [
      ['[', '${ARTIFACT_NAME}', ']', '||', 'mx', 'build', '--force-javac'], // build only if no artifact is being used
    ],
  },

  local defaultGateTags = gateTags('all') + {
    local tags = if 'os' in super && super.os == 'windows' then 'windows' else 'all',
    environment+: {
      TAGS: tags,
    }
  },

  local gateSubstrateVmSmokeTest = {
    run+: [
      ['mx', '--env', 'svm', 'build'],
      ['set-export', 'GRAALVM_HOME', ['mx', '--quiet', '--env', 'svm', 'graalvm-home']],
      ['${GRAALVM_HOME}/bin/node', '-e', 'console.log(\'Hello, World!\')'],
      ['${GRAALVM_HOME}/bin/npm', '--version'],
    ],
    timelimit: '45:00',
  },

  local gateVmSmokeTest = build + {
    run+: [
      ['set-export', 'GRAALVM_HOME', ['mx', '--quiet', 'graalvm-home']],
      ['${GRAALVM_HOME}/bin/node', '-e', 'console.log(\'Hello, World!\')'],
      ['${GRAALVM_HOME}/bin/npm', '--version'],
    ],
    timelimit: '45:00',
  },

  local gateCoverage = {
    coverage_gate_args:: ['--jacoco-omit-excluded', '--jacoco-relativize-paths', '--jacoco-omit-src-gen', '--jacoco-format', 'lcov', '--jacocout', 'coverage'],
    run+: [
      ['mx', '--dynamicimports', '/tools,/wasm', 'gate', '-B=--force-deprecation-as-warning', '-B=-A-J-Dtruffle.dsl.SuppressWarnings=truffle', '--strict-mode', '--tags', 'build,coverage'] + self.coverage_gate_args,
    ],
    teardown+: [
      ['mx', 'sversions', '--print-repositories', '--json', '|', 'coverage-uploader.py', '--associated-repos', '-'],
    ],
    timelimit: '1:00:00',
  },

  local checkoutNodeJsBenchmarks = {
    setup+: [
      ['git', 'clone', '--depth', '1', ['mx', 'urlrewrite', 'https://github.com/graalvm/nodejs-benchmarks.git'], '../nodejs-benchmarks'],
    ],
  },

  local auxEngineCache = {
    graalvmtests:: '../../graalvm-tests',
    run+: [
      ['python', self.graalvmtests + '/test.py', '-g', ['mx', '--quiet', 'graalvm-home'], '--print-revisions', '--keep-on-error', 'test/graal/aux-engine-cache'],
    ],
    timelimit: '1:00:00',
  },

  local testNode(suite, part='-r0,1', max_heap='8G') = {
    environment+: {
      SUITE: suite,
      PART: part,
      MAX_HEAP: max_heap,
    },
    run+: [
      ['mx', 'graalvm-show'],
      ['mx', 'testnode', '-Xmx${MAX_HEAP}', '${SUITE}', '${PART}'],
    ],
    timelimit: '1:15:00',
  },

  local buildAddons = build + {
    run+: [
      ['mx', 'makeinnodeenv', 'build-addons'],
    ],
  },

  local buildNodeAPI = build + {
    run+: [
      ['mx', 'makeinnodeenv', 'build-node-api-tests'],
    ],
  },

  local buildJSNativeAPI = build + {
    run+: [
      ['mx', 'makeinnodeenv', 'build-js-native-api-tests'],
    ],
  },

  local parallelHttp2 = 'parallel/test-http2-.*',
  local parallelNoHttp2 = 'parallel/(?!test-http2-).*',

  local generateBuilds = ci.generateBuilds,
  local promoteToTarget = ci.promoteToTarget,
  local defaultToTarget = ci.defaultToTarget,
  local includePlatforms = ci.includePlatforms,
  local excludePlatforms = ci.excludePlatforms,
  local gateOnMain = ci.gateOnMain,

  // Builds that should run on all supported platforms
  local testingBuilds = generateBuilds([
    graalNodeJs + common.gateStyleFullBuild                                                                                        + {name: 'nodejs-style-fullbuild'} +
      defaultToTarget(common.gate) +
      includePlatforms([common.linux_amd64]),

    graalNodeJs                             + defaultGateTags          + {dynamicimports+:: ['/wasm']}                             + {name: 'nodejs-default'} +
      promoteToTarget(common.gate, [common.linux_amd64, common.jdk17 + common.linux_amd64, common.jdk17 + common.linux_aarch64, common.jdk17 + common.darwin_aarch64, common.jdk17 + common.windows_amd64]) +
      promoteToTarget(common.postMerge, [common.jdk17 + common.darwin_amd64]),

    graalNodeJs                             + gateSubstrateVmSmokeTest                                                             + {name: 'nodejs-substratevm-ce'} +
      excludePlatforms([ci.mainGatePlatform]) +
      promoteToTarget(common.gate, [common.jdk17 + common.darwin_aarch64, common.jdk17 + common.windows_amd64]) +
      promoteToTarget(common.postMerge, [common.jdk17 + common.darwin_amd64]),
    graalNodeJs                             + gateSubstrateVmSmokeTest                                                             + {name: 'nodejs-substratevm-ee'} +
      excludePlatforms([ci.mainGatePlatform]),

    graalNodeJs + vm_env                    + gateVmSmokeTest                                                                 + ce + {name: 'nodejs-graalvm-ce'} +
      includePlatforms([ci.mainGatePlatform]) +
      promoteToTarget(common.gate, [ci.mainGatePlatform]),
    graalNodeJs + vm_env                    + gateVmSmokeTest                                                                 + ee + {name: 'nodejs-graalvm-ee'} +
      includePlatforms([ci.mainGatePlatform]) +
      promoteToTarget(common.gate, [ci.mainGatePlatform]),

    graalNodeJs          + buildAddons      + testNode('addons',        part='-r0,1', max_heap='8G')                               + {name: 'nodejs-addons'} + gateOnMain,
    graalNodeJs          + buildNodeAPI     + testNode('node-api',      part='-r0,1', max_heap='8G')                               + {name: 'nodejs-node-api'} + gateOnMain,
    graalNodeJs          + buildJSNativeAPI + testNode('js-native-api', part='-r0,1', max_heap='8G')                               + {name: 'nodejs-js-native-api'} + gateOnMain,

    graalNodeJs + vm_env + build            + testNode('async-hooks',   part='-r0,1', max_heap='8G')                               + {name: 'nodejs-async-hooks'} + gateOnMain +
      promoteToTarget(common.gate, [common.jdk17 + common.windows_amd64]),
    graalNodeJs + vm_env + build            + testNode('es-module',     part='-r0,1', max_heap='8G')                               + {name: 'nodejs-es-module'} + gateOnMain +
      promoteToTarget(common.gate, [common.jdk17 + common.windows_amd64]),
    # We run the `sequential` tests with a smaller heap because `test/sequential/test-child-process-pass-fd.js` starts 80 child processes.
    graalNodeJs + vm_env + build            + testNode('sequential',    part='-r0,1', max_heap='512M')                             + {name: 'nodejs-sequential'} + gateOnMain +
      promoteToTarget(common.gate, [common.jdk17 + common.windows_amd64]),

    graalNodeJs + vm_env + build            + testNode(parallelNoHttp2, part='-r0,5', max_heap='8G')                               + {name: 'nodejs-parallel-1'} + gateOnMain,
    graalNodeJs + vm_env + build            + testNode(parallelNoHttp2, part='-r1,5', max_heap='8G')                               + {name: 'nodejs-parallel-2'} + gateOnMain,
    graalNodeJs + vm_env + build            + testNode(parallelNoHttp2, part='-r2,5', max_heap='8G')                               + {name: 'nodejs-parallel-3'} + gateOnMain,
    graalNodeJs + vm_env + build            + testNode(parallelNoHttp2, part='-r3,5', max_heap='8G')                               + {name: 'nodejs-parallel-4'} + gateOnMain,
    graalNodeJs + vm_env + build            + testNode(parallelNoHttp2, part='-r4,5', max_heap='8G')                               + {name: 'nodejs-parallel-5'} + gateOnMain,

    graalNodeJs + vm_env + build            + testNode(parallelHttp2,   part='-r0,1', max_heap='8G')                               + {name: 'nodejs-parallel-http2'} +
      promoteToTarget(common.postMerge, [ci.mainGatePlatform]),

    graalNodeJs + vm_env + build            + auxEngineCache                                                                  + ee + {name: 'nodejs-aux-engine-cache'} + gateOnMain,
  ], defaultTarget=common.weekly),

  // Builds that only need to run on one platform
  local otherBuilds = generateBuilds([
    graalNodeJs + common.weekly    + gateCoverage                                                                                  + {name: 'nodejs-coverage'},

  ], platforms=[ci.mainGatePlatform]),

  builds: testingBuilds + otherBuilds,
}
