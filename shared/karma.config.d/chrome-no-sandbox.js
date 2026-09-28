// GitHub-hosted Ubuntu (23.10+) disables unprivileged user namespaces, so
// ChromeHeadless dies with "No usable sandbox!". Local runs keep the sandbox.
if (process.env.CI) {
  config.customLaunchers = Object.assign({}, config.customLaunchers, {
    ChromeHeadlessNoSandbox: {
      base: "ChromeHeadless",
      flags: ["--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage"],
    },
  });
  config.browsers = ["ChromeHeadlessNoSandbox"];
}
