//
//  NotificationsModeView.swift
//  SimpleX (iOS)
//
//  Created by Evgeny on 03/07/2022.
//  Copyright © 2022 SimpleX Chat. All rights reserved.
//
// Spec: spec/client/navigation.md

import SwiftUI
import SimpleXChat

// Trustiatis Secure: the mode selector and its card view are removed, see
// docs/DECISIONS.md P-5. Only the information screen remains, reached from onboarding
// and explaining why this app has one notification mode and not three.

struct NotificationsInfoView: View {
    var body: some View {
        VStack(alignment: .leading) {
            Text("Notifications privacy")
                .font(.largeTitle)
                .bold()
                .padding(.vertical)
            ScrollView {
                VStack(alignment: .leading) {
                    Text("This app does not use a push server. You are not told about new messages while it is closed: you see them when you open it. That is the price of not handing your device token to anyone.")
                        .font(.callout)
                        .padding(.bottom)
                    Group {
                        ForEach(NotificationsMode.values) { mode in
                            VStack(alignment: .leading, spacing: 4) {
                                (Text(Image(systemName: mode.icon)) + textSpace + Text(mode.label))
                                    .font(.headline)
                                    .foregroundColor(.secondary)
                                Text(ntfModeDescription(mode))
                                    .lineLimit(10)
                                    .font(.callout)
                            }
                        }
                    }
                    .padding(.bottom)
                }
            }
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .modifier(ThemedBackground())
    }
}

struct NotificationsInfoView_Previews: PreviewProvider {
    static var previews: some View {
        NotificationsInfoView()
    }
}
