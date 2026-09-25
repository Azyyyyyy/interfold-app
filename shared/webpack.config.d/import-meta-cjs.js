// Kotlin 2.4.20 ships webpack 5.108.1, which treats files containing
// `import.meta` as ES modules. Karma still evaluates vendor chunks
// (commons.js) as classic scripts, so Chrome throws:
//   Uncaught SyntaxError: Cannot use 'import.meta' outside a module
// jose 6 is ESM-only and uses import.meta.url. Emit a classic script and
// strip any import.meta that webpack leaves in the bundle.
config.output = config.output || {};
config.output.module = false;
config.output.environment = Object.assign({}, config.output.environment, {
  module: false,
});
config.experiments = config.experiments || {};
config.experiments.outputModule = false;

config.module = config.module || {};
config.module.parser = config.module.parser || {};
config.module.parser.javascript = Object.assign(
  {},
  config.module.parser.javascript,
  { importMeta: true }
);
config.module.rules = [
  {
    test: /\.m?js$/,
    type: "javascript/auto",
    resolve: { fullySpecified: false },
  },
].concat(config.module.rules || []);

class StripImportMetaPlugin {
  apply(compiler) {
    const { Compilation, sources } = compiler.webpack;
    compiler.hooks.thisCompilation.tap("StripImportMetaPlugin", (compilation) => {
      compilation.hooks.processAssets.tap(
        {
          name: "StripImportMetaPlugin",
          stage: Compilation.PROCESS_ASSETS_STAGE_OPTIMIZE_INLINE,
        },
        () => {
          Object.keys(compilation.assets).forEach((file) => {
            if (!file.endsWith(".js")) return;
            const text = compilation.assets[file].source().toString();
            if (text.indexOf("import.meta") === -1) return;
            const rewritten = text
              .split("import.meta.url")
              .join("''")
              .split("import.meta")
              .join("({})");
            compilation.updateAsset(file, new sources.RawSource(rewritten));
          });
        }
      );
    });
  }
}

config.plugins = config.plugins || [];
config.plugins.push(new StripImportMetaPlugin());
