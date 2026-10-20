/* ============================================================================
   MODULO DE CHECKOUT B2C REAL (COTIZACION, CUPON, ORDEN Y PAGO)
   ----------------------------------------------------------------------------
   Consume los endpoints publicos de /api/checkout sin fabricar ningun dato:
   el costo de envio sale de /cotizar, el descuento de /cupon/validar, el
   numero de orden de /iniciar y la factura de /pagar. Todo mensaje que el
   backend envia (exito o error) se muestra tal cual al comprador.
   ============================================================================ */
import { CONFIG, apiQuery } from "../config.js";
import { fetchJson } from "../services/api.js";
import { CarritoService } from "../services/carrito-service.js";
import { NotificationService } from "../services/notification-service.js";
import {
    el, clear, money, humanize, dateTime, table,
    loadingState, emptyState, errorState, estadoBadgeClass
} from "../core/dom.js";

const ENTREGA_DOMICILIO = "ENVIO_DOMICILIO";
const ENTREGA_RETIRO = "RETIRO_TIENDA_CLICK_COLLECT";

/** Codigos sembrados en cat_pago_metodo. La pasarela simulada es la unica con recargo. */
const METODOS_PAGO = [
    { code: "TARJETA_SIMULADA", label: "Tarjeta de crédito o débito (pasarela simulada)" },
    { code: "TRANSFERENCIA", label: "Transferencia bancaria local" },
    { code: "EFECTIVO_CONTRA_ENTREGA", label: "Efectivo contra entrega" }
];

/** Patron pragmatico: mismo criterio que el backend, sin pretender ser RFC 5322. */
const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

/** Fila de un desglose de importes. */
function totalRow(label, value, grand = false) {
    return el("div", { class: grand ? "checkout-total-row is-grand" : "checkout-total-row" }, [
        el("span", { text: label }),
        el("strong", { text: value })
    ]);
}

export class CheckoutModule {

    constructor() {
        this.modal = document.getElementById("modal-checkout");
        this.title = document.getElementById("checkout-title");
        this.step1 = document.getElementById("checkout-step-1");
        this.step2 = document.getElementById("checkout-step-2");
        this.step3 = document.getElementById("checkout-step-3");

        this.unsubscribe = null;
        this.reset();
    }

    reset() {
        this.state = {
            nombre: "",
            email: "",
            tipoEntrega: ENTREGA_DOMICILIO,
            codigoPostal: "",
            idSucursalRetiro: null,
            sucursales: [],
            sucursalesLoaded: false,
            cuponInput: "",
            cupon: null,
            cotizacion: null,
            cotizando: false,
            orden: null,
            factura: null,
            metodoPago: METODOS_PAGO[0].code
        };
        this.step1Error = null;
        this.step2Error = null;
    }

    open() {
        if (!this.modal) return;
        if (CarritoService.getCount() === 0) {
            NotificationService.warning("Tu carrito está vacío. Agregá productos antes de continuar.");
            return;
        }

        this.reset();
        if (this.title) this.title.textContent = "Finalizar compra";
        this.renderStep1();
        this.showStep(1);
        this.modal.classList.add("active");

        if (!this.unsubscribe) {
            this.unsubscribe = CarritoService.subscribe(() => this.onCartChange());
        }
        this.cargarSucursales();
    }

    close() {
        if (this.modal) this.modal.classList.remove("active");
        if (this.unsubscribe) {
            this.unsubscribe();
            this.unsubscribe = null;
        }
    }

    /** El carrito cambio mientras el checkout estaba abierto. */
    onCartChange() {
        // Tras crear la orden o pagar, el carrito ya no manda sobre esta vista.
        if (this.state.orden || this.state.factura) return;

        if (CarritoService.getCount() === 0) {
            this.close();
            NotificationService.warning("Tu carrito quedó vacío.");
            return;
        }

        this.refreshLineas();
        if (this.state.cupon && this.state.cupon.valido) {
            // El descuento depende del subtotal: se revalida contra el backend.
            this.aplicarCupon();
        } else if (this.state.tipoEntrega === ENTREGA_DOMICILIO && this.state.cotizacion) {
            this.cotizarEnvio();
        } else {
            this.refreshTotales();
        }
    }

    showStep(step) {
        if (this.step1) this.step1.classList.toggle("is-hidden", step !== 1);
        if (this.step2) this.step2.classList.toggle("is-hidden", step !== 2);
        if (this.step3) this.step3.classList.toggle("is-hidden", step !== 3);
    }

    /* ------------------------------------------------------------------ Paso 1 */

    renderStep1() {
        if (!this.step1) return;
        clear(this.step1);

        const nombreInput = el("input", {
            type: "text", id: "checkout-nombre", class: "form-control",
            value: this.state.nombre, placeholder: "Nombre y apellido", autocomplete: "name",
            on: { input: event => { this.state.nombre = event.target.value; } }
        });
        const emailInput = el("input", {
            type: "email", id: "checkout-email", class: "form-control",
            value: this.state.email, placeholder: "tu@correo.com", autocomplete: "email",
            on: { input: event => { this.state.email = event.target.value; } }
        });

        const datos = el("div", { class: "checkout-block" }, [
            el("h3", { text: "Tus datos" }),
            el("div", { class: "form-group" }, [
                el("label", { for: "checkout-nombre", text: "Nombre completo" }),
                nombreInput
            ]),
            el("div", { class: "form-group" }, [
                el("label", { for: "checkout-email", text: "Correo electrónico (recibirás la factura y el código OTP)" }),
                emailInput
            ])
        ]);

        this.linesNode = el("div", { class: "checkout-lineas" });
        this.totalsNode = el("div", { class: "checkout-totales" });
        this.step1Error = el("div", { class: "alert-box danger is-hidden" });

        const resumen = el("div", { class: "checkout-block" }, [
            el("h3", { text: "Tu pedido" }),
            this.linesNode,
            this.totalsNode
        ]);

        const actions = el("div", { class: "checkout-actions" }, [
            this.step1Error,
            el("button", {
                class: "btn btn-success btn-block", type: "button",
                text: "Crear orden y reservar stock",
                on: { click: () => this.crearOrden() }
            })
        ]);

        this.step1.append(datos, this.buildDeliveryBlock(), this.buildCouponBlock(), resumen, actions);

        this.refreshLineas();
        this.refreshEnvioInfo();
        this.refreshRetiroInfo();
        this.refreshTotales();
    }

    buildDeliveryBlock() {
        const radioName = "checkout-tipo-entrega";
        const domicilioRadio = el("input", {
            type: "radio", name: radioName, value: ENTREGA_DOMICILIO,
            checked: this.state.tipoEntrega === ENTREGA_DOMICILIO,
            on: { change: () => this.setTipoEntrega(ENTREGA_DOMICILIO) }
        });
        const retiroRadio = el("input", {
            type: "radio", name: radioName, value: ENTREGA_RETIRO,
            checked: this.state.tipoEntrega === ENTREGA_RETIRO,
            on: { change: () => this.setTipoEntrega(ENTREGA_RETIRO) }
        });

        return el("div", { class: "checkout-block" }, [
            el("h3", { text: "Entrega" }),
            el("div", { class: "delivery-choice" }, [
                el("label", { class: "delivery-option" }, [domicilioRadio, el("span", { text: "Envío a domicilio" })]),
                el("label", { class: "delivery-option" }, [retiroRadio, el("span", { text: "Retiro en tienda (sin costo de envío)" })])
            ]),
            this.buildEnvioBlock(),
            this.buildRetiroBlock()
        ]);
    }

    buildEnvioBlock() {
        const postalInput = el("input", {
            type: "text", id: "checkout-codigo-postal", class: "form-control",
            value: this.state.codigoPostal, placeholder: "Ej: 090112", inputmode: "numeric",
            on: {
                input: event => { this.state.codigoPostal = event.target.value; },
                change: () => this.cotizarEnvio()
            }
        });
        this.envioInfoNode = el("div", { class: "checkout-envio-info" });

        this.envioBlock = el("div", { class: "checkout-subblock" }, [
            el("div", { class: "checkout-inline-form" }, [
                el("div", { class: "form-group checkout-grow" }, [
                    el("label", { for: "checkout-codigo-postal", text: "Código postal" }),
                    postalInput
                ]),
                el("button", {
                    class: "btn btn-outline", type: "button", text: "Cotizar envío",
                    on: { click: () => this.cotizarEnvio() }
                })
            ]),
            this.envioInfoNode
        ]);
        this.envioBlock.classList.toggle("is-hidden", this.state.tipoEntrega !== ENTREGA_DOMICILIO);
        return this.envioBlock;
    }

    buildRetiroBlock() {
        this.retiroSelect = el("select", {
            id: "checkout-sucursal", class: "form-control",
            on: {
                change: event => {
                    this.state.idSucursalRetiro = event.target.value ? Number(event.target.value) : null;
                    this.refreshTotales();
                }
            }
        });
        this.retiroInfoNode = el("div", { class: "checkout-envio-info" });

        this.retiroBlock = el("div", { class: "checkout-subblock" }, [
            el("div", { class: "form-group" }, [
                el("label", { for: "checkout-sucursal", text: "Sucursal de retiro" }),
                this.retiroSelect
            ]),
            this.retiroInfoNode
        ]);
        this.retiroBlock.classList.toggle("is-hidden", this.state.tipoEntrega !== ENTREGA_RETIRO);
        return this.retiroBlock;
    }

    setTipoEntrega(tipo) {
        if (this.state.tipoEntrega === tipo) return;
        this.state.tipoEntrega = tipo;

        if (this.envioBlock) this.envioBlock.classList.toggle("is-hidden", tipo !== ENTREGA_DOMICILIO);
        if (this.retiroBlock) this.retiroBlock.classList.toggle("is-hidden", tipo !== ENTREGA_RETIRO);

        if (tipo === ENTREGA_RETIRO) {
            this.state.cotizacion = null;
        }
        this.refreshEnvioInfo();
        this.refreshRetiroInfo();
        this.refreshTotales();

        if (tipo === ENTREGA_DOMICILIO && this.state.codigoPostal.trim()) {
            this.cotizarEnvio();
        }
    }

    async cargarSucursales() {
        try {
            const data = await fetchJson(CONFIG.ENDPOINTS.CHECKOUT_SUCURSALES);
            this.state.sucursales = Array.isArray(data) ? data : [];
        } catch (error) {
            this.state.sucursales = [];
            NotificationService.error(error && error.message
                ? error.message
                : "No se pudieron cargar las sucursales de retiro.");
        }
        this.state.sucursalesLoaded = true;
        this.renderSucursales();
    }

    renderSucursales() {
        if (!this.retiroSelect) return;
        clear(this.retiroSelect);
        this.retiroSelect.append(el("option", {
            value: "",
            text: this.state.sucursalesLoaded ? "Elegí una sucursal" : "Cargando sucursales…"
        }));
        this.state.sucursales.forEach(sucursal => {
            this.retiroSelect.append(el("option", { value: String(sucursal.id), text: sucursal.nombre }));
        });
        if (this.state.idSucursalRetiro) {
            this.retiroSelect.value = String(this.state.idSucursalRetiro);
        }
        this.refreshRetiroInfo();
    }

    async cotizarEnvio() {
        if (this.state.tipoEntrega !== ENTREGA_DOMICILIO || this.state.cotizando) return;

        const codigoPostal = this.state.codigoPostal.trim();
        if (!codigoPostal) {
            this.state.cotizacion = null;
            this.refreshEnvioInfo();
            this.refreshTotales();
            return;
        }

        this.state.cotizando = true;
        this.refreshEnvioInfo();
        try {
            const data = await fetchJson(CONFIG.ENDPOINTS.CHECKOUT_COTIZAR, {
                method: "POST",
                body: JSON.stringify({
                    codigoPostal,
                    subtotalProductos: CarritoService.getSubtotal()
                })
            });
            this.state.cotizacion = data;
            this.setStep1Error(null);
        } catch (error) {
            this.state.cotizacion = null;
            this.setStep1Error(error && error.message ? error.message : "No se pudo cotizar el envío.");
        } finally {
            this.state.cotizando = false;
        }
        this.refreshEnvioInfo();
        this.refreshTotales();
    }

    refreshEnvioInfo() {
        if (!this.envioInfoNode) return;
        clear(this.envioInfoNode);
        if (this.state.tipoEntrega !== ENTREGA_DOMICILIO) return;

        if (this.state.cotizando) {
            this.envioInfoNode.append(el("p", { class: "text-muted text-sm", text: "Cotizando envío…" }));
            return;
        }
        const cotizacion = this.state.cotizacion;
        if (!cotizacion) {
            this.envioInfoNode.append(el("p", {
                class: "text-muted text-sm",
                text: this.state.codigoPostal.trim()
                    ? "No se pudo cotizar el envío para ese código postal."
                    : "Ingresá tu código postal para cotizar el envío."
            }));
            return;
        }
        this.envioInfoNode.append(el("p", { class: "text-sm", text: `Zona: ${cotizacion.zonaNombre}` }));
        this.envioInfoNode.append(el("p", {
            class: "text-muted text-sm",
            text: cotizacion.envioGratis
                ? "Envío gratis aplicado por el monto de tu compra."
                : `Envío gratis desde ${money(cotizacion.umbralCompraEnvioGratis)}.`
        }));
    }

    refreshRetiroInfo() {
        if (!this.retiroInfoNode) return;
        clear(this.retiroInfoNode);
        if (this.state.tipoEntrega !== ENTREGA_RETIRO) return;
        this.retiroInfoNode.append(el("p", {
            class: "text-muted text-sm",
            text: "Retiro en tienda: el envío no tiene costo."
        }));
    }

    buildCouponBlock() {
        this.cuponInput = el("input", {
            type: "text", id: "checkout-cupon", class: "form-control",
            value: this.state.cuponInput, placeholder: "Código de cupón (opcional)",
            on: { input: event => { this.state.cuponInput = event.target.value; } }
        });
        this.cuponFeedback = el("div", { class: "alert-box is-hidden" });

        return el("div", { class: "checkout-block" }, [
            el("h3", { text: "Cupón de descuento" }),
            el("div", { class: "checkout-inline-form" }, [
                el("div", { class: "form-group checkout-grow" }, [
                    el("label", { for: "checkout-cupon", text: "Código" }),
                    this.cuponInput
                ]),
                el("button", {
                    class: "btn btn-outline", type: "button", text: "Aplicar cupón",
                    on: { click: () => this.aplicarCupon() }
                }),
                el("button", {
                    class: "btn btn-ghost", type: "button", text: "Quitar",
                    on: { click: () => this.quitarCupon() }
                })
            ]),
            this.cuponFeedback
        ]);
    }

    async aplicarCupon() {
        const codigo = (this.state.cuponInput || "").trim();
        if (!codigo) {
            this.quitarCupon();
            return;
        }

        try {
            const data = await fetchJson(CONFIG.ENDPOINTS.CHECKOUT_CUPON, {
                method: "POST",
                body: JSON.stringify({
                    codigoCupon: codigo,
                    subtotalProductos: CarritoService.getSubtotal()
                })
            });
            this.state.cupon = data;
            if (data && data.valido) {
                this.setCuponFeedback("success",
                    `Cupón ${data.codigoCupon} aplicado: ${money(data.descuento)} de descuento.`);
            } else {
                // Un cupon rechazado llega como HTTP 200 con valido:false: se muestra su mensaje.
                this.setCuponFeedback("danger", (data && data.mensaje) || "El cupón no es válido.");
            }
        } catch (error) {
            this.state.cupon = null;
            this.setCuponFeedback("danger",
                error && error.message ? error.message : "No se pudo validar el cupón.");
        }
        this.refreshTotales();
    }

    quitarCupon() {
        this.state.cupon = null;
        this.state.cuponInput = "";
        if (this.cuponInput) this.cuponInput.value = "";
        this.setCuponFeedback(null);
        this.refreshTotales();
    }

    setCuponFeedback(type, message) {
        if (!this.cuponFeedback) return;
        if (!type) {
            this.cuponFeedback.className = "alert-box is-hidden";
            clear(this.cuponFeedback);
            return;
        }
        this.cuponFeedback.className = `alert-box ${type}`;
        clear(this.cuponFeedback);
        this.cuponFeedback.textContent = message;
    }

    refreshLineas() {
        if (!this.linesNode) return;
        clear(this.linesNode);

        const items = CarritoService.getItems();
        if (!items.length) {
            this.linesNode.append(emptyState("Tu carrito está vacío"));
            return;
        }
        items.forEach(item => {
            this.linesNode.append(el("div", { class: "checkout-line" }, [
                el("span", { class: "checkout-line-name", text: `${item.nombre} (${item.sku})` }),
                el("span", { class: "text-muted", text: `x${item.cantidad}` }),
                el("strong", { text: money(Number(item.precio) * item.cantidad) })
            ]));
        });
    }

    refreshTotales() {
        if (!this.totalsNode) return;

        const subtotal = CarritoService.getSubtotal();
        const esRetiro = this.state.tipoEntrega === ENTREGA_RETIRO;
        const envioConocido = esRetiro || Boolean(this.state.cotizacion);
        const costoEnvio = esRetiro ? 0 : (this.state.cotizacion ? Number(this.state.cotizacion.costoEnvio) : 0);
        const descuento = this.state.cupon && this.state.cupon.valido ? Number(this.state.cupon.descuento) : 0;
        const total = envioConocido ? Math.max(0, subtotal + costoEnvio - descuento) : null;

        clear(this.totalsNode);
        this.totalsNode.append(totalRow("Subtotal", money(subtotal)));
        this.totalsNode.append(totalRow("Envío", esRetiro
            ? "Retiro en tienda: sin costo"
            : (envioConocido ? money(costoEnvio) : "Pendiente de cotizar")));
        this.totalsNode.append(totalRow("Descuento", descuento > 0 ? `- ${money(descuento)}` : money(0)));
        this.totalsNode.append(totalRow("Total estimado", total === null ? "—" : money(total), true));
        this.totalsNode.append(el("p", {
            class: "checkout-note text-muted text-sm",
            text: "El total final, incluida la comisión de pasarela, se confirma al crear la orden."
        }));
    }

    validarFormulario() {
        const nombre = (this.state.nombre || "").trim();
        const email = (this.state.email || "").trim();

        if (!nombre) return "Ingresá tu nombre completo.";
        if (!email) return "Ingresá tu correo electrónico.";
        if (!EMAIL_PATTERN.test(email)) return "Ingresá un correo electrónico válido.";
        if (CarritoService.getCount() === 0) return "Tu carrito está vacío.";

        if (this.state.tipoEntrega === ENTREGA_DOMICILIO) {
            if (!this.state.codigoPostal.trim()) return "Ingresá el código postal para el envío a domicilio.";
            if (!this.state.cotizacion) return "Cotizá el envío antes de crear la orden.";
        } else if (!this.state.idSucursalRetiro) {
            return "Elegí la sucursal de retiro.";
        }
        return null;
    }

    construirSolicitud() {
        const esRetiro = this.state.tipoEntrega === ENTREGA_RETIRO;
        return {
            email: this.state.email.trim(),
            nombre: this.state.nombre.trim(),
            lineas: CarritoService.getItems().map(item => ({ sku: item.sku, cantidad: item.cantidad })),
            // Solo se envia un cupon ya validado por el backend.
            codigoCupon: this.state.cupon && this.state.cupon.valido ? this.state.cupon.codigoCupon : null,
            codigoPostal: esRetiro ? null : this.state.codigoPostal.trim(),
            tipoEntrega: this.state.tipoEntrega,
            idSucursalRetiro: esRetiro ? this.state.idSucursalRetiro : null
        };
    }

    async crearOrden() {
        const error = this.validarFormulario();
        if (error) {
            this.setStep1Error(error);
            NotificationService.error(error);
            return;
        }
        this.setStep1Error(null);

        try {
            const data = await fetchJson(CONFIG.ENDPOINTS.CHECKOUT_INICIAR, {
                method: "POST",
                body: JSON.stringify(this.construirSolicitud())
            });
            this.state.orden = data;
            this.renderStep2();
            this.showStep(2);
        } catch (err) {
            const mensaje = err && err.message ? err.message : "No se pudo crear la orden.";
            this.setStep1Error(mensaje);
            NotificationService.error(mensaje);
        }
    }

    setStep1Error(message) {
        this.setAlertNode(this.step1Error, message);
    }

    /* ------------------------------------------------------------------ Paso 2 */

    renderStep2() {
        if (!this.step2) return;
        clear(this.step2);

        const orden = this.state.orden || {};
        this.step2Error = el("div", { class: "alert-box danger is-hidden" });

        const metodoSelect = el("select", {
            id: "checkout-metodo-pago", class: "form-control",
            on: { change: event => { this.state.metodoPago = event.target.value; } }
        }, METODOS_PAGO.map(metodo => el("option", { value: metodo.code, text: metodo.label })));

        const desglose = el("div", { class: "checkout-totales" }, [
            totalRow("Número de orden", orden.numeroOrden || "—"),
            totalRow("Subtotal", money(orden.subtotal)),
            totalRow("Envío", Number(orden.costoEnvio) === 0 ? "Sin costo" : money(orden.costoEnvio)),
            totalRow("Descuento", money(orden.descuentoAplicado)),
            totalRow("Comisión de pasarela", money(orden.comisionPasarela)),
            totalRow("Total a pagar", money(orden.totalAPagar), true)
        ]);

        this.step2.append(
            el("div", { class: "alert-box info" }, [
                el("strong", { text: "Stock reservado por 2 horas." }),
                el("div", { text: orden.mensaje || "Completá el pago antes de que expire la reserva." }),
                el("div", { text: `La reserva expira el ${dateTime(orden.expiraEn)}.` })
            ]),
            desglose,
            el("div", { class: "form-group" }, [
                el("label", { for: "checkout-metodo-pago", text: "Método de pago" }),
                metodoSelect
            ]),
            this.step2Error,
            el("div", { class: "checkout-actions" }, [
                el("button", {
                    class: "btn btn-success btn-block", type: "button", text: "Pagar ahora",
                    on: { click: () => this.procesarPago() }
                })
            ])
        );
        metodoSelect.value = this.state.metodoPago;
    }

    async procesarPago() {
        if (!this.state.orden || !this.state.orden.numeroOrden) {
            this.setStep2Error("No hay una orden creada para pagar.");
            return;
        }
        this.setStep2Error(null);

        try {
            const data = await fetchJson(CONFIG.ENDPOINTS.CHECKOUT_PAGAR, {
                method: "POST",
                body: JSON.stringify({
                    numeroOrden: this.state.orden.numeroOrden,
                    metodoPago: this.state.metodoPago
                })
            });
            this.state.factura = data;
            this.renderStep3();
            this.showStep(3);
            CarritoService.clear();
        } catch (err) {
            const mensaje = err && err.message ? err.message : "No se pudo procesar el pago.";
            this.setStep2Error(mensaje);
            NotificationService.error(mensaje);
        }
    }

    setStep2Error(message) {
        this.setAlertNode(this.step2Error, message);
    }

    /* ------------------------------------------------------------------ Paso 3 */

    renderStep3() {
        if (!this.step3) return;
        clear(this.step3);

        const factura = this.state.factura || {};
        const orden = this.state.orden || {};

        const children = [
            el("div", { class: "alert-box success" }, [
                el("strong", { text: "¡Pago aprobado y factura emitida!" }),
                el("div", { text: `Orden: ${orden.numeroOrden || "—"}` }),
                el("div", { text: `Factura: ${factura.facturaCorrelativo || "—"}` }),
                el("div", { text: `Total pagado: ${money(factura.totalPagado)}` }),
                el("div", { text: `NIT/CI del cliente: ${factura.nitCliente || "CF"}` }),
                factura.mensaje ? el("p", { class: "text-sm", text: factura.mensaje }) : null
            ])
        ];

        if (factura.pdfUrl) {
            children.push(el("a", {
                class: "btn btn-outline btn-block checkout-pdf-link",
                href: factura.pdfUrl,
                target: "_blank",
                rel: "noopener noreferrer",
                text: "Descargar factura en PDF"
            }));
        }

        children.push(el("div", { class: "checkout-actions" }, [
            el("button", {
                class: "btn btn-primary btn-block", type: "button", text: "Finalizar",
                on: { click: () => this.close() }
            })
        ]));

        this.step3.append(...children);
    }

    /* --------------------------------------------------------------- Auxiliares */

    setAlertNode(node, message) {
        if (!node) return;
        if (!message) {
            node.className = "alert-box danger is-hidden";
            clear(node);
            return;
        }
        node.className = "alert-box danger";
        clear(node);
        node.textContent = message;
    }
}

/* ============================================================================
   CONSULTA PUBLICA DE UNA ORDEN
   ----------------------------------------------------------------------------
   GET /checkout/ordenes exige correo Y numero de orden: un correo equivocado y
   un numero inexistente responden el mismo 404 a proposito. Esta vista no es un
   "listado de mis ordenes"; muestra una sola orden y aclara que ambos campos
   deben coincidir.
   ============================================================================ */
export class ConsultaOrdenModule {

    constructor() {
        this.emailInput = document.querySelector("#consulta-email");
        this.numeroInput = document.querySelector("#consulta-numero");
        this.button = document.querySelector("#btn-consultar-orden");
        this.resultado = document.querySelector("#consulta-resultado");
    }

    init() {
        if (this.button) this.button.addEventListener("click", () => this.consultar());
    }

    async consultar() {
        if (!this.resultado) return;
        clear(this.resultado);

        const email = (this.emailInput && this.emailInput.value || "").trim();
        const numeroOrden = (this.numeroInput && this.numeroInput.value || "").trim();

        if (!email || !numeroOrden) {
            this.resultado.append(errorState(
                "Ingresá el correo electrónico y el número de orden: ambos son obligatorios."));
            return;
        }

        this.resultado.append(loadingState("Consultando la orden…"));
        try {
            const data = await fetchJson(apiQuery(CONFIG.ENDPOINTS.CHECKOUT_ORDENES, { email, numeroOrden }));
            this.render(data);
        } catch (error) {
            const mensaje = error && error.message ? error.message : "No se pudo consultar la orden.";
            clear(this.resultado);
            this.resultado.append(errorState(
                `${mensaje} Verificá que el correo y el número de orden correspondan a la misma compra.`));
        }
    }

    render(data) {
        if (!this.resultado) return;
        clear(this.resultado);

        const lineas = Array.isArray(data.lineas) ? data.lineas : [];
        this.resultado.append(el("div", { class: "card" }, [
            el("div", { class: "card-header" }, [
                el("h3", { text: `Orden ${data.numeroOrden}` }),
                el("span", { class: estadoBadgeClass(data.estado), text: humanize(data.estado) })
            ]),
            el("p", { class: "text-muted", text: data.estadoDescripcion || "" }),
            el("div", { class: "detail-facts" }, [
                el("p", { class: "text-muted", text: `Tipo de entrega: ${humanize(data.tipoEntrega)}` }),
                el("p", { class: "text-muted", text: `Fecha de creación: ${dateTime(data.fechaCreacion)}` })
            ]),
            el("div", { class: "checkout-totales" }, [
                totalRow("Subtotal", money(data.subtotal)),
                totalRow("Envío", money(data.costoEnvio)),
                totalRow("Descuento", money(data.descuentoAplicado)),
                totalRow("Total a pagar", money(data.totalAPagar), true)
            ]),
            table(
                ["SKU", "Cantidad", "Precio unitario", "Total"],
                lineas.map(linea => [
                    linea.sku,
                    String(linea.cantidad),
                    money(linea.precioUnitario),
                    money(linea.totalLinea)
                ])
            )
        ]));
    }
}
