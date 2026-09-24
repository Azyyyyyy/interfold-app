// Kotlin 2.4.20 ships webpack 5.108.1, which classifies any file containing
// `import.meta` as ESM. Karma still executes vendor chunks (commons.js) as
// classic scripts, so Chrome throws:
//   Uncaught SyntaxError: Cannot use 'import.meta' outside a module
// jose 6 is ESM-only and uses import.meta.url. Force CJS-compatible emission
// for the Karma test bundle: rewrite import.meta and treat jose as javascript/auto.
config.output = config.output || {};
config.output.environment = Object.assign({}, config.output.environment, {
  module: false,
});

config.module = config.module || {};
config.module.parser = config.module.parser || {};
config.module.parser.javascript = Object.assign(
  {},
  config.module.parser.javascript,
  { importMeta: true }
);
config.module.rules = (config.module.rules || []).concat({
  test: /[\\/]node_modules[\\/]jose[\\/]/,
  type: "javascript/auto",
});
