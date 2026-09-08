import WidgetKit
import SwiftUI

@main
struct InterfoldWidgetBundle: WidgetBundle {
    var body: some Widget {
      InterfoldFrontingWidget()
        /*if #available(iOS 18.0, *) {
            InterfoldWidgetControl()
        }*/
    }
}

extension Bundle {
  var releaseVersionNumber: String? {
    return infoDictionary?["CFBundleShortVersionString"] as? String
  }
  var buildVersionNumber: String? {
    return infoDictionary?["CFBundleVersion"] as? String
  }
}
