import UIKit
import UniformTypeIdentifiers

/// Share Extension: recibe una imagen desde el menú Compartir del sistema, la guarda en el contenedor
/// del App Group y despierta a la app host. La app (al volver activa) consume ese archivo y pregunta a
/// qué juego de Playing adjuntarlo (`AttachSharedPhotoDialog`, ya en commonMain).
///
/// No muestra UI propia: procesa y cierra. La extensión corre en otro proceso y no comparte sandbox
/// con la app, de ahí el App Group como canal.
class ShareViewController: UIViewController {
    private let appGroup = "group.com.gmoqa.fullset"
    private let fileName = "shared_incoming.jpg"

    override func viewDidLoad() {
        super.viewDidLoad()
        handleShare()
    }

    private func handleShare() {
        let imageType = UTType.image.identifier
        guard let item = extensionContext?.inputItems.first as? NSExtensionItem,
              let provider = item.attachments?.first(where: { $0.hasItemConformingToTypeIdentifier(imageType) })
        else {
            return finish()
        }
        provider.loadItem(forTypeIdentifier: imageType, options: nil) { [weak self] value, _ in
            self?.save(value)
            self?.finish()
        }
    }

    /// El item puede llegar como URL de archivo, UIImage o Data según la app que comparte.
    private func save(_ value: Any?) {
        guard let container = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroup) else { return }

        let data: Data?
        switch value {
        case let url as URL: data = try? Data(contentsOf: url)
        case let image as UIImage: data = image.jpegData(compressionQuality: 0.9)
        case let d as Data: data = d
        default: data = nil
        }
        guard let data else { return }
        try? data.write(to: container.appendingPathComponent(fileName))
    }

    private func finish() {
        DispatchQueue.main.async { [weak self] in
            self?.openHostApp()
            self?.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
        }
    }

    /// Abre la app host (`fullset://shared`) recorriendo la responder chain: `UIApplication.shared` no
    /// está disponible dentro de una extensión. Es best-effort; si no prende, el usuario abre la app a
    /// mano y la foto pendiente igual se levanta al volver a primer plano.
    private func openHostApp() {
        guard let url = URL(string: "fullset://shared") else { return }
        let selector = sel_registerName("openURL:")
        var responder: UIResponder? = self
        while let current = responder {
            if current.responds(to: selector), current !== self {
                _ = current.perform(selector, with: url)
                return
            }
            responder = current.next
        }
    }
}
