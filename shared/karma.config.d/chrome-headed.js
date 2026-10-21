const headedFs = require("fs");
const headedPath = require("path");
const headedOs = require("os");

function headedMarkerOn() {
  if (process.env.INTERFOLD_CHROME_HEADED === "1") return true;
  try {
    const p = headedPath.join(__dirname, "chrome-headed.txt");
    return headedFs.existsSync(p) && headedFs.readFileSync(p, "utf8").trim() === "1";
  } catch (_e) {
    return false;
  }
}

if (headedMarkerOn()) {
  const headedDir = headedPath.join(headedOs.tmpdir(), "interfold-karma-chrome-headed");
  const headedDefault = headedPath.join(headedDir, "Default");
  headedFs.mkdirSync(headedDefault, { recursive: true });
  headedFs.writeFileSync(
    headedPath.join(headedDefault, "Preferences"),
    JSON.stringify({
      profile: {
        default_content_setting_values: { notifications: 1 },
      },
    })
  );
  config.customLaunchers = Object.assign({}, config.customLaunchers, {
    ChromeHeadedFcm: {
      base: "Chrome",
      flags: [
        "--user-data-dir=" + headedDir,
        "--no-first-run",
        "--no-default-browser-check",
        "--disable-popup-blocking",
      ],
    },
  });
  config.browsers = ["ChromeHeadedFcm"];
}
