/* ============================================================================
   SERVICIO DE NOTIFICACIONES (TOASTS Y CONFIRMACIONES SIN window.alert)
   ============================================================================ */
export class NotificationService {

    static showToast(message, type = "success", timeoutMs = 4200) {
        const container = document.getElementById("toast-container") || this.createToastContainer();

        const toast = document.createElement("div");
        toast.className = `toast toast-${type}`;
        toast.setAttribute("role", type === "error" ? "alert" : "status");
        toast.textContent = message;
        container.append(toast);

        setTimeout(() => toast.remove(), timeoutMs);
    }

    static success(message) { this.showToast(message, "success"); }
    static error(message) { this.showToast(message, "error"); }
    static warning(message) { this.showToast(message, "warning"); }
    static info(message) { this.showToast(message, "info"); }

    /**
     * Confirmacion asincronica reemplazando window.confirm, para que la UX no
     * dependa de dialogos nativos del navegador.
     */
    static confirm(message, { confirmLabel = "Confirmar", cancelLabel = "Cancelar" } = {}) {
        return new Promise(resolve => {
            const overlay = document.createElement("div");
            overlay.className = "modal-overlay active";

            const dialog = document.createElement("div");
            dialog.className = "modal-card";

            const text = document.createElement("p");
            text.className = "modal-message";
            text.textContent = message;
            dialog.append(text);

            const actions = document.createElement("div");
            actions.className = "modal-actions";

            const cancelButton = document.createElement("button");
            cancelButton.className = "btn btn-outline";
            cancelButton.textContent = cancelLabel;

            const confirmButton = document.createElement("button");
            confirmButton.className = "btn btn-danger";
            confirmButton.textContent = confirmLabel;

            const close = (result) => {
                overlay.remove();
                resolve(result);
            };

            cancelButton.addEventListener("click", () => close(false));
            confirmButton.addEventListener("click", () => close(true));
            overlay.addEventListener("click", event => {
                if (event.target === overlay) close(false);
            });

            actions.append(cancelButton, confirmButton);
            dialog.append(actions);
            overlay.append(dialog);
            document.body.append(overlay);
            confirmButton.focus();
        });
    }

    static createToastContainer() {
        const container = document.createElement("div");
        container.id = "toast-container";
        document.body.append(container);
        return container;
    }
}
