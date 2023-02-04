local common_json = (import "common.json");

{
  jdk17: {
    jdk:: 'jdk17',
    downloads+: {
      JAVA_HOME: common_json.jdks["labsjdk-ee-17"],
    },
  },

  jdk20: {
    jdk:: 'jdk20',
    downloads+: {
      JAVA_HOME: common_json.jdks["labsjdk-ce-20"],
    },
  },

  deploy:      {targets+: ['deploy']},
  gate:        {targets+: ['gate']},
  postMerge:   {targets+: ['post-merge']},
  bench:       {targets+: ['bench', 'post-merge']},
  dailyBench:  {targets+: ['bench', 'daily']},
  weeklyBench: {targets+: ['bench', 'weekly']},
  manualBench: {targets+: ['bench']},
  daily:       {targets+: ['daily']},
  weekly:      {targets+: ['weekly']},

  local packages(platform) =
    if 'packages' in common_json.sulong.deps[platform] then common_json.sulong.deps[platform].packages else {},

  local common = {
    packages+: packages('common') + {
      'mx': common_json.mx_version,
      'python3': '==3.8.10',
      'pip:pylint': '==2.4.4',
      'pip:ninja_syntax': '==1.7.2',
    },
    catch_files+: [
      'Graal diagnostic output saved in (?P<filename>.+.zip)',
      'npm-debug.log', // created on npm errors
    ],
    environment+: {
      MX_PYTHON: "python3.8",
      GRAALVM_CHECK_EXPERIMENTAL_OPTIONS: "true",
    },
    python_version: "3",
  },

  ol7:: {
    docker+: {
      image: 'buildslave_ol7',
      mount_modules: true,
    }
  },

  linux: common + self.ol7 + {
    os:: 'linux',
    arch:: 'amd64',
    packages+: packages('linux') + {
      'apache/ab': '==2.3',
      devtoolset: '==7', # GCC 7.3.1, make 4.2.1, binutils 2.28, valgrind 3.13.0
      git: '>=1.8.3',
      maven: '==3.3.9',
    },
    capabilities+: ['linux', 'amd64'],
  },

  x52: self.linux + {
    capabilities+: ['no_frequency_scaling', 'tmpfs25g', 'x52'],
  },

  linux_aarch64: common + {
    os:: 'linux',
    arch:: 'aarch64',
    capabilities+: ['linux', 'aarch64'],
    packages+: {
      devtoolset: '==7', # GCC 7.3.1, make 4.2.1, binutils 2.28, valgrind 3.13.0
    }
  },

  darwin: common + {
    os:: 'darwin',
    arch:: 'amd64',
    packages+: packages('darwin_amd64'),
    environment+: {
      // for compatibility with macOS El Capitan
      MACOSX_DEPLOYMENT_TARGET: '10.11',
    },
    capabilities: ['darwin_mojave', 'amd64'],
  },

  darwin_aarch64: common + {
    os:: 'darwin',
    arch:: 'aarch64',
    packages+: packages('darwin_aarch64'),
    environment+: {
      // for compatibility with macOS BigSur
      MACOSX_DEPLOYMENT_TARGET: '11.0',
    },
    capabilities: ['darwin', 'aarch64'],
  },

  windows: common + {
    os:: 'windows',
    arch:: 'amd64',
    capabilities: ['windows', 'amd64'],
    packages+: packages('windows') + common_json.devkits["windows-" + self.jdk].packages,
    devkit_version:: std.filterMap(function(p) std.startsWith(p, 'devkit:VS'), function(p) std.substr(p, std.length('devkit:VS'), 4), std.objectFields(self.packages))[0],
    setup+: [
      ['set-export', 'DEVKIT_VERSION', self.devkit_version],
    ],
  },

  local gateCmd = ['mx', 'gate', '-B=--force-deprecation-as-warning', '-B=-A-J-Dtruffle.dsl.ignoreCompilerWarnings=true', '--strict-mode', '--tags', '${TAGS}'],

  eclipse : {
    downloads+: {
      ECLIPSE: {name: 'eclipse', version: '4.14.0', platformspecific: true},
      JDT: {name: 'ecj', version: '4.14.0', platformspecific: false},
    },
    environment+: {
      ECLIPSE_EXE: '$ECLIPSE/eclipse',
    },
  },

  build : {
    run+: [
      ['mx', 'build', '--force-javac'],
    ],
  },

  buildCompiler : {
    run+: [
      ['mx', '--dynamicimports', '/compiler', 'build', '--force-javac'],
    ],
  },

  gateTags : self.build + {
    run+: [
      gateCmd,
    ],
    timelimit: '30:00',
  },

  gateStyleFullBuild : self.eclipse + {
    run+: [
      ['set-export', 'TAGS', 'style,fullbuild'],
      gateCmd,
    ],
    timelimit: '30:00',
  },
}
