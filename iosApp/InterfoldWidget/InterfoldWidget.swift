import WidgetKit
import KeychainSwift
import SwiftUI

struct InterfoldWidgetEntryView : View {
  var entry: InterfoldWidgetProvider.Entry
  
  @Environment(\.widgetFamily) var family
  
  var body: some View {
    switch entry.state {
      case .data(let data):
        switch family {
          case .systemSmall: InterfoldSmallWidget(data: data)
          case .systemMedium: InterfoldMediumWidget(data: data)
          case .systemLarge: InterfoldLargeWidget(data: data)
          default: Text("Widget size not supported.")
        }
      case .error(let error):
        let errorText = switch error {
          case .NOT_LOGGED_IN: "You must be logged in to use this widget."
          case .PIN_IS_PROTECTED: "Disable your Interfold PIN to use this widget."
          case .NETWORK_ERROR: "A network error occurred loading this widget."
        }
      
        Text(errorText)
    }
  }
}

struct InterfoldFrontingWidget: Widget {
  let kind: String = "InterfoldFrontingWidget"
  
  var body: some WidgetConfiguration {
    StaticConfiguration(kind: kind, provider: InterfoldWidgetProvider()) { entry in
      if #available(iOS 17.0, *) {
        InterfoldWidgetEntryView(entry: entry)
          .containerBackground(.fill.tertiary, for: .widget)
          .widgetAccentable()
      } else {
        InterfoldWidgetEntryView(entry: entry)
          .padding()
          .background()
      }
    }
    .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    .contentMarginsDisabled()
    .configurationDisplayName("Currently Fronting")
    .description("Displays your currently fronting alters.")
  }
}

#Preview(as: .systemSmall) {
  InterfoldFrontingWidget()
} timeline: {
  generateDummyEntry()
}
