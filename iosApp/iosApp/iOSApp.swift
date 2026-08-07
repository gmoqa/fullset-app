import SwiftUI
import UIKit
import Shared

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
                // Al arrancar en frío: la extensión pudo despertarnos con una foto ya guardada.
                .onAppear { MainViewControllerKt.checkPendingSharedImage() }
                // fullset://shared: la Share Extension nos despierta tras dejar la foto en el App Group.
                .onOpenURL { _ in MainViewControllerKt.checkPendingSharedImage() }
                // Al volver a primer plano por cualquier vía, revisamos si quedó una foto pendiente.
                // Vía NotificationCenter y no .onChange(of:) por compatibilidad entre versiones del SDK.
                .onReceive(
                    NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)
                ) { _ in
                    MainViewControllerKt.checkPendingSharedImage()
                }
        }
    }
}
