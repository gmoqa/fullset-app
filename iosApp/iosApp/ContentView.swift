import SwiftUI
import UIKit
import Shared

/// Puente entre SwiftUI y la UI de Compose Multiplatform: monta el UIViewController que devuelve
/// `MainViewController()` (definido en :shared, iosMain).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard) // Compose maneja los insets internos
    }
}
