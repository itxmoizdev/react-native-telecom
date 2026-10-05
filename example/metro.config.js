const path = require('path');
const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');

const root = path.resolve(__dirname, '..');
const pak = require('../package.json');

/**
 * Metro configuration for the library example app.
 * https://facebook.github.io/metro/docs/configuration
 *
 * @type {import('metro-config').MetroConfig}
 */
const config = {
  watchFolders: [root],
  resolver: {
    unstable_enablePackageExports: true,
    nodeModulesPaths: [
      path.resolve(__dirname, 'node_modules'),
      path.resolve(root, 'node_modules'),
    ],
    extraNodeModules: {
      [pak.name]: root,
    },
    resolveRequest: (context, moduleName, platform) => {
      if (
        moduleName === pak.name ||
        moduleName.startsWith(`${pak.name}/`)
      ) {
        return context.resolveRequest(
          {
            ...context,
            unstable_conditionNames: [
              'react-native-telecom-source',
              ...(context.unstable_conditionNames || []),
            ],
          },
          moduleName,
          platform
        );
      }

      return context.resolveRequest(context, moduleName, platform);
    },
  },
};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
