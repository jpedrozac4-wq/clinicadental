/* ============================================================================
   MODULO DE TRACKING Y VALIDACION DE ENTREGA OTP
   ----------------------------------------------------------------------------
   Consume GET /api/tracking/{guia} y POST /api/tracking/entregar. El codigo OTP
   lo dicta el comprador desde su factura: no existe ningun OTP constante en el
   frontend. Toda respuesta (exito o error) se muestra con el texto del backend.
   ============================================================================ */
import { CONFIG, apiUrl } from "../config.js";
import { fetchJson } from "../services/api.js";
import { el, clear, humanize, dateTime, loadingState, errorState } from "../core/dom.js";
import { NotificationService } from "../services/notification-service.js";

/** Hitos de despacho que expone CONFIG.ESTADOS.DESPACHO, en orden de avance. */
const PASOS = [
    { code: "PREPARADO", label: "Preparado en Bodega" },
    { code: "CARGADO", label: "Cargado en Transporte" },
    { code: "EN_RUTA", label: "En Ruta a Destino" },
    { code: "ENTREGADO", label: "Entregado con OTP" }
];

export class TrackingModule {

    constructor() {
        this.inputGuia = document.querySelector("#tracking-input");
        this.resultadoContainer = document.querySelector("#tracking-resultado");
        this.stepsContainer = document.querySelector("#tracking-steps");
        this.detalleContainer = document.querySelector("#tracking-detalle");
        this.inputOtp = document.querySelector("#otp-input");
        this.alertOtp = document.querySelector("#otp-alert");
    }

    async consultar() {
        const guia = (this.inputGuia && this.inputGuia.value || "").trim();
        if (!guia) {
            NotificationService.warning("Ingresá el número de guía de tracking.");
            return;
        }
        if (!this.resultadoContainer) return;

        this.resultadoContainer.classList.remove("is-hidden");
        this.setAlert(null);
        this.renderSteps(null);
        if (this.detalleContainer) {
            clear(this.detalleContainer);
            this.detalleContainer.append(loadingState("Consultando el estado del envío…"));
        }

        try {
            const data = await fetchJson(apiUrl(CONFIG.ENDPOINTS.TRACKING, guia));
            this.render(data);
        } catch (error) {
            const mensaje = error && error.message ? error.message : "No se pudo consultar el tracking.";
            if (this.detalleContainer) {
                clear(this.detalleContainer);
                this.detalleContainer.append(errorState(mensaje));
            }
            NotificationService.error(mensaje);
        }
    }

    render(data) {
        this.renderSteps(data.estado);
        if (!this.detalleContainer) return;

        clear(this.detalleContainer);
        this.detalleContainer.append(el("div", { class: "detail-facts" }, [
            el("p", { class: "text-muted", text: `Guía: ${data.numeroGuia}` }),
            el("p", { class: "text-muted", text: `Transporte: ${humanize(data.transporte)}` }),
            el("p", { class: "text-muted", text: `Courier: ${data.courier}` }),
            el("p", { class: "text-muted", text: `Estado: ${humanize(data.estado)}` }),
            el("p", { class: "text-muted", text: `OTP verificado: ${data.otpVerificado ? "Sí" : "No"}` }),
            el("p", { class: "text-muted", text: `Límite de entrega (SLA): ${dateTime(data.slaLimiteEntrega)}` })
        ]));
    }

    renderSteps(estado) {
        if (!this.stepsContainer) return;
        clear(this.stepsContainer);

        const actual = String(estado || "").toUpperCase();
        const indiceActual = PASOS.findIndex(paso => paso.code === actual);

        PASOS.forEach((paso, indice) => {
            const alcanzado = indiceActual >= 0 && indice <= indiceActual;
            this.stepsContainer.append(el("div", {
                class: alcanzado ? "tracking-step is-reached" : "tracking-step"
            }, [
                el("span", {
                    class: alcanzado ? "badge badge-success" : "badge badge-primary",
                    text: `Hito ${indice + 1}`
                }),
                el("div", { class: "tracking-step-label", text: paso.label })
            ]));
        });
    }

    async validarOtp() {
        const guia = (this.inputGuia && this.inputGuia.value || "").trim();
        const otp = (this.inputOtp && this.inputOtp.value || "").trim();

        if (!guia) {
            NotificationService.warning("Ingresá primero el número de guía.");
            return;
        }
        if (!otp) {
            this.setAlert("danger", "Ingresá el código OTP que figura en tu factura.");
            return;
        }

        try {
            const data = await fetchJson(CONFIG.ENDPOINTS.TRACKING_ENTREGAR, {
                method: "POST",
                body: JSON.stringify({ numeroGuia: guia, codigoOtp: otp })
            });
            this.setAlert("success", this.mensajeEntrega(data));
            this.renderSteps("ENTREGADO");
            NotificationService.success("Entrega confirmada.");
        } catch (error) {
            const mensaje = error && error.message ? error.message : "No se pudo confirmar la entrega.";
            this.setAlert("danger", mensaje);
        }
    }

    mensajeEntrega(data) {
        const partes = [data && data.mensaje ? data.mensaje : "Entrega confirmada."];
        if (data && data.fechaEntrega) {
            partes.push(`Fecha de entrega: ${dateTime(data.fechaEntrega)}.`);
        }
        return partes.join(" ");
    }

    setAlert(type, message) {
        if (!this.alertOtp) return;
        if (!type) {
            this.alertOtp.className = "alert-box is-hidden";
            clear(this.alertOtp);
            return;
        }
        this.alertOtp.className = `alert-box ${type}`;
        clear(this.alertOtp);
        this.alertOtp.textContent = message;
    }
}
