# Formato del documento de entrega (APA 7)

Especificación extraída del pipeline de referencia del curso (`build_apa_document.py` →
`Analisis Ecommerse zapateria.docx`). El documento clínico debe reproducir estas medidas al punto:
la entrega se evalúa contra ese molde, no contra un criterio propio.

Fuente autorizada: carpeta de referencia `C:\Users\josue\OneDrive\Documents\e-commerce`,
leída solo para replicar el formato.

## Página y estilo base

- Tamaño carta: 8.5 × 11 pulgadas. Márgenes de 1 pulgada (2.54 cm) en los cuatro lados.
- Encabezado: número de página en la esquina superior derecha, Times New Roman 12 pt,
  interlineado 1.0, sin espacio posterior, insertado como campo `PAGE` de Word.
- Estilo `Normal`: Times New Roman 12 pt, color negro, interlineado 1.5,
  espacio posterior 6 pt, espacio anterior 0.
- Párrafo de cuerpo: justificado, sangría de primera línea 0.5 pulgadas, interlineado 1.5.

## Títulos

| Nivel | Alineación | Formato | Antes | Después | `keep_with_next` |
|-------|-----------|---------|-------|---------|------------------|
| H1 | Centrado | TNR 13 pt negrita | 18 pt | 8 pt | sí |
| H2 | Izquierda | TNR 12 pt negrita | 14 pt | 6 pt | sí |
| H3 | Izquierda | TNR 12 pt negrita cursiva | 10 pt | 4 pt | sí |

Las secciones se numeran en el texto del título (`1. Introducción`, `2. Marco metodológico`, ...).

## Tablas

- Rótulo: `Tabla N` en negrita, TNR 12 pt, sin sangría, 12 pt antes y 2 pt después,
  `keep_with_next`.
- Título: una línea en cursiva, TNR 12 pt, 6 pt después, `keep_with_next`.
- Bordes APA: superior e inferior exterior de 8 (single, negro); sin bordes internos
  horizontales, sin verticales, sin laterales. La fila de encabezado se repite en cada página
  (`tblHeader`) y cada celda del encabezado lleva borde inferior de 6.
- Encabezado: TNR 10 pt negrita, centrado, interlineado 1.15, 4 pt antes y después.
- Celdas: TNR 9.5 pt, interlineado 1.15, 3 pt antes y después; centradas si la columna mide
  menos de 1.2 pulgadas, alineadas a la izquierda en el resto. Tabla centrada en la página.
- Nota: párrafo aparte, `Nota. ` en cursiva TNR 9.5 pt seguido del texto en TNR 9.5 pt,
  4 pt antes y 12 pt después.

## Figuras

- Rótulo: `Figura N` en negrita, TNR 12 pt, sin sangría, 12 pt antes y 2 pt después.
- Título: una línea en cursiva, TNR 12 pt, `keep_with_next`.
- Imagen centrada, ancho 5.8 pulgadas (cabe en el margen útil de 6.5), 4 pt antes y después.
- Nota: mismo formato que la nota de tabla, con la fuente del diagrama.

## Referencias

- Sangría francesa: `left_indent` 0.5 pulgadas y `first_line_indent` −0.5 pulgadas.
- TNR 12 pt, alineación izquierda, interlineado 1.5, 3 pt antes y 6 pt después.

## Estructura de secciones del documento

1. Portada (título, autor, institución, asignatura, docente, fecha).
2. Resumen y abstract con palabras clave.
3. Introducción y contextualización del consultorio.
4. Marco metodológico por etapas del ciclo de vida.
5. Análisis de requisitos y modelado del negocio (incluye los diagramas UML como Figuras).
6. Requerimientos funcionales (matriz trazada a S1–S10) y no funcionales.
7. Matriz de trazabilidad.
8. Conclusiones y recomendaciones.
9. Referencias bibliográficas en APA 7.
10. Anexo A: instrumento de elicitación de requisitos.

## Reglas de generación

- El `.docx` se produce por script reproducible (`docs/build_documento_clinica.py`), nunca a mano.
- Las fuentes PlantUML se versionan en `docs/uml/*.puml`; los PNG renderizados en `docs/uml/out/`
  y se incrustan como Figuras numeradas con su nota de fuente.
- Cada figura debe corresponder al código entregado: si un diagrama cambia, se regenera el
  documento.
