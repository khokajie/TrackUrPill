module.exports = {
  // Environment settings
  env: {
    es6: true,
    node: true,
  },
  // Parser options
  parserOptions: {
    ecmaVersion: 2018,
  },
  // Extend configurations
  extends: ["eslint:recommended", "google"],
  // Custom rules
  rules: {
    "indent": [
      "error",
      2,
      { SwitchCase: 1, ignoredNodes: ["ConditionalExpression"] },
    ],
    "no-restricted-globals": ["error", "name", "length"],
    "prefer-arrow-callback": "error",
    "quotes": ["error", "double", { allowTemplateLiterals: true }],
    "max-len": ["error", { code: 120 }],
    "object-curly-spacing": ["error", "always"],
  },
  // Overrides for specific file types
  overrides: [
    {
      files: ["**/*.spec.*"],
      env: {
        mocha: true,
      },
      rules: {},
    },
  ],
  // Global variables
  globals: {},
};
