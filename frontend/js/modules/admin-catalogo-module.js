/* ============================================================================
   VISTA: CATALOGO MAESTRO (ALTA DE CALZADO CON IMAGEN Y GENERACION DE SKUs)
   ----------------------------------------------------------------------------
   Los selectores se cargan desde la API; el alta viaja como multipart porque
   incluye la fotografia del producto.
   ============================================================================ */
import { CONFIG, apiUrl, apiQuery } from "../config.js";
import { fetchJson } from "../services/api.js";
import { NotificationService } from "../services/notification-service.js";
import {
    el, qs, money, humanize, table,
    sectionHeader, loadingState, emptyState, errorState
} from "../core/dom.js";

// Backend limit declared in application.yml
// (spring.servlet.multipart.max-file-size / max-request-size).
const MAX_IMAGEN_BYTES = 10 * 1024 * 1024;

export class CatalogoAdminView {

    constructor() {
        this.selectedFile = null;
        this.modelos = [];
        this.modelosRequestId = 0;
        this.tipos = [];
        this.marcas = [];
        this.colores = [];
        this.tallas = [];
        this.modeloEditando = null;
        this.modeloDetalleRequestId = 0;
        // Bumped on every mount and unmount: a continuation that captured an older
        // value must not touch state, paint, or notify on the dead view.
        this.viewGeneration = 0;
        this.panelMutationEnCurso = false;
        this.skuEditandoId = null;
        this.tallaEditandoId = null;
    }

    async mount(container) {
        this.viewGeneration += 1;
        this.container = container;
        container.replaceChildren(
            sectionHeader("Catálogo maestro", "Generación algorítmica de SKUs"),
            this.buildModelosSection(),
            this.buildForm(),
            this.buildTallasSection()
        );

        this.selects = {
            tipo: qs("#nuevo-prod-tipo"),
            marca: qs("#nuevo-prod-marca"),
            color: qs("#nuevo-prod-color")
        };

        this.filtrosSelect = {
            marca: qs("#filtro-modelos-marca"),
            tipo: qs("#filtro-modelos-tipo")
        };
        this.resultados = qs("#modelos-resultados");
        this.conteo = qs("#modelos-conteo");
        this.tallasResultados = qs("#tallas-resultados");

        this.bindPreview();
        await Promise.all([this.loadSelectors(), this.cargarModelos(), this.cargarTallas()]);
        // Sizes feed the SKU selector and this master-data section: paint once loaded.
        this.renderTallasSection();
        return null;
    }

    // ------------------------------------------------------------------
    // Modelos: listado administrativo con filtros facetados
    // ------------------------------------------------------------------

    buildModelosSection() {
        const filtros = el("form", {
            class: "toolbar",
            on: { submit: event => { event.preventDefault(); this.cargarModelos(); } }
        }, [
            el("div", { class: "form-group toolbar-grow" }, [
                el("label", { for: "filtro-modelos-q", text: "Buscar por nombre" }),
                el("input", {
                    type: "search", id: "filtro-modelos-q", class: "form-control",
                    placeholder: "Ej: Runner Pro"
                })
            ]),
            this.filterGroup("Marca", "filtro-modelos-marca",
                this.filterSelect("filtro-modelos-marca", "Todas las marcas")),
            this.filterGroup("Tipo de calzado", "filtro-modelos-tipo",
                this.filterSelect("filtro-modelos-tipo", "Todos los tipos")),
            this.filterGroup("Outlet", "filtro-modelos-outlet",
                this.filterSelect("filtro-modelos-outlet", "Todos", [
                    el("option", { value: "true", text: "Solo outlet" }),
                    el("option", { value: "false", text: "Sin outlet" })
                ])),
            this.filterGroup("Visible en web", "filtro-modelos-visible",
                this.filterSelect("filtro-modelos-visible", "Todos", [
                    el("option", { value: "true", text: "Visibles en la web" }),
                    el("option", { value: "false", text: "Ocultos en la web" })
                ])),
            el("button", { class: "btn btn-primary", type: "submit", text: "Filtrar" }),
            el("button", {
                class: "btn btn-outline", type: "button", text: "Limpiar",
                on: { click: () => this.limpiarFiltros() }
            }),
            el("button", {
                class: "btn btn-primary", type: "button", text: "Nuevo modelo",
                on: { click: () => this.abrirNuevoModelo() }
            })
        ]);

        return el("div", { class: "card" }, [
            el("div", { class: "card-title-row" }, [
                el("h3", { text: "Modelos" }),
                el("span", { class: "badge badge-primary", text: "Catálogo administrable" })
            ]),
            el("p", {
                class: "text-muted text-sm",
                text: "Listado administrativo de modelos. Los modelos con baja lógica siguen visibles para su gestión."
            }),
            filtros,
            el("p", { class: "text-muted text-sm", id: "modelos-conteo" }),
            el("div", { class: "table-scroll", id: "modelos-resultados" }),
            el("div", { id: "modelo-panel" })
        ]);
    }

    filterGroup(labelText, id, control) {
        return el("div", { class: "form-group" }, [
            el("label", { for: id, text: labelText }),
            control
        ]);
    }

    filterSelect(id, emptyLabel, options = []) {
        return el("select", {
            id,
            class: "form-control",
            on: { change: () => this.cargarModelos() }
        }, [
            el("option", { value: "", text: emptyLabel }),
            ...options
        ]);
    }

    filtros() {
        return {
            q: qs("#filtro-modelos-q")?.value?.trim() || "",
            marcaId: this.filtrosSelect?.marca?.value || "",
            tipoId: this.filtrosSelect?.tipo?.value || "",
            outlet: qs("#filtro-modelos-outlet")?.value || "",
            visibleWeb: qs("#filtro-modelos-visible")?.value || ""
        };
    }

    async cargarModelos() {
        if (!this.resultados) return;

        const generation = this.viewGeneration;
        // Sequence guard: a select change can fire while a previous request is in
        // flight, and only the latest response may render.
        const requestId = ++this.modelosRequestId;
        this.renderModelosLoading();

        try {
            const endpoint = apiQuery(CONFIG.ENDPOINTS.CATALOGO_ADMIN_MODELOS, this.filtros());
            const modelos = await fetchJson(endpoint);
            if (generation !== this.viewGeneration) return;
            if (requestId !== this.modelosRequestId) return;
            this.modelos = Array.isArray(modelos) ? modelos : [];
            this.renderModelos();
        } catch (error) {
            if (generation !== this.viewGeneration) return;
            if (requestId !== this.modelosRequestId) return;
            this.renderModelosError(error.message);
            NotificationService.error(`No se pudieron cargar los modelos: ${error.message}`);
        }
    }

    renderModelosLoading() {
        if (this.conteo) this.conteo.textContent = "Cargando modelos...";
        this.resultados.replaceChildren(loadingState("Cargando modelos..."));
    }

    renderModelos() {
        const total = this.modelos.length;
        if (this.conteo) {
            this.conteo.textContent = `${total} ${total === 1 ? "modelo" : "modelos"}`;
        }

        if (!total) {
            const vacio = emptyState(
                "Sin modelos para los filtros aplicados",
                "Ajusta los filtros o limpia la busqueda para ver todo el catalogo."
            );
            vacio.append(el("button", {
                class: "btn btn-outline btn-sm",
                text: "Limpiar filtros",
                on: { click: () => this.limpiarFiltros() }
            }));
            this.resultados.replaceChildren(vacio);
            return;
        }

        this.resultados.replaceChildren(table(
            ["Correlativo", "Modelo", "Marca", "Tipo", "Género",
                "Precio lista", "Precio efectivo", "Outlet", "Web", ""],
            this.modelos.map(modelo => this.modeloRow(modelo))
        ));
    }

    modeloRow(modelo) {
        return [
            el("span", { class: "cell-strong", text: String(modelo.correlativo ?? "-") }),
            el("span", { class: "cell-strong", text: modelo.nombre || "-" }),
            el("span", { text: modelo.marca || "-" }),
            el("span", { text: modelo.tipoCalzado || "-" }),
            el("span", { text: humanize(modelo.genero) }),
            el("span", { text: money(modelo.precioListaBase) }),
            el("span", { class: "cell-strong", text: money(modelo.precioEfectivo) }),
            this.outletBadge(modelo),
            this.visibilidadBadge(modelo),
            el("button", {
                class: "btn btn-outline btn-sm",
                text: "Detalle",
                on: { click: () => this.abrirDetalle(modelo.id) }
            })
        ];
    }

    outletBadge(modelo) {
        if (!modelo.esOutlet) {
            return el("span", { class: "badge badge-neutral", text: "Regular" });
        }
        const descuento = Number(modelo.porcentajeDescuento || 0);
        return el("span", {
            class: "badge badge-warning",
            text: descuento > 0 ? `-${descuento}%` : "Outlet"
        });
    }

    visibilidadBadge(modelo) {
        if (modelo.esVisibleWeb) {
            return el("span", { class: "badge badge-success", text: "Visible" });
        }
        return el("span", {
            class: "badge badge-danger",
            text: "Oculto",
            title: "Baja lógica: no visible en la tienda web"
        });
    }

    renderModelosError(message) {
        if (this.conteo) this.conteo.textContent = "";
        this.resultados.replaceChildren(errorState(message));
    }

    limpiarFiltros() {
        const q = qs("#filtro-modelos-q");
        if (q) q.value = "";
        if (this.filtrosSelect?.marca) this.filtrosSelect.marca.value = "";
        if (this.filtrosSelect?.tipo) this.filtrosSelect.tipo.value = "";
        const outlet = qs("#filtro-modelos-outlet");
        if (outlet) outlet.value = "";
        const visible = qs("#filtro-modelos-visible");
        if (visible) visible.value = "";
        this.cargarModelos();
    }

    // ------------------------------------------------------------------
    // Modelos: alta y edicion en panel en linea
    // ------------------------------------------------------------------

    abrirNuevoModelo() {
        // Invalidate any detail load still in flight so a late response cannot
        // reopen the panel on top of the new-model form.
        this.modeloDetalleRequestId += 1;
        this.modeloEditando = null;
        this.skuEditandoId = null;
        this.renderPanelModelo();
    }

    async abrirDetalle(id) {
        // R3-DETAIL-MUTATION-RACE: opening the detail is a read, so it never takes
        // the write gate, but it must not start while a write is in flight or the
        // painted data could predate that write's effect.
        if (this.warnIfMutating()) return;

        // R3-DETAIL-RACE: a slower earlier response must never render over a
        // newer one, so only the latest request id is allowed to paint the panel.
        const generation = this.viewGeneration;
        const requestId = ++this.modeloDetalleRequestId;
        const panel = qs("#modelo-panel");
        if (panel) panel.replaceChildren(loadingState("Cargando detalle del modelo…"));

        try {
            const endpoint = apiUrl(CONFIG.ENDPOINTS.CATALOGO_ADMIN_MODELOS, id);
            const detalle = await fetchJson(endpoint);
            if (generation !== this.viewGeneration) return;
            if (requestId !== this.modeloDetalleRequestId) return;
            this.modeloEditando = detalle;
            this.skuEditandoId = null;
            this.renderPanelModelo();
        } catch (error) {
            if (generation !== this.viewGeneration) return;
            if (requestId !== this.modeloDetalleRequestId) return;
            if (panel) panel.replaceChildren(errorState(error.message));
            NotificationService.error(`No se pudo cargar el detalle del modelo: ${error.message}`);
        }
    }

    cerrarPanelModelo() {
        // Closing invalidates the detail load too: no stale response may repaint.
        this.modeloDetalleRequestId += 1;
        this.modeloEditando = null;
        this.skuEditandoId = null;
        const panel = qs("#modelo-panel");
        if (panel) panel.replaceChildren();
    }

    /**
     * Shared refusal: the write gate uses it, and so do reads that must not start
     * while a write is in flight. It never takes the gate, only reports it.
     */
    warnIfMutating() {
        if (!this.panelMutationEnCurso) return false;
        NotificationService.info("Hay una operación en curso en el catálogo. Esperá a que termine.");
        return true;
    }

    /**
     * R3-MUTATION-OVERLAP: a single gate serializes the writes of the panel, so a
     * write never overlaps another write nor the re-render of a late one.
     */
    iniciarMutacionPanel() {
        if (this.warnIfMutating()) return false;
        this.panelMutationEnCurso = true;
        return true;
    }

    finalizarMutacionPanel(generation) {
        // A write whose view is gone must not reopen the gate: the unmount already
        // released it, and a newer mount may own it by now.
        if (generation !== undefined && generation !== this.viewGeneration) return;
        this.panelMutationEnCurso = false;
    }

    renderPanelModelo() {
        const panel = qs("#modelo-panel");
        if (!panel) return;
        const esEdicion = Boolean(this.modeloEditando);
        panel.replaceChildren(this.buildPanelModelo(esEdicion, this.modeloEditando || {}));
        panel.scrollIntoView({ behavior: "smooth", block: "start" });
    }

    buildPanelModelo(esEdicion, detalle) {
        const card = el("div", { class: "card modelo-detalle-panel" }, [
            el("div", { class: "card-title-row" }, [
                el("h3", { text: esEdicion ? `Editar modelo: ${detalle.nombre || ""}` : "Nuevo modelo" }),
                el("button", {
                    class: "btn btn-ghost btn-sm", type: "button", text: "Cerrar",
                    on: { click: () => this.cerrarPanelModelo() }
                })
            ]),
            el("p", {
                class: "text-muted text-sm",
                text: esEdicion
                    ? "Editá los datos descriptivos. El outlet y la visibilidad web se cambian con sus propias acciones."
                    : "Completá los datos del modelo. El correlativo se asigna automáticamente si lo dejás vacío."
            }),
            this.buildModeloForm(esEdicion, detalle)
        ]);

        if (esEdicion) {
            card.append(this.buildContextoEdicion(detalle));
            card.append(this.buildSkuBlock(detalle));
            card.append(this.buildOutletBlock(detalle));
            card.append(this.buildVisibilidadBlock(detalle));
        }
        return card;
    }

    buildModeloForm(esEdicion, detalle) {
        const tipoSelect = this.modeloSelect(
            "modelo-id-tipo", this.tipos, detalle.tipoCalzadoId,
            item => `${item.nombre} (inicial '${item.codigoLetra}')`, esEdicion ? "" : "Seleccioná un tipo");
        const marcaSelect = this.modeloSelect(
            "modelo-id-marca", this.marcas, detalle.marcaId,
            item => `${item.nombre} (inicial '${item.codigoLetra}')`, esEdicion ? "" : "Seleccioná una marca");

        const generoSelect = el("select", { id: "modelo-genero", class: "form-control" },
            ["HOMBRE", "MUJER", "UNISEX", "NINO"].map(value =>
                el("option", { value, text: this.generoLabel(value) })));
        generoSelect.value = detalle.genero || "UNISEX";

        const form = el("div", { class: "grid-2 mt-2" }, [
            this.campoModelo("Nombre del modelo", el("input", {
                type: "text", id: "modelo-nombre", class: "form-control",
                value: detalle.nombre || "", placeholder: "Ej: Tenis Runner Pro"
            })),
            this.campoModelo("Precio de lista base", el("input", {
                type: "number", step: "0.01", min: "0.01", id: "modelo-precio",
                class: "form-control", value: detalle.precioListaBase ?? ""
            }))
        ]);

        form.append(el("div", { class: "grid-3" }, [
            this.campoModelo("Tipo de calzado", tipoSelect),
            this.campoModelo("Marca / fabricante", marcaSelect),
            this.campoModelo("Género", generoSelect)
        ]));

        form.append(el("div", { class: "grid-3" }, [
            this.campoModelo("Correlativo (opcional)", el("input", {
                type: "number", min: "1", max: "999", id: "modelo-correlativo",
                class: "form-control", value: detalle.correlativo ?? "",
                placeholder: esEdicion ? "" : "Automático"
            })),
            this.campoModelo("Temporada", el("input", {
                type: "text", id: "modelo-temporada", class: "form-control",
                value: detalle.temporada || "", placeholder: "Ej: Verano 2025"
            })),
            this.campoModelo("Material exterior", el("input", {
                type: "text", id: "modelo-material-exterior", class: "form-control",
                value: detalle.materialExterior || ""
            }))
        ]));

        form.append(el("div", { class: "grid-3" }, [
            this.campoModelo("Material forro (opcional)", el("input", {
                type: "text", id: "modelo-material-forro", class: "form-control",
                value: detalle.materialForro || ""
            })),
            this.campoModelo("Tipo de suela", el("input", {
                type: "text", id: "modelo-tipo-suela", class: "form-control",
                value: detalle.tipoSuela || ""
            })),
            this.campoModelo("Tipo de cierre", el("input", {
                type: "text", id: "modelo-tipo-cierre", class: "form-control",
                value: detalle.tipoCierre || ""
            }))
        ]));

        form.append(el("div", { class: "toolbar mt-2" }, [
            el("button", {
                class: "btn btn-primary", type: "button", id: "btn-modelo-guardar",
                text: esEdicion ? "Guardar cambios" : "Crear modelo",
                on: { click: () => this.guardarModelo() }
            }),
            el("button", {
                class: "btn btn-outline", type: "button", text: "Cancelar",
                on: { click: () => this.cerrarPanelModelo() }
            })
        ]));

        return form;
    }

    buildContextoEdicion(detalle) {
        const colores = Array.isArray(detalle.colores) ? detalle.colores : [];
        const skus = Array.isArray(detalle.skus) ? detalle.skus : [];

        return el("div", { class: "card modelo-detalle-block mt-2" }, [
            el("div", { class: "card-title-row" }, [
                el("h3", { text: "Variantes y SKUs" }),
                el("span", { class: "badge badge-primary", text: "Colores y fotos" })
            ]),
            el("p", {
                class: "text-muted text-sm",
                text: "Una variante es el par (modelo, color) y no se puede repetir. Cada variante tiene su propia foto."
            }),
            this.buildVariantesBlock(colores),
            el("p", { class: "text-sm mt-2", text: `SKUs del modelo: ${skus.length}` })
        ]);
    }

    buildVariantesBlock(colores) {
        return el("div", { class: "mt-2" }, [
            el("h4", { class: "variant-block-title", text: "Colores" }),
            this.buildAgregarColorForm(colores),
            colores.length
                ? el("div", { class: "variant-list" },
                    colores.map(variante => this.buildVarianteItem(variante)))
                : el("p", { class: "text-muted text-sm", text: "Sin variantes de color cargadas." })
        ]);
    }

    buildAgregarColorForm(colores) {
        // A variant is the (modelo, color) pair, so already-used colours are not offered
        // again. colorId is the cat_color id (the colour), not the modelo_color id.
        const usados = new Set(colores.map(color => color.colorId));
        const disponibles = this.colores.filter(color => !usados.has(color.id));

        if (!disponibles.length) {
            return el("p", {
                class: "text-muted text-sm",
                text: this.colores.length
                    ? "Todos los colores disponibles ya están asociados al modelo."
                    : "No se pudieron cargar los colores disponibles."
            });
        }

        const select = el("select", { id: "variante-color-select", class: "form-control" }, [
            el("option", { value: "", text: "Seleccioná un color" }),
            ...disponibles.map(color => el("option", { value: color.id, text: color.nombre }))
        ]);

        return el("div", { class: "toolbar mt-2" }, [
            el("div", { class: "form-group toolbar-grow" }, [
                el("label", { for: "variante-color-select", text: "Color disponible" }),
                select
            ]),
            el("button", {
                class: "btn btn-primary", type: "button", id: "btn-variante-agregar",
                text: "Agregar color",
                on: { click: () => this.agregarColor() }
            })
        ]);
    }

    buildVarianteItem(variante) {
        const tieneFoto = Boolean(variante.fotoPrincipalUrl);
        const totalSkus = this.skusDeVariante(variante.id).length;

        const archivoInput = el("input", {
            type: "file", class: "form-control variant-file", accept: "image/*",
            "aria-label": `Foto de la variante ${variante.color || ""}`
        });

        const botonSubir = el("button", {
            class: "btn btn-outline btn-sm", type: "button", text: "Subir foto",
            on: { click: () => this.subirImagenColor(variante, archivoInput, botonSubir) }
        });

        const botonQuitar = el("button", {
            class: "btn btn-danger btn-sm", type: "button", text: "Quitar foto",
            on: { click: () => this.quitarImagenColor(variante, botonQuitar) }
        });
        botonQuitar.disabled = !tieneFoto;

        return el("div", { class: "variant-item" }, [
            this.colorSwatch(variante.codigoHex),
            this.varianteFoto(variante, tieneFoto),
            el("div", { class: "variant-body" }, [
                el("div", { class: "variant-heading" }, [
                    el("strong", { text: variante.color || `Color ${variante.colorId ?? "-"}` }),
                    el("span", { class: "badge badge-neutral", text: `SKUs: ${totalSkus}` }),
                    variante.codigoHex
                        ? el("span", { class: "text-muted text-sm", text: variante.codigoHex })
                        : null
                ]),
                tieneFoto
                    ? el("p", {
                        class: "text-muted text-sm variant-hint",
                        text: "Reemplazar la foto elimina el archivo anterior y su URL deja de estar disponible."
                    })
                    : null,
                archivoInput,
                el("div", { class: "inline-actions" }, [botonSubir, botonQuitar])
            ])
        ]);
    }

    varianteFoto(variante, tieneFoto) {
        if (!tieneFoto) {
            return el("div", { class: "variant-photo" }, [
                el("span", { class: "text-muted text-sm", text: "Sin foto" })
            ]);
        }
        return el("div", { class: "variant-photo" }, [
            el("img", {
                src: variante.fotoPrincipalUrl,
                alt: `Foto de la variante ${variante.color || ""}`
            })
        ]);
    }

    /**
     * Colour chip built as an SVG rectangle: the hex travels as the {@code fill}
     * presentation attribute, so no inline CSS is needed for a dynamic colour.
     */
    colorSwatch(hex) {
        const svgNs = "http://www.w3.org/2000/svg";
        const swatch = document.createElementNS(svgNs, "svg");
        swatch.setAttribute("viewBox", "0 0 24 24");
        swatch.setAttribute("class", "variant-swatch");
        swatch.setAttribute("role", "img");
        swatch.setAttribute("aria-label", `Muestra del color ${hex || "sin definir"}`);

        const rect = document.createElementNS(svgNs, "rect");
        rect.setAttribute("width", "24");
        rect.setAttribute("height", "24");
        rect.setAttribute("rx", "4");
        rect.setAttribute("fill", this.hexSeguro(hex));
        swatch.append(rect);
        return swatch;
    }

    hexSeguro(hex) {
        return /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.test(hex || "") ? hex : "#cbd5e1";
    }

    async agregarColor() {
        const id = this.modeloEditando?.id;
        if (!id) return;

        const idColor = qs("#variante-color-select")?.value || "";
        if (!idColor) {
            NotificationService.warning("Seleccioná un color para agregar la variante.");
            return;
        }
        if (!this.iniciarMutacionPanel()) return;
        const generation = this.viewGeneration;

        const button = qs("#btn-variante-agregar");
        if (button) { button.disabled = true; button.textContent = "Agregando…"; }

        try {
            const endpoint = apiUrl(CONFIG.ENDPOINTS.CATALOGO_ADMIN_MODELOS, id, "colores");
            const variante = await fetchJson(endpoint, {
                method: "POST",
                body: { idColor: Number(idColor) }
            });
            if (generation !== this.viewGeneration) return;
            // Re-render from this write's own response; a duplicate 400 never reaches here.
            if (!this.actualizarVarianteLocal(variante, id)) return;
            NotificationService.success(`Color ${variante.color || ""} agregado al modelo.`);
            await this.refrescarDetallePanel(id);
        } catch (error) {
            if (generation !== this.viewGeneration) return;
            NotificationService.error(`No se pudo agregar el color: ${error.message}`);
        } finally {
            if (button) { button.disabled = false; button.textContent = "Agregar color"; }
            this.finalizarMutacionPanel(generation);
        }
    }

    async subirImagenColor(variante, input, button) {
        const id = this.modeloEditando?.id;
        if (!id) return;

        const archivo = input?.files?.[0];
        if (!archivo) {
            NotificationService.warning("Seleccioná una imagen antes de subir.");
            return;
        }
        if (archivo.size > MAX_IMAGEN_BYTES) {
            NotificationService.warning("La imagen supera el límite de 10 MB permitido por el servidor.");
            return;
        }
        if (archivo.type && !archivo.type.startsWith("image/")) {
            NotificationService.warning("El archivo debe ser una imagen (JPG, PNG o WEBP).");
            return;
        }
        if (!this.iniciarMutacionPanel()) return;
        const generation = this.viewGeneration;

        if (button) { button.disabled = true; button.textContent = "Subiendo…"; }

        try {
            const formData = new FormData();
            formData.append("imagen", archivo);
            // The path parameter is the cat_color id (the colour), NOT the
            // modelo_color id: a variant is the (modelo, color) pair.
            const endpoint = apiUrl(
                CONFIG.ENDPOINTS.CATALOGO_ADMIN_MODELOS, id, "colores", variante.colorId, "imagen");
            const actualizada = await fetchJson(endpoint, { method: "POST", body: formData });
            if (generation !== this.viewGeneration) return;
            // The previous file is deleted server-side, so the old url must not be kept.
            if (!this.actualizarVarianteLocal(actualizada, id)) return;
            NotificationService.success(`Foto de la variante ${actualizada.color || ""} actualizada.`);
            await this.refrescarDetallePanel(id);
        } catch (error) {
            if (generation !== this.viewGeneration) return;
            NotificationService.error(`No se pudo subir la foto: ${error.message}`);
        } finally {
            if (button) { button.disabled = false; button.textContent = "Subir foto"; }
            this.finalizarMutacionPanel(generation);
        }
    }

    async quitarImagenColor(variante, button) {
        const id = this.modeloEditando?.id;
        if (!id || !variante?.fotoPrincipalUrl) return;
        if (!this.iniciarMutacionPanel()) return;
        const generation = this.viewGeneration;

        try {
            // Deleting the file is irreversible: no confirmation, no request.
            const confirmado = await NotificationService.confirm(
                `Se quitará la foto de ${variante.color || "esta variante"}. El archivo se elimina y no se puede recuperar. ¿Continuar?`,
                { confirmLabel: "Quitar foto" });
            if (!confirmado) return;
            if (generation !== this.viewGeneration) return;

            if (button) { button.disabled = true; button.textContent = "Quitando…"; }
            // Same route shape as the upload: the path parameter is the cat_color id
            // (the colour), and the variant is the (modelo, color) pair.
            const endpoint = apiUrl(
                CONFIG.ENDPOINTS.CATALOGO_ADMIN_MODELOS, id, "colores", variante.colorId, "imagen");
            const actualizada = await fetchJson(endpoint, { method: "DELETE" });
            if (generation !== this.viewGeneration) return;
            if (!this.actualizarVarianteLocal(actualizada, id)) return;
            NotificationService.success("Foto de la variante eliminada.");
            await this.refrescarDetallePanel(id);
        } catch (error) {
            if (generation !== this.viewGeneration) return;
            NotificationService.error(`No se pudo quitar la foto: ${error.message}`);
        } finally {
            if (button) { button.disabled = false; button.textContent = "Quitar foto"; }
            this.finalizarMutacionPanel(generation);
        }
    }

    /**
     * Folds a colour-variant write response into the local detail, so the next
     * render paints exactly what this write persisted. Returns false when the
     * panel has moved to a different model, in which case the caller must not
     * render this response on top of it.
     */
    actualizarVarianteLocal(variante, modeloId) {
        if (!this.modeloEditando || !variante) return false;
        if (modeloId !== undefined && this.modeloEditando.id !== modeloId) return false;
        const colores = Array.isArray(this.modeloEditando.colores)
            ? [...this.modeloEditando.colores]
            : [];
        // Variant responses are matched by colorId, which is the cat_color id (the
        // colour); the variant itself is the (modelo, color) pair.
        const indice = colores.findIndex(color => color.colorId === variante.colorId);
        if (indice >= 0) {
            colores[indice] = variante;
        } else {
            colores.push(variante);
        }
        this.modeloEditando = { ...this.modeloEditando, colores };
        return true;
    }

    // ------------------------------------------------------------------
    // SKUs del modelo: alta, edicion en linea, baja logica y reactivacion
    // ------------------------------------------------------------------

    skusDeVariante(idModeloColor) {
        const skus = Array.isArray(this.modeloEditando?.skus) ? this.modeloEditando.skus : [];
        return skus.filter(sku => String(sku.idModeloColor) === String(idModeloColor));
    }

    buildSkuBlock(detalle) {
        const colores = Array.isArray(detalle.colores) ? detalle.colores : [];
        const skus = Array.isArray(detalle.skus) ? detalle.skus : [];

        return el("div", { class: "card modelo-detalle-block mt-2" }, [
            el("div", { class: "card-title-row" }, [
                el("h3", { text: "SKUs del modelo" }),
                el("span", { class: "badge badge-primary", text: "Código generado por el sistema" })
            ]),
            el("p", {
                class: "text-muted text-sm",
                text: "Cada SKU se genera automáticamente a partir del modelo, el color y la talla: el código lo calcula el sistema y nunca se escribe a mano. La talla forma parte de la identidad del SKU, por eso no se puede cambiar después; si necesitás otra talla, agregá un SKU nuevo y dá de baja el incorrecto."
            }),
            this.buildAgregarSkuForm(colores),
            el("div", { id: "sku-tabla" }, [this.buildSkuTable(colores, skus)])
        ]);
    }

    buildAgregarSkuForm(colores) {
        // A SKU is the (variant, size) pair, so the operator cannot pick a pair that already exists.
        if (!colores.length) {
            return el("p", {
                class: "text-muted text-sm",
                text: "Este modelo todavía no tiene variantes de color. Agregá una variante para poder generar SKUs."
            });
        }

        const varianteSelect = el("select", {
            id: "sku-variante-select",
            class: "form-control",
            on: { change: () => this.actualizarTallasDisponibles() }
        }, [
            el("option", { value: "", text: "Seleccioná una variante" }),
            ...colores.map(variante => el("option", {
                value: variante.id,
                text: variante.color || `Color ${variante.colorId ?? "-"}`
            }))
        ]);

        return el("div", { class: "mt-2" }, [
            el("h4", { class: "variant-block-title", text: "Agregar SKU" }),
            el("div", { class: "grid-3" }, [
                this.campoModelo("Variante de color", varianteSelect),
                el("div", { class: "form-group" }, [
                    el("label", { for: "sku-talla-select", text: "Talla" }),
                    el("select", { id: "sku-talla-select", class: "form-control" }, [
                        el("option", { value: "", text: "Seleccioná una variante primero" })
                    ])
                ]),
                this.campoModelo("Código de barras EAN (opcional)", el("input", {
                    type: "text", id: "sku-ean", class: "form-control",
                    placeholder: "Entre 8 y 14 dígitos"
                }))
            ]),
            el("div", { class: "grid-3" }, [
                this.campoModelo("Peso en gramos (opcional)", el("input", {
                    type: "number", min: "1", step: "1", id: "sku-peso",
                    class: "form-control", placeholder: "800"
                })),
                el("div", { class: "form-group" }, [
                    el("span", {
                        class: "text-muted text-sm",
                        text: "Si dejás el peso vacío se guarda el valor por defecto de 800 g."
                    })
                ])
            ]),
            el("p", {
                class: "text-muted text-sm", id: "sku-talla-aviso",
                text: "Elegí una variante para ver las tallas disponibles."
            }),
            el("div", { class: "toolbar mt-2" }, [
                el("button", {
                    class: "btn btn-primary", type: "button", id: "btn-sku-agregar",
                    text: "Agregar SKU", disabled: true,
                    on: { click: () => this.agregarSku() }
                })
            ])
        ]);
    }

    /** Recomputes the size options from the selected variant, excluding the pairs already used. */
    actualizarTallasDisponibles() {
        const select = qs("#sku-talla-select");
        const aviso = qs("#sku-talla-aviso");
        const boton = qs("#btn-sku-agregar");
        if (!select) return;

        const fijarAviso = texto => { if (aviso) aviso.textContent = texto; };
        const habilitar = valor => { if (boton) boton.disabled = !valor; };
        const idVariante = qs("#sku-variante-select")?.value || "";

        if (!idVariante) {
            select.replaceChildren(el("option", { value: "", text: "Seleccioná una variante primero" }));
            fijarAviso("Elegí una variante para ver las tallas disponibles.");
            habilitar(false);
            return;
        }

        const activas = (this.tallas || []).filter(talla => talla.active === true);
        if (!activas.length) {
            select.replaceChildren(el("option", { value: "", text: "Sin tallas activas" }));
            fijarAviso("No hay tallas activas en el catálogo. Activá una talla para poder generar SKUs.");
            habilitar(false);
            return;
        }

        // Inactive SKUs keep their code reserved, so their size still counts as used.
        const usadas = new Set(this.skusDeVariante(idVariante).map(sku => Number(sku.tallaEur)));
        const disponibles = activas.filter(talla => !usadas.has(Number(talla.tallaEur)));

        if (!disponibles.length) {
            select.replaceChildren(el("option", { value: "", text: "Sin tallas disponibles" }));
            fijarAviso("Esta variante ya tiene un SKU para cada talla activa. Editá los existentes o dá de baja el incorrecto.");
            habilitar(false);
            return;
        }

        select.replaceChildren(
            el("option", { value: "", text: "Seleccioná una talla" }),
            ...disponibles.map(talla => el("option", { value: talla.id, text: this.tallaLabel(talla) }))
        );
        fijarAviso(`${disponibles.length} ${disponibles.length === 1 ? "talla disponible" : "tallas disponibles"} para esta variante.`);
        habilitar(true);
    }

    tallaLabel(talla) {
        const cm = talla?.tallaCmMx;
        return cm === null || cm === undefined
            ? `Talla ${talla?.tallaEur ?? "-"}`
            : `Talla ${talla.tallaEur} (${cm} cm)`;
    }

    tallaTexto(sku) {
        const cm = sku?.tallaCmMx;
        return cm === null || cm === undefined
            ? String(sku?.tallaEur ?? "-")
            : `${sku.tallaEur} (${cm} cm)`;
    }

    buildSkuTable(colores, skus) {
        if (!skus.length) {
            return emptyState(
                "Este modelo todavía no tiene SKUs",
                "Usá el formulario de arriba para generar el primero."
            );
        }

        // Deterministic order: the variant order shown above, then the size ascending.
        const ordenVariante = new Map(colores.map((variante, indice) => [String(variante.id), indice]));
        const posicion = variante => ordenVariante.has(String(variante))
            ? ordenVariante.get(String(variante))
            : Number.MAX_SAFE_INTEGER;
        const ordenados = [...skus].sort((a, b) => {
            const ia = posicion(a.idModeloColor);
            const ib = posicion(b.idModeloColor);
            if (ia !== ib) return ia - ib;
            const tallaA = Number(a.tallaEur ?? 0);
            const tallaB = Number(b.tallaEur ?? 0);
            if (tallaA !== tallaB) return tallaA - tallaB;
            return Number(a.id ?? 0) - Number(b.id ?? 0);
        });

        const colorPorId = new Map(colores.map(variante => [String(variante.id), variante]));
        const tabla = table(
            ["Código", "Color", "Talla", "EAN", "Peso", "Precio vigente", "Estado", "Acciones"],
            ordenados.map(sku => this.buildSkuRow(sku, colorPorId))
        );

        return el("div", { class: "mt-2" }, [
            el("div", { class: "table-scroll" }, [tabla]),
            this.skuEditandoId
                ? el("p", {
                    class: "text-muted text-sm mt-2",
                    text: "La talla y el código del SKU no se editan: la talla forma parte del código impreso. Para otra talla, generá un SKU nuevo y dá de baja el incorrecto."
                })
                : null
        ]);
    }

    buildSkuRow(sku, colorPorId) {
        const inactivo = sku.active !== true;
        const nombreColor = colorPorId.get(String(sku.idModeloColor))?.color || "-";

        if (this.skuEditandoId === sku.id) {
            return [
                this.celdaSku(sku.sku || "-", false, { strong: true }),
                this.celdaSku(nombreColor, false),
                this.celdaSku(this.tallaTexto(sku), false),
                el("input", {
                    type: "text", id: "sku-edit-ean", class: "form-control",
                    value: sku.codigoBarrasEan || "", placeholder: "Sin EAN"
                }),
                el("input", {
                    type: "number", min: "1", step: "1", id: "sku-edit-peso",
                    class: "form-control", value: sku.pesoGramos ?? "", placeholder: "800"
                }),
                this.celdaSku(money(sku.precioVentaVigente), false),
                this.skuEstadoBadge(sku),
                el("div", { class: "inline-actions" }, [
                    el("button", {
                        class: "btn btn-primary btn-sm", type: "button", id: "btn-sku-edit-guardar",
                        text: "Guardar", on: { click: () => this.guardarEdicionSku(sku) }
                    }),
                    el("button", {
                        class: "btn btn-outline btn-sm", type: "button", text: "Cancelar",
                        on: { click: () => this.cancelarEdicionSku() }
                    })
                ])
            ];
        }

        return [
            this.celdaSku(sku.sku || "-", inactivo, { strong: true }),
            this.celdaSku(nombreColor, inactivo),
            this.celdaSku(this.tallaTexto(sku), inactivo),
            this.celdaSku(sku.codigoBarrasEan || "-", inactivo),
            this.celdaSku(sku.pesoGramos != null ? `${sku.pesoGramos} g` : "-", inactivo),
            this.celdaSku(money(sku.precioVentaVigente), inactivo),
            this.skuEstadoBadge(sku),
            el("div", { class: "inline-actions" }, [
                el("button", {
                    class: "btn btn-outline btn-sm", type: "button", text: "Editar",
                    on: { click: () => this.editarSku(sku) }
                }),
                sku.active
                    ? el("button", {
                        class: "btn btn-danger btn-sm", type: "button", text: "Dar de baja",
                        on: { click: () => this.cambiarEstadoSku(sku) }
                    })
                    : el("button", {
                        class: "btn btn-success btn-sm", type: "button", text: "Reactivar",
                        on: { click: () => this.cambiarEstadoSku(sku) }
                    })
            ])
        ];
    }

    celdaSku(value, inactivo, { strong = false } = {}) {
        const clases = [];
        if (strong) clases.push("cell-strong");
        if (inactivo) clases.push("text-muted");
        return el("span", { class: clases.join(" ") || null, text: value });
    }

    skuEstadoBadge(sku) {
        return sku.active
            ? el("span", { class: "badge badge-success", text: "Activo" })
            : el("span", { class: "badge badge-neutral", text: "Inactivo" });
    }

    renderSkuTable() {
        const contenedor = qs("#sku-tabla");
        if (!contenedor || !this.modeloEditando) return;
        const colores = Array.isArray(this.modeloEditando.colores) ? this.modeloEditando.colores : [];
        const skus = Array.isArray(this.modeloEditando.skus) ? this.modeloEditando.skus : [];
        contenedor.replaceChildren(this.buildSkuTable(colores, skus));
    }

    editarSku(sku) {
        if (!sku?.id) return;
        this.skuEditandoId = sku.id;
        this.renderSkuTable();
    }

    cancelarEdicionSku() {
        this.skuEditandoId = null;
        this.renderSkuTable();
    }

    /**
     * Client-side EAN shape check: 8 to 14 digits. The backend still validates;
     * this only avoids a pointless round trip.
     */
    isValidEan(ean) {
        return /^\d{8,14}$/.test(ean);
    }

    async agregarSku() {
        const id = this.modeloEditando?.id;
        if (!id) return;

        const idVariante = qs("#sku-variante-select")?.value || "";
        const idTalla = qs("#sku-talla-select")?.value || "";
        if (!idVariante) {
            NotificationService.warning("Seleccioná una variante de color para generar el SKU.");
            return;
        }
        if (!idTalla) {
            NotificationService.warning("Seleccioná una talla disponible para esa variante.");
            return;
        }

        // Blank optional fields are omitted, never sent as empty strings.
        const body = { idModeloColor: Number(idVariante), idTalla: Number(idTalla) };
        const ean = (qs("#sku-ean")?.value || "").trim();
        if (ean !== "") {
            if (!this.isValidEan(ean)) {
                NotificationService.warning("El código de barras EAN debe tener entre 8 y 14 dígitos.");
                return;
            }
            body.codigoBarrasEan = ean;
        }
        const pesoTexto = (qs("#sku-peso")?.value || "").trim();
        if (pesoTexto !== "") {
            const peso = Number(pesoTexto);
            if (!Number.isInteger(peso) || peso <= 0) {
                NotificationService.warning("El peso debe ser un número entero de gramos mayor que 0.");
                return;
            }
            body.pesoGramos = peso;
        }

        if (!this.iniciarMutacionPanel()) return;
        const generation = this.viewGeneration;

        const button = qs("#btn-sku-agregar");
        if (button) { button.disabled = true; button.textContent = "Agregando…"; }

        try {
            const sku = await fetchJson(CONFIG.ENDPOINTS.CATALOGO_ADMIN_SKUS, { method: "POST", body });
            if (generation !== this.viewGeneration) return;
            // Fold the response in before the refresh so a failed refresh still
            // paints the state this write persisted.
            this.mergeSkuIntoDetail(sku, id);
            NotificationService.success(`SKU ${sku?.sku || ""} agregado al modelo.`);
            await this.refrescarDetallePanel(id);
        } catch (error) {
            if (generation !== this.viewGeneration) return;
            NotificationService.error(`No se pudo agregar el SKU: ${error.message}`);
        } finally {
            if (button) { button.disabled = false; button.textContent = "Agregar SKU"; }
            this.finalizarMutacionPanel(generation);
        }
    }

    async guardarEdicionSku(sku) {
        const id = this.modeloEditando?.id;
        if (!id || !sku?.id) return;

        // An empty EAN is sent on purpose: it clears the stored value. A blank weight
        // is omitted so the backend keeps the current one.
        const ean = (qs("#sku-edit-ean")?.value || "").trim();
        if (ean !== "" && !this.isValidEan(ean)) {
            NotificationService.warning("El código de barras EAN debe tener entre 8 y 14 dígitos.");
            return;
        }
        const body = { codigoBarrasEan: ean };
        const pesoTexto = (qs("#sku-edit-peso")?.value || "").trim();
        if (pesoTexto !== "") {
            const peso = Number(pesoTexto);
            if (!Number.isInteger(peso) || peso <= 0) {
                NotificationService.warning("El peso debe ser un número entero de gramos mayor que 0.");
                return;
            }
            body.pesoGramos = peso;
        }

        if (!this.iniciarMutacionPanel()) return;
        const generation = this.viewGeneration;

        const button = qs("#btn-sku-edit-guardar");
        if (button) { button.disabled = true; button.textContent = "Guardando…"; }

        try {
            const endpoint = apiUrl(CONFIG.ENDPOINTS.CATALOGO_ADMIN_SKUS, sku.id);
            const actualizado = await fetchJson(endpoint, { method: "PUT", body });
            if (generation !== this.viewGeneration) return;
            // Fold the response in before the refresh so a failed refresh still
            // paints the state this write persisted.
            this.mergeSkuIntoDetail(actualizado, id);
            this.skuEditandoId = null;
            NotificationService.success(`SKU ${actualizado?.sku || sku.sku || ""} actualizado.`);
            await this.refrescarDetallePanel(id);
        } catch (error) {
            if (generation !== this.viewGeneration) return;
            NotificationService.error(`No se pudo actualizar el SKU: ${error.message}`);
        } finally {
            if (button) { button.disabled = false; button.textContent = "Guardar"; }
            this.finalizarMutacionPanel(generation);
        }
    }

    async cambiarEstadoSku(sku) {
        const id = this.modeloEditando?.id;
        if (!id || !sku?.id) return;

        // The rendered row can drift from the authoritative detail, so the target
        // state comes from the current SKU in the open model, never from the row.
        const actual = (Array.isArray(this.modeloEditando?.skus) ? this.modeloEditando.skus : [])
            .find(item => item.id === sku.id);
        const activar = (actual || sku).active !== true;

        // The gate is taken before the dialog: a write already in flight must reject
        // this one before the operator is asked to confirm an action that cannot run.
        if (!this.iniciarMutacionPanel()) return;
        const generation = this.viewGeneration;

        try {
            if (!activar) {
                const confirmado = await NotificationService.confirm(
                    `Dar de baja el SKU ${sku.sku || ""} es una baja lógica: el código queda reservado y el SKU se conserva para su gestión. ¿Dar de baja?`,
                    { confirmLabel: "Dar de baja" });
                if (!confirmado) return;
                if (generation !== this.viewGeneration) return;
            }

            const endpoint = apiUrl(CONFIG.ENDPOINTS.CATALOGO_ADMIN_SKUS, sku.id, "active");
            const actualizado = await fetchJson(endpoint, {
                method: "PATCH",
                body: { active: activar }
            });
            if (generation !== this.viewGeneration) return;
            // Fold the response in before the refresh so a failed refresh still
            // paints the state this write persisted.
            this.mergeSkuIntoDetail(actualizado, id);
            NotificationService.success(activar
                ? `SKU ${actualizado?.sku || sku.sku || ""} reactivado.`
                : `SKU ${actualizado?.sku || sku.sku || ""} dado de baja.`);
            await this.refrescarDetallePanel(id);
        } catch (error) {
            if (generation !== this.viewGeneration) return;
            NotificationService.error(`No se pudo ${activar ? "reactivar" : "dar de baja"} el SKU: ${error.message}`);
        } finally {
            this.finalizarMutacionPanel(generation);
        }
    }

    /**
     * Returns a new list with the entry replaced by id, or appended when absent.
     * Folding a write response into local state lets a failed refresh still paint
     * exactly what the write persisted.
     */
    mergeById(list, entry) {
        const items = Array.isArray(list) ? [...list] : [];
        if (!entry) return items;
        const index = items.findIndex(item => item.id === entry.id);
        if (index >= 0) {
            items[index] = entry;
        } else {
            items.push(entry);
        }
        return items;
    }

    /**
     * Folds a SKU write response into the local detail, so the next render paints
     * exactly what this write persisted and a failed refresh cannot show pre-write
     * data. Returns false when the panel has moved to a different model.
     */
    mergeSkuIntoDetail(sku, modeloId) {
        if (!this.modeloEditando || !sku) return false;
        if (modeloId !== undefined && this.modeloEditando.id !== modeloId) return false;
        this.modeloEditando = {
            ...this.modeloEditando,
            skus: this.mergeById(this.modeloEditando.skus, sku)
        };
        return true;
    }

    /**
     * Single post-mutation refresh path. Re-reads the model detail from the API so the
     * SKU table, the variant SKU counts and the effective price all come from one fresh
     * source, then repaints the panel and the listing.
     */
    async refrescarDetallePanel(modeloId) {
        const generation = this.viewGeneration;
        if (this.modeloEditando && this.modeloEditando.id === modeloId) {
            try {
                const endpoint = apiUrl(CONFIG.ENDPOINTS.CATALOGO_ADMIN_MODELOS, modeloId);
                const detalle = await fetchJson(endpoint);
                if (generation !== this.viewGeneration) return;
                if (this.modeloEditando && this.modeloEditando.id === modeloId) {
                    this.modeloEditando = detalle;
                    this.renderPanelModelo();
                }
            } catch (error) {
                if (generation !== this.viewGeneration) return;
                // A failed refresh must not hide the panel, but the local detail is
                // the only truth left: say plainly that it may be stale.
                NotificationService.error(
                    `No se pudo actualizar el detalle del modelo: ${error.message}. El panel puede estar mostrando datos desactualizados.`);
                this.renderPanelModelo();
            }
        }
        await this.cargarModelos();
    }

    buildOutletBlock(detalle) {
        const descuento = Number(detalle.porcentajeDescuento || 0);

        const outletInput = el("input", { type: "checkbox", id: "modelo-outlet" });
        outletInput.checked = Boolean(detalle.esOutlet);

        const descuentoInput = el("input", {
            type: "number", min: "0", step: "0.01", id: "modelo-descuento",
            class: "form-control", value: descuento,
            on: {
                input: event => {
                    // Rule surfaced in the UI: a discount greater than 0 marks the model as outlet.
                    if (Number(event.target.value || 0) > 0) {
                        const check = qs("#modelo-outlet");
                        if (check) check.checked = true;
                    }
                }
            }
        });

        return el("div", { class: "card modelo-detalle-block mt-2" }, [
            el("div", { class: "card-title-row" }, [
                el("h3", { text: "Outlet" }),
                el("span", { class: "badge badge-warning", text: "Recalcula precios" })
            ]),
            el("p", {
                class: "text-muted text-sm",
                text: "Un descuento mayor a 0 convierte el modelo en outlet. Al guardar, se recalcula el precio de venta de TODOS los SKUs del modelo."
            }),
            el("div", { class: "grid-2 mt-2" }, [
                el("div", { class: "form-group" }, [
                    el("label", { for: "modelo-outlet", text: "Modelo en outlet" }),
                    el("label", { class: "checkbox-row" }, [
                        outletInput,
                        el("span", { text: "Marcar como outlet" })
                    ])
                ]),
                this.campoModelo("% Descuento outlet", descuentoInput)
            ]),
            el("div", { class: "toolbar mt-2" }, [
                el("button", {
                    class: "btn btn-primary", type: "button", id: "btn-modelo-outlet",
                    text: "Aplicar outlet", on: { click: () => this.aplicarOutlet() }
                })
            ])
        ]);
    }

    buildVisibilidadBlock(detalle) {
        const visibleInput = el("input", { type: "checkbox", id: "modelo-visible-web" });
        visibleInput.checked = Boolean(detalle.esVisibleWeb);

        return el("div", { class: "card modelo-detalle-block mt-2" }, [
            el("div", { class: "card-title-row" }, [
                el("h3", { text: "Visibilidad web" }),
                detalle.esVisibleWeb
                    ? el("span", { class: "badge badge-success", text: "Visible" })
                    : el("span", { class: "badge badge-danger", text: "Oculto" })
            ]),
            el("p", {
                class: "text-muted text-sm",
                text: "Ocultar un modelo es su baja lógica en la tienda web: deja de venderse en línea, pero el modelo y sus datos se conservan para su gestión. Volver a mostrarlo no pide confirmación."
            }),
            el("div", { class: "form-group mt-2" }, [
                el("label", { for: "modelo-visible-web", text: "Visible en la tienda web" }),
                el("label", { class: "checkbox-row" }, [
                    visibleInput,
                    el("span", { text: "Mostrar el modelo en la tienda web" })
                ])
            ]),
            el("div", { class: "toolbar mt-2" }, [
                el("button", {
                    class: "btn btn-primary", type: "button", id: "btn-modelo-visibilidad",
                    text: "Guardar visibilidad", on: { click: () => this.aplicarVisibilidad() }
                })
            ])
        ]);
    }

    campoModelo(labelText, control) {
        return el("div", { class: "form-group" }, [
            el("label", { for: control.id, text: labelText }),
            control
        ]);
    }

    modeloSelect(id, items, selectedValue, labelFor, placeholder) {
        const options = (items || []).map(item => el("option", { value: item.id, text: labelFor(item) }));
        const select = el("select", { id, class: "form-control" }, [
            placeholder ? el("option", { value: "", text: placeholder }) : null,
            ...options
        ]);
        if (!options.length) {
            select.append(el("option", { value: "", text: "Sin datos" }));
        }
        select.value = selectedValue === undefined || selectedValue === null
            ? "" : String(selectedValue);
        return select;
    }

    generoLabel(value) {
        const labels = { HOMBRE: "Hombre", MUJER: "Mujer", UNISEX: "Unisex", NINO: "Niño" };
        return labels[value] || value;
    }

    async guardarModelo() {
        const nombre = qs("#modelo-nombre")?.value?.trim() || "";
        const idTipoCalzado = qs("#modelo-id-tipo")?.value || "";
        const idMarca = qs("#modelo-id-marca")?.value || "";
        const correlativo = qs("#modelo-correlativo")?.value?.trim() || "";
        const genero = qs("#modelo-genero")?.value || "";
        const precio = qs("#modelo-precio")?.value || "";
        const temporada = qs("#modelo-temporada")?.value?.trim() || "";
        const materialExterior = qs("#modelo-material-exterior")?.value?.trim() || "";
        const materialForro = qs("#modelo-material-forro")?.value?.trim() || "";
        const tipoSuela = qs("#modelo-tipo-suela")?.value?.trim() || "";
        const tipoCierre = qs("#modelo-tipo-cierre")?.value?.trim() || "";

        if (!nombre || !idTipoCalzado || !idMarca || !genero || !precio || !temporada
            || !materialExterior || !tipoSuela || !tipoCierre) {
            NotificationService.warning(
                "Completá nombre, tipo, marca, género, precio, temporada, material exterior, suela y cierre.");
            return;
        }

        // Descriptive payload shared by create and update. Outlet and web visibility
        // are intentionally omitted on update, where null keeps the current value.
        const body = {
            idTipoCalzado: Number(idTipoCalzado),
            idMarca: Number(idMarca),
            nombre,
            materialExterior,
            materialForro: materialForro || null,
            tipoSuela,
            tipoCierre,
            genero,
            temporada,
            precioListaBase: Number(precio)
        };
        if (correlativo !== "") body.correlativo = Number(correlativo);

        const editando = Boolean(this.modeloEditando);
        const endpoint = editando
            ? apiUrl(CONFIG.ENDPOINTS.CATALOGO_ADMIN_MODELOS, this.modeloEditando.id)
            : CONFIG.ENDPOINTS.CATALOGO_ADMIN_MODELOS;

        if (!editando) {
            // Creation defaults: regular (non-outlet) and visible on the web.
            body.esOutlet = false;
            body.porcentajeDescuento = 0;
            body.esVisibleWeb = true;
        }

        if (!this.iniciarMutacionPanel()) return;
        const generation = this.viewGeneration;

        const button = qs("#btn-modelo-guardar");
        if (button) { button.disabled = true; button.textContent = "Guardando…"; }

        try {
            const data = await fetchJson(endpoint, { method: editando ? "PUT" : "POST", body });
            if (generation !== this.viewGeneration) return;
            if (editando) {
                NotificationService.success(`Modelo ${data.nombre || nombre} actualizado.`);
                this.modeloEditando = data;
                this.renderPanelModelo();
            } else {
                NotificationService.success(
                    `Modelo ${data.nombre || nombre} creado con correlativo ${data.correlativo ?? "-"}.`);
                this.cerrarPanelModelo();
            }
            await this.cargarModelos();
        } catch (error) {
            if (generation !== this.viewGeneration) return;
            NotificationService.error(`No se pudo guardar el modelo: ${error.message}`);
        } finally {
            if (button) {
                button.disabled = false;
                button.textContent = editando ? "Guardar cambios" : "Crear modelo";
            }
            this.finalizarMutacionPanel(generation);
        }
    }

    async aplicarOutlet() {
        const id = this.modeloEditando?.id;
        if (!id) return;

        const esOutlet = Boolean(qs("#modelo-outlet")?.checked);
        const porcentajeDescuento = Number(qs("#modelo-descuento")?.value || 0);

        if (!Number.isFinite(porcentajeDescuento) || porcentajeDescuento < 0) {
            NotificationService.warning("Ingresá un porcentaje de descuento válido (0 o mayor).");
            return;
        }

        if (!this.iniciarMutacionPanel()) return;
        const generation = this.viewGeneration;

        const endpoint = apiUrl(CONFIG.ENDPOINTS.CATALOGO_ADMIN_MODELOS, id, "outlet");
        const button = qs("#btn-modelo-outlet");

        try {
            const totalSkus = Array.isArray(this.modeloEditando.skus) ? this.modeloEditando.skus.length : 0;
            if (totalSkus > 0) {
                const confirmado = await NotificationService.confirm(
                    `Al aplicar el outlet se recalculará el precio de venta de los ${totalSkus} SKUs de este modelo. ¿Continuar?`,
                    { confirmLabel: "Aplicar outlet" });
                if (!confirmado) return;
                if (generation !== this.viewGeneration) return;
            }

            if (button) { button.disabled = true; button.textContent = "Aplicando…"; }
            const data = await fetchJson(endpoint, {
                method: "PATCH",
                body: { esOutlet, porcentajeDescuento }
            });
            if (generation !== this.viewGeneration) return;
            NotificationService.success("Outlet aplicado. Se recalcularon los precios de los SKUs.");
            this.modeloEditando = data;
            this.renderPanelModelo();
            await this.cargarModelos();
        } catch (error) {
            if (generation !== this.viewGeneration) return;
            NotificationService.error(`No se pudo aplicar el outlet: ${error.message}`);
        } finally {
            if (button) { button.disabled = false; button.textContent = "Aplicar outlet"; }
            this.finalizarMutacionPanel(generation);
        }
    }

    async aplicarVisibilidad() {
        const id = this.modeloEditando?.id;
        if (!id) return;

        const esVisibleWeb = Boolean(qs("#modelo-visible-web")?.checked);
        const estabaVisible = Boolean(this.modeloEditando.esVisibleWeb);

        if (!this.iniciarMutacionPanel()) return;
        const generation = this.viewGeneration;

        const endpoint = apiUrl(CONFIG.ENDPOINTS.CATALOGO_ADMIN_MODELOS, id, "visibilidad-web");
        const button = qs("#btn-modelo-visibilidad");

        try {
            if (!esVisibleWeb && estabaVisible) {
                const confirmado = await NotificationService.confirm(
                    "Ocultar el modelo es la baja lógica en la tienda web: deja de venderse en línea, pero el modelo y sus datos se conservan para su gestión. ¿Ocultar el modelo?",
                    { confirmLabel: "Ocultar de la web" });
                if (!confirmado) return;
                if (generation !== this.viewGeneration) return;
            }

            if (button) { button.disabled = true; button.textContent = "Guardando…"; }
            const data = await fetchJson(endpoint, {
                method: "PATCH",
                body: { esVisibleWeb }
            });
            if (generation !== this.viewGeneration) return;
            NotificationService.success(esVisibleWeb
                ? "Modelo visible en la tienda web."
                : "Modelo oculto: baja lógica aplicada sin borrar sus datos.");
            this.modeloEditando = data;
            this.renderPanelModelo();
            await this.cargarModelos();
        } catch (error) {
            if (generation !== this.viewGeneration) return;
            NotificationService.error(`No se pudo actualizar la visibilidad web: ${error.message}`);
        } finally {
            if (button) { button.disabled = false; button.textContent = "Guardar visibilidad"; }
            this.finalizarMutacionPanel(generation);
        }
    }

    buildForm() {
        const card = el("div", { class: "card" }, [
            el("div", { class: "card-title-row" }, [
                el("h3", { text: "Registrar nuevo calzado" }),
                el("span", { class: "badge badge-primary", text: "Persistencia en PostgreSQL" })
            ]),
            el("p", {
                class: "text-muted text-sm",
                text: "Al guardar se almacena la imagen, se calculan los SKUs por talla ([Tipo][Marca][Correlativo]-[Talla]) y se genera el stock inicial."
            })
        ]);

        const form = el("div", { class: "grid-2 mt-2" });

        const fields = el("div", {}, [
            el("div", { class: "grid-2" }, [
                this.field("Nombre del modelo", el("input", {
                    type: "text", id: "nuevo-prod-nombre", class: "form-control",
                    placeholder: "Ej: Tenis Runner Pro", value: "Tenis Runner Pro"
                })),
                this.field("Precio de lista base", el("input", {
                    type: "number", step: "0.01", id: "nuevo-prod-precio",
                    class: "form-control", value: "75.00"
                }))
            ]),
            el("div", { class: "grid-3" }, [
                this.field("Tipo de calzado", el("select", { id: "nuevo-prod-tipo", class: "form-control" })),
                this.field("Marca / fabricante", el("select", { id: "nuevo-prod-marca", class: "form-control" })),
                this.field("Color principal", el("select", { id: "nuevo-prod-color", class: "form-control" }))
            ]),
            el("div", { class: "grid-3" }, [
                this.field("Material exterior", el("input", {
                    type: "text", id: "nuevo-prod-material", class: "form-control",
                    value: "Cuero vacuno encerado"
                })),
                this.field("Tipo de suela", el("input", {
                    type: "text", id: "nuevo-prod-suela", class: "form-control",
                    value: "Goma vulcanizada antideslizante"
                })),
                this.field("Género", el("select", { id: "nuevo-prod-genero", class: "form-control" }, [
                    el("option", { value: "UNISEX", text: "UNISEX" }),
                    el("option", { value: "HOMBRE", text: "HOMBRE" }),
                    el("option", { value: "MUJER", text: "MUJER" })
                ]))
            ]),
            el("div", { class: "grid-3" }, [
                this.field("Temporada", el("input", {
                    type: "text", id: "nuevo-prod-temporada", class: "form-control",
                    value: "Verano 2025"
                })),
                el("div", { class: "form-group" }, [
                    el("label", { for: "nuevo-prod-outlet", text: "Producto outlet" }),
                    el("label", { class: "checkbox-row" }, [
                        el("input", { type: "checkbox", id: "nuevo-prod-outlet" }),
                        el("span", { text: "Marcar como liquidación" })
                    ])
                ]),
                this.field("% Descuento outlet", el("input", {
                    type: "number", id: "nuevo-prod-descuento", class: "form-control", value: "0"
                }))
            ]),
            this.field("Stock inicial por talla (bodega central)", el("input", {
                type: "number", id: "nuevo-prod-stock", class: "form-control", value: "15"
            }))
        ]);

        const media = el("div", { class: "sticky-column" }, [
            el("div", { class: "card mb-0 upload-panel" }, [
                el("h4", { text: "Imagen del producto" }),
                el("p", {
                    class: "text-muted text-sm",
                    text: "Se almacena en disco y se publica en la tienda online."
                }),
                el("div", { id: "img-preview-box", class: "image-preview" }, [
                    el("span", { class: "text-muted text-sm", text: "Vista previa" })
                ]),
                el("input", {
                    type: "file", id: "nuevo-prod-imagen", class: "form-control", accept: "image/*"
                })
            ]),
            el("button", {
                class: "btn btn-success btn-block mt-2",
                id: "btn-guardar-calzado",
                text: "Guardar calzado y generar SKUs",
                on: { click: () => this.save() }
            })
        ]);

        form.append(fields, media);
        card.append(form);
        return card;
    }

    field(labelText, control) {
        return el("div", { class: "form-group" }, [
            el("label", { for: control.id, text: labelText }),
            control
        ]);
    }

    async loadSelectors() {
        const generation = this.viewGeneration;
        const status = this.selects.tipo;
        status.replaceChildren(el("option", { text: "Cargando…" }));
        this.filtrosSelect.marca?.replaceChildren(el("option", { value: "", text: "Todas las marcas" }));
        this.filtrosSelect.tipo?.replaceChildren(el("option", { value: "", text: "Todos los tipos" }));

        try {
            const [tipos, marcas, colores] = await Promise.all([
                fetchJson(CONFIG.ENDPOINTS.CATALOGO_TIPOS),
                fetchJson(CONFIG.ENDPOINTS.CATALOGO_MARCAS),
                fetchJson(CONFIG.ENDPOINTS.CATALOGO_COLORES)
            ]);
            if (generation !== this.viewGeneration) return;
            this.fill(this.selects.tipo, tipos, item => `${item.nombre} (inicial '${item.codigoLetra}')`);
            this.fill(this.selects.marca, marcas, item => `${item.nombre} (inicial '${item.codigoLetra}')`);
            this.fill(this.selects.color, colores, item => item.nombre);
            this.tipos = Array.isArray(tipos) ? tipos : [];
            this.marcas = Array.isArray(marcas) ? marcas : [];
            this.fillFilter(this.filtrosSelect.marca, marcas, item => item.nombre, "Todas las marcas");
            this.fillFilter(this.filtrosSelect.tipo, tipos, item => item.nombre, "Todos los tipos");
            // Colours feed both the legacy quick-create select and the variant block.
            this.colores = Array.isArray(colores) ? colores : [];
        } catch (error) {
            if (generation !== this.viewGeneration) return;
            this.selects.tipo.replaceChildren(el("option", { text: "No disponible" }));
            NotificationService.error(`No se pudieron cargar los selectores: ${error.message}`);
        }
    }

    /**
     * Sizes feed the SKU creation form and the master-data section. A failed
     * refresh keeps the last known list so the section can still paint it, and the
     * message says plainly that the screen may be stale.
     */
    async cargarTallas({ refresh = false } = {}) {
        const generation = this.viewGeneration;
        try {
            const tallas = await fetchJson(CONFIG.ENDPOINTS.CATALOGO_ADMIN_TALLAS);
            if (generation !== this.viewGeneration) return;
            this.tallas = Array.isArray(tallas) ? tallas : [];
        } catch (error) {
            if (generation !== this.viewGeneration) return;
            // An initial load may clear the list; a refresh must not, or it would
            // wipe the state the write already folded in.
            if (!refresh) this.tallas = [];
            NotificationService.error(refresh
                ? `No se pudo actualizar la lista de tallas: ${error.message}. La sección de tallas puede estar mostrando datos desactualizados.`
                : `No se pudieron cargar las tallas: ${error.message}`);
        }
    }

    fillFilter(select, items, labelFor, emptyLabel) {
        if (!select) return;
        select.replaceChildren(el("option", { value: "", text: emptyLabel }));
        (items || []).forEach(item => {
            select.append(el("option", { value: item.id, text: labelFor(item) }));
        });
    }

    fill(select, items, labelFor) {
        if (!select) return;
        select.replaceChildren();
        (items || []).forEach(item => {
            select.append(el("option", { value: item.id, text: labelFor(item) }));
        });
        if (!items || !items.length) {
            select.append(el("option", { text: "Sin datos" }));
        }
    }

    bindPreview() {
        const input = qs("#nuevo-prod-imagen");
        const box = qs("#img-preview-box");
        if (!input || !box) return;

        input.addEventListener("change", event => {
            const file = event.target.files?.[0];
            box.replaceChildren();
            this.selectedFile = file || null;

            if (!file) {
                box.append(el("span", { class: "text-muted text-sm", text: "Vista previa" }));
                return;
            }

            const reader = new FileReader();
            reader.onload = loadEvent => {
                box.append(el("img", { src: loadEvent.target.result, alt: "Vista previa" }));
            };
            reader.readAsDataURL(file);
        });
    }

    async save() {
        const nombre = qs("#nuevo-prod-nombre")?.value?.trim();
        const precio = qs("#nuevo-prod-precio")?.value;

        if (!nombre || !precio) {
            NotificationService.warning("Completá nombre y precio de lista.");
            return;
        }

        const generation = this.viewGeneration;
        const button = qs("#btn-guardar-calzado");
        if (button) {
            button.disabled = true;
            button.textContent = "Guardando…";
        }

        const formData = new FormData();
        formData.append("nombre", nombre);
        formData.append("precioLista", precio);
        formData.append("idTipoCalzado", this.selects.tipo?.value ?? "");
        formData.append("idMarca", this.selects.marca?.value ?? "");
        formData.append("idColor", this.selects.color?.value ?? "");
        formData.append("materialExterior", qs("#nuevo-prod-material")?.value || "Cuero");
        formData.append("tipoSuela", qs("#nuevo-prod-suela")?.value || "Goma");
        formData.append("genero", qs("#nuevo-prod-genero")?.value || "UNISEX");
        formData.append("temporada", qs("#nuevo-prod-temporada")?.value || "Verano 2025");
        formData.append("esOutlet", qs("#nuevo-prod-outlet")?.checked ? "true" : "false");
        formData.append("porcentajeDescuento", qs("#nuevo-prod-descuento")?.value || "0");
        formData.append("stockInicialPorTalla", qs("#nuevo-prod-stock")?.value || "10");
        if (this.selectedFile) {
            formData.append("imagen", this.selectedFile);
        }

        try {
            const data = await fetchJson(`${CONFIG.ENDPOINTS.CATALOGO}/crear-con-imagen`, {
                method: "POST",
                body: formData
            });
            if (generation !== this.viewGeneration) return;
            NotificationService.success(
                `Calzado creado. SKUs generados: ${data.totalSkusGenerados}. Foto: ${data.fotoUrl}`
            );
            this.resetForm();
        } catch (error) {
            if (generation !== this.viewGeneration) return;
            NotificationService.error(`No se pudo guardar el calzado: ${error.message}`);
        } finally {
            if (button) {
                button.disabled = false;
                button.textContent = "Guardar calzado y generar SKUs";
            }
        }
    }

    resetForm() {
        qs("#nuevo-prod-nombre").value = "";
        qs("#nuevo-prod-precio").value = "";
        qs("#nuevo-prod-outlet").checked = false;
        const input = qs("#nuevo-prod-imagen");
        if (input) input.value = "";
        const box = qs("#img-preview-box");
        if (box) box.replaceChildren(el("span", { class: "text-muted text-sm", text: "Vista previa" }));
        this.selectedFile = null;
    }

    // ------------------------------------------------------------------
    // Tallas del catalogo: datos maestros (alta, edicion, baja y reactivacion)
    // ------------------------------------------------------------------

    buildTallasSection() {
        return el("div", { class: "card" }, [
            el("div", { class: "card-title-row" }, [
                el("h3", { text: "Tallas del catálogo" }),
                el("span", { class: "badge badge-primary", text: "Datos maestros" })
            ]),
            el("p", {
                class: "text-muted text-sm",
                text: "Las tallas son los datos de referencia del catálogo y cada SKU hace referencia a una. La talla EUR es la que se incrusta en el código del SKU, por eso no se puede editar después de crearla. Dar de baja una talla es una baja lógica: los SKUs ya generados conservan su talla y su código reservado."
            }),
            this.buildAgregarTallaForm(),
            el("div", { id: "tallas-resultados" })
        ]);
    }

    buildAgregarTallaForm() {
        return el("div", { class: "mt-2" }, [
            el("h4", { class: "variant-block-title", text: "Agregar talla" }),
            el("div", { class: "grid-3" }, [
                this.campoModelo("Talla EUR (obligatoria)", el("input", {
                    type: "number", id: "talla-nueva-eur", class: "form-control",
                    step: "0.1", min: "0.1", max: "99.9", placeholder: "Ej: 42.5"
                })),
                this.campoModelo("cm MX (obligatoria)", el("input", {
                    type: "number", id: "talla-nueva-cm", class: "form-control",
                    step: "0.1", min: "0.1", placeholder: "Ej: 27"
                })),
                this.campoModelo("US hombre (opcional)", el("input", {
                    type: "number", id: "talla-nueva-us-hombre", class: "form-control",
                    step: "0.5", placeholder: "Ej: 9"
                }))
            ]),
            el("div", { class: "grid-2" }, [
                this.campoModelo("US mujer (opcional)", el("input", {
                    type: "number", id: "talla-nueva-us-mujer", class: "form-control",
                    step: "0.5", placeholder: "Ej: 10.5"
                })),
                this.campoModelo("UK (opcional)", el("input", {
                    type: "number", id: "talla-nueva-uk", class: "form-control",
                    step: "0.5", placeholder: "Ej: 8"
                }))
            ]),
            el("div", { class: "toolbar mt-2" }, [
                el("button", {
                    class: "btn btn-primary", type: "button", id: "btn-talla-agregar",
                    text: "Agregar talla", on: { click: () => this.agregarTalla() }
                })
            ])
        ]);
    }

    renderTallasSection() {
        if (!this.tallasResultados) return;
        this.tallasResultados.replaceChildren(this.buildTallasTable());
    }

    buildTallasTable() {
        if (!this.tallas.length) {
            return emptyState(
                "Todavía no hay tallas en el catálogo",
                "Usá el formulario de arriba para agregar la primera talla."
            );
        }

        const tabla = table(
            ["Talla EUR", "US hombre", "US mujer", "UK", "cm MX", "Estado", "Acciones"],
            this.tallas.map(talla => this.buildTallaRow(talla))
        );

        return el("div", { class: "mt-2" }, [
            el("div", { class: "table-scroll" }, [tabla]),
            this.tallaEditandoId
                ? el("p", {
                    class: "text-muted text-sm mt-2",
                    text: "La talla EUR no se edita: forma parte del código de los SKUs ya generados. Dejá un campo vacío para conservar su valor actual."
                })
                : null
        ]);
    }

    buildTallaRow(talla) {
        const inactivo = talla.active !== true;

        if (this.tallaEditandoId === talla.id) {
            return [
                this.celdaTalla(String(talla.tallaEur ?? "-"), false, { strong: true }),
                el("input", {
                    type: "number", step: "0.5", id: "talla-edit-us-hombre", class: "form-control",
                    value: talla.tallaUsHombre ?? "", placeholder: "Sin equivalencia"
                }),
                el("input", {
                    type: "number", step: "0.5", id: "talla-edit-us-mujer", class: "form-control",
                    value: talla.tallaUsMujer ?? "", placeholder: "Sin equivalencia"
                }),
                el("input", {
                    type: "number", step: "0.5", id: "talla-edit-uk", class: "form-control",
                    value: talla.tallaUk ?? "", placeholder: "Sin equivalencia"
                }),
                el("input", {
                    type: "number", step: "0.1", id: "talla-edit-cm", class: "form-control",
                    value: talla.tallaCmMx ?? "", placeholder: "Ej: 27"
                }),
                this.tallaEstadoBadge(talla),
                el("div", { class: "inline-actions" }, [
                    el("button", {
                        class: "btn btn-primary btn-sm", type: "button", id: "btn-talla-edit-guardar",
                        text: "Guardar", on: { click: () => this.guardarEdicionTalla(talla) }
                    }),
                    el("button", {
                        class: "btn btn-outline btn-sm", type: "button", text: "Cancelar",
                        on: { click: () => this.cancelarEdicionTalla() }
                    })
                ])
            ];
        }

        return [
            this.celdaTalla(String(talla.tallaEur ?? "-"), inactivo, { strong: true }),
            this.celdaTalla(this.equivalencia(talla.tallaUsHombre), inactivo),
            this.celdaTalla(this.equivalencia(talla.tallaUsMujer), inactivo),
            this.celdaTalla(this.equivalencia(talla.tallaUk), inactivo),
            this.celdaTalla(this.equivalencia(talla.tallaCmMx), inactivo),
            this.tallaEstadoBadge(talla),
            el("div", { class: "inline-actions" }, [
                el("button", {
                    class: "btn btn-outline btn-sm", type: "button", text: "Editar",
                    on: { click: () => this.editarTalla(talla) }
                }),
                talla.active
                    ? el("button", {
                        class: "btn btn-danger btn-sm", type: "button", text: "Dar de baja",
                        on: { click: () => this.cambiarEstadoTalla(talla) }
                    })
                    : el("button", {
                        class: "btn btn-success btn-sm", type: "button", text: "Reactivar",
                        on: { click: () => this.cambiarEstadoTalla(talla) }
                    })
            ])
        ];
    }

    celdaTalla(value, inactivo, { strong = false } = {}) {
        const clases = [];
        if (strong) clases.push("cell-strong");
        if (inactivo) clases.push("text-muted");
        return el("span", { class: clases.join(" ") || null, text: value });
    }

    equivalencia(valor) {
        return valor === null || valor === undefined || valor === "" ? "-" : String(valor);
    }

    tallaEstadoBadge(talla) {
        return talla.active
            ? el("span", { class: "badge badge-success", text: "Activa" })
            : el("span", { class: "badge badge-neutral", text: "Inactiva" });
    }

    editarTalla(talla) {
        if (!talla?.id) return;
        this.tallaEditandoId = talla.id;
        this.renderTallasSection();
    }

    cancelarEdicionTalla() {
        this.tallaEditandoId = null;
        this.renderTallasSection();
    }

    /** Validates the EUR size client-side: required, 0.1..99.9 and at most one decimal. */
    validarTallaEur(texto) {
        const raw = String(texto ?? "").trim();
        if (raw === "") {
            return { ok: false, message: "Ingresá la talla EUR: es obligatoria." };
        }
        if (!/^\d+(\.\d+)?$/.test(raw)) {
            return { ok: false, message: "La talla EUR debe ser un número entre 0.1 y 99.9 con un máximo de un decimal." };
        }
        const numero = Number(raw);
        const decimales = (raw.split(".")[1] || "").length;
        if (!Number.isFinite(numero) || decimales > 1 || numero < 0.1 || numero > 99.9) {
            return { ok: false, message: "La talla EUR debe estar entre 0.1 y 99.9 con un máximo de un decimal." };
        }
        return { ok: true, value: numero };
    }

    async agregarTalla() {
        const eur = this.validarTallaEur(qs("#talla-nueva-eur")?.value);
        if (!eur.ok) {
            NotificationService.warning(eur.message);
            return;
        }

        // Client-side guard: a size already in the loaded list never reaches the API.
        const existente = (this.tallas || []).find(
            talla => Number(talla.tallaEur).toFixed(1) === eur.value.toFixed(1));
        if (existente) {
            NotificationService.warning(`La talla ${eur.value.toFixed(1)} ya existe en el catálogo.`);
            return;
        }

        const cmTexto = (qs("#talla-nueva-cm")?.value || "").trim();
        if (cmTexto === "") {
            NotificationService.warning("Ingresá los centímetros MX: son obligatorios.");
            return;
        }
        const cm = Number(cmTexto);
        if (!Number.isFinite(cm) || cm <= 0) {
            NotificationService.warning("Los centímetros MX deben ser un número mayor que 0.");
            return;
        }

        // Blank optional fields are omitted, never sent as empty strings.
        const body = { tallaEur: eur.value, tallaCmMx: cm };
        [["tallaUsHombre", "#talla-nueva-us-hombre"],
            ["tallaUsMujer", "#talla-nueva-us-mujer"],
            ["tallaUk", "#talla-nueva-uk"]].forEach(([clave, selector]) => {
            const texto = (qs(selector)?.value || "").trim();
            if (texto !== "") body[clave] = Number(texto);
        });

        if (!this.iniciarMutacionPanel()) return;
        const generation = this.viewGeneration;

        const button = qs("#btn-talla-agregar");
        if (button) { button.disabled = true; button.textContent = "Agregando…"; }

        try {
            const creada = await fetchJson(CONFIG.ENDPOINTS.CATALOGO_ADMIN_TALLAS, { method: "POST", body });
            if (generation !== this.viewGeneration) return;
            // Fold the response in before the refresh so a failed reload still
            // paints the state this write persisted.
            this.tallas = this.mergeById(this.tallas, creada);
            NotificationService.success(`Talla ${creada?.tallaEur ?? body.tallaEur} agregada al catálogo.`);
            this.resetFormTalla();
            await this.refrescarTrasMutacionTalla();
        } catch (error) {
            if (generation !== this.viewGeneration) return;
            NotificationService.error(`No se pudo agregar la talla: ${error.message}`);
        } finally {
            // The success path only repaints #tallas-resultados, never this form, so the
            // button must be restored here or a second size cannot be added without remounting.
            if (button) { button.disabled = false; button.textContent = "Agregar talla"; }
            this.finalizarMutacionPanel(generation);
        }
    }

    async guardarEdicionTalla(talla) {
        if (!talla?.id) return;

        // Blank fields are omitted: null means "keep the current value" on the backend.
        const body = {};
        [["tallaUsHombre", "#talla-edit-us-hombre"],
            ["tallaUsMujer", "#talla-edit-us-mujer"],
            ["tallaUk", "#talla-edit-uk"],
            ["tallaCmMx", "#talla-edit-cm"]].forEach(([clave, selector]) => {
            const texto = (qs(selector)?.value || "").trim();
            if (texto !== "") body[clave] = Number(texto);
        });

        if (!Object.keys(body).length) {
            NotificationService.warning("No hay cambios para guardar: los campos vacíos conservan su valor actual.");
            return;
        }

        if (!this.iniciarMutacionPanel()) return;
        const generation = this.viewGeneration;

        const button = qs("#btn-talla-edit-guardar");
        if (button) { button.disabled = true; button.textContent = "Guardando…"; }

        try {
            const endpoint = apiUrl(CONFIG.ENDPOINTS.CATALOGO_ADMIN_TALLAS, talla.id);
            const actualizada = await fetchJson(endpoint, { method: "PUT", body });
            if (generation !== this.viewGeneration) return;
            // Fold the response in before the refresh so a failed reload still
            // paints the state this write persisted.
            this.tallas = this.mergeById(this.tallas, actualizada);
            this.tallaEditandoId = null;
            NotificationService.success(`Talla ${actualizada?.tallaEur ?? talla.tallaEur} actualizada.`);
            await this.refrescarTrasMutacionTalla();
        } catch (error) {
            if (generation !== this.viewGeneration) return;
            NotificationService.error(`No se pudo actualizar la talla: ${error.message}`);
        } finally {
            if (button) { button.disabled = false; button.textContent = "Guardar"; }
            this.finalizarMutacionPanel(generation);
        }
    }

    async cambiarEstadoTalla(talla) {
        if (!talla?.id) return;

        // The rendered row can drift from the authoritative list, so the target
        // state comes from the current size in this.tallas, never from the row.
        const actual = (Array.isArray(this.tallas) ? this.tallas : [])
            .find(item => item.id === talla.id);
        const activar = (actual || talla).active !== true;

        // The gate is taken before the dialog: a write already in flight must reject
        // this one before the operator is asked to confirm an action that cannot run.
        if (!this.iniciarMutacionPanel()) return;
        const generation = this.viewGeneration;

        try {
            if (!activar) {
                const confirmado = await NotificationService.confirm(
                    `Dar de baja la talla ${talla.tallaEur} es una baja lógica: los SKUs ya generados conservan su talla y su código reservado. ¿Dar de baja?`,
                    { confirmLabel: "Dar de baja" });
                if (!confirmado) return;
                if (generation !== this.viewGeneration) return;
            }

            const endpoint = apiUrl(CONFIG.ENDPOINTS.CATALOGO_ADMIN_TALLAS, talla.id, "active");
            const actualizada = await fetchJson(endpoint, { method: "PATCH", body: { active: activar } });
            if (generation !== this.viewGeneration) return;
            // Fold the response in before the refresh so a failed reload still
            // paints the state this write persisted.
            this.tallas = this.mergeById(this.tallas, actualizada);
            NotificationService.success(activar
                ? `Talla ${actualizada?.tallaEur ?? talla.tallaEur} reactivada.`
                : `Talla ${actualizada?.tallaEur ?? talla.tallaEur} dada de baja.`);
            await this.refrescarTrasMutacionTalla();
        } catch (error) {
            if (generation !== this.viewGeneration) return;
            NotificationService.error(`No se pudo ${activar ? "reactivar" : "dar de baja"} la talla: ${error.message}`);
        } finally {
            this.finalizarMutacionPanel(generation);
        }
    }

    /**
     * Single post-mutation refresh path for sizes: reload the master list, repaint
     * this section and, when a model detail is open, refresh its SKU size selector.
     */
    async refrescarTrasMutacionTalla() {
        const generation = this.viewGeneration;
        await this.cargarTallas({ refresh: true });
        if (generation !== this.viewGeneration) return;
        this.renderTallasSection();
        if (this.modeloEditando) {
            await this.refrescarDetallePanel(this.modeloEditando.id);
        }
    }

    resetFormTalla() {
        ["#talla-nueva-eur", "#talla-nueva-cm", "#talla-nueva-us-hombre",
            "#talla-nueva-us-mujer", "#talla-nueva-uk"].forEach(selector => {
            const input = qs(selector);
            if (input) input.value = "";
        });
    }

    unmount() {
        // A fresh mount must be usable immediately, so the gate is released; the
        // generation bump is what makes any write still in flight completely inert.
        this.viewGeneration += 1;
        this.selectedFile = null;
        this.modelos = [];
        this.modelosRequestId += 1;
        this.modeloEditando = null;
        this.tipos = [];
        this.marcas = [];
        this.colores = [];
        this.tallas = [];
        this.modeloDetalleRequestId += 1;
        this.panelMutationEnCurso = false;
        this.skuEditandoId = null;
        this.tallaEditandoId = null;
        this.tallasResultados = null;
    }
}
