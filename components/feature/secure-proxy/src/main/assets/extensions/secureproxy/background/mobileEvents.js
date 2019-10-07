import {Component} from "./component.js";
import {constants} from "./constants.js";

export class MobileEvents extends Component {
  async init() {
    if (!constants.isAndroid) {
      return;
    }

    ConfigUtils.setDebuggingEnabled(true);

    // eslint-disable-next-line verify-await/check
    let port = browser.runtime.connectNative("mozacSecureProxy");
    port.onMessage.addListener(async message => {
      switch (message.action) {
        case "sendCode":
          log("Received sendCode");
          await this.sendMessage("sendCode", message);
          return;

        case "sendEnabled":
          log("Received sendEnabled value:" + message.value);
          let newState = (message.value === 'true')
          // reason is a NOOP as mobile uses its own telemetry
          await this.sendMessage("enableProxy", {enabledState: newState, reason: "mobile"});
          return;

        case "log":
          await port.postMessage(message.value);
          return;

        default:
          console.error(`Received invalid action ${message.action}`);
      }
    });
  }
}
