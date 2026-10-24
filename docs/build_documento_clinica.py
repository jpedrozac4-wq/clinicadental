#!/usr/bin/env python3
"""Build the APA 7 course document for the dental clinic system.

Usage
-----
    python docs/build_documento_clinica.py

The script is self-contained: it needs only ``python-docx`` (tested with 1.2.0),
does not use the network, does not call an external converter and never edits a
``.docx`` by hand. Running it again simply overwrites the previous output, so the
document is reproducible from the repository contents.

What it generates
-----------------
``docs/Analisis Clinica Dental.docx`` with the format contract defined in
``docs/FORMATO_DOCUMENTO.md``:

* letter page, 1 inch margins and a right-aligned ``PAGE`` field in the header;
* ``Normal`` style in Times New Roman 12 pt, 1.5 line spacing, 6 pt after;
* section headings ``H1``/``H2``/``H3`` with the exact size, spacing and
  ``keep_with_next`` settings of the contract;
* APA tables (top/bottom rules only, repeating header row, 9.5 pt cells) with
  ``Tabla N``, italic title and a ``Nota.`` line;
* ``Figura N`` plus italic title, a centered 5.8 inch image and a source note;
* hanging-indent APA references.

Content is written in Spanish. Technical identifiers, endpoints and file paths
come from the delivered code: ``schema.sql``, ``backend/src/main/java/com/clinica/dental``,
``frontend/js`` and the specs S1-S10 of ``odd/tasks/clinica-dental.md``.

Figures are discovered at build time from ``docs/uml/out/*.png``. A curated
catalogue supplies the title and the source note for the diagrams named in S12;
any other PNG is appended with a derived title. Catalogued diagrams that are not
rendered yet produce a warning on stdout and are skipped, never a failure.
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.image.image import Image as DocxImage
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor

# ---------------------------------------------------------------------------
# Paths and global format constants (docs/FORMATO_DOCUMENTO.md)
# ---------------------------------------------------------------------------

DOCS_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = DOCS_DIR.parent
UML_OUT_DIR = DOCS_DIR / "uml" / "out"
ROUTES_PATH = PROJECT_ROOT / "frontend" / "js" / "core" / "routes.js"
OUTPUT_PATH = DOCS_DIR / "Analisis Clinica Dental.docx"

FONT_NAME = "Times New Roman"
BLACK = RGBColor(0, 0, 0)

BODY_SIZE = Pt(12)
H1_SIZE = Pt(13)
TABLE_HEADER_SIZE = Pt(10)
TABLE_BODY_SIZE = Pt(9.5)
NOTE_SIZE = Pt(9.5)

PAGE_WIDTH = Inches(8.5)
PAGE_HEIGHT = Inches(11)
PAGE_MARGIN = Inches(1)
CONTENT_WIDTH = Inches(6.5)
FIGURE_WIDTH = Inches(5.8)
# Una figura mas alta que esto desborda la pagina una vez que se suman rotulo, titulo y
# nota; se escala por altura conservando la proporcion en lugar de recortarla.
FIGURE_MAX_HEIGHT = Inches(7.4)

COURSE_NAME = "Análisis de Sistemas 2"
DOCUMENT_DATE = "20 de octubre de 2026"
DOCUMENT_TITLE = (
    "Análisis y Diseño de un Sistema de Gestión Odontológica "
    "para un Consultorio Dental"
)
DOCUMENT_SUBTITLE = (
    "Requerimientos funcionales y no funcionales, modelado UML y "
    "matriz de trazabilidad"
)

# Cover data. Replace the bracketed placeholders with the real names before the
# final submission; nothing else in the document depends on them.
COVER_LINES = [
    ("[Nombre del estudiante]", False),
    ("[Nombre de la institución]", False),
    (f"{COURSE_NAME}: Análisis y Diseño de Sistemas", False),
    ("[Nombre del docente]", False),
    (DOCUMENT_DATE, False),
]

HEADING_SPEC = {
    1: {
        "size": H1_SIZE,
        "italic": False,
        "align": WD_ALIGN_PARAGRAPH.CENTER,
        "before": 18,
        "after": 8,
    },
    2: {
        "size": BODY_SIZE,
        "italic": False,
        "align": WD_ALIGN_PARAGRAPH.LEFT,
        "before": 14,
        "after": 6,
    },
    3: {
        "size": BODY_SIZE,
        "italic": True,
        "align": WD_ALIGN_PARAGRAPH.LEFT,
        "before": 10,
        "after": 4,
    },
}


# ---------------------------------------------------------------------------
# Formatting primitives
# ---------------------------------------------------------------------------


def set_run_font(run, size=BODY_SIZE, *, bold=False, italic=False):
    """Apply Times New Roman, size and colour to a run, including eastAsia/cs."""
    run.font.name = FONT_NAME
    run.font.size = size
    run.font.bold = bold
    run.font.italic = italic
    run.font.color.rgb = BLACK
    run_properties = run._element.get_or_add_rPr()
    fonts = run_properties.find(qn("w:rFonts"))
    if fonts is None:
        fonts = OxmlElement("w:rFonts")
        run_properties.append(fonts)
    for attribute in ("w:ascii", "w:hAnsi", "w:eastAsia", "w:cs"):
        fonts.set(qn(attribute), FONT_NAME)
    return run


def configure_styles(document):
    """Set the Normal style and the three heading levels to the contract values."""
    normal = document.styles["Normal"]
    normal.font.name = FONT_NAME
    normal.font.size = BODY_SIZE
    normal.font.color.rgb = BLACK
    normal_format = normal.paragraph_format
    normal_format.line_spacing = 1.5
    normal_format.space_before = Pt(0)
    normal_format.space_after = Pt(6)

    normal_properties = normal.element.get_or_add_rPr()
    fonts = normal_properties.find(qn("w:rFonts"))
    if fonts is None:
        fonts = OxmlElement("w:rFonts")
        normal_properties.append(fonts)
    for attribute in ("w:ascii", "w:hAnsi", "w:eastAsia", "w:cs"):
        fonts.set(qn(attribute), FONT_NAME)
    language = OxmlElement("w:lang")
    language.set(qn("w:val"), "es-ES")
    language.set(qn("w:eastAsia"), "es-ES")
    language.set(qn("w:bidi"), "ar-SA")
    normal_properties.append(language)

    for level, spec in HEADING_SPEC.items():
        style = document.styles[f"Heading {level}"]
        style.font.name = FONT_NAME
        style.font.size = spec["size"]
        style.font.bold = True
        style.font.italic = spec["italic"]
        style.font.color.rgb = BLACK
        style_format = style.paragraph_format
        style_format.alignment = spec["align"]
        style_format.space_before = Pt(spec["before"])
        style_format.space_after = Pt(spec["after"])
        style_format.line_spacing = 1.5
        style_format.keep_with_next = True


def configure_page(section):
    """Letter page with 1 inch margins and a right-aligned PAGE field."""
    section.page_width = PAGE_WIDTH
    section.page_height = PAGE_HEIGHT
    for margin in ("top_margin", "bottom_margin", "left_margin", "right_margin"):
        setattr(section, margin, PAGE_MARGIN)
    section.header_distance = Inches(0.5)
    section.footer_distance = Inches(0.5)

    header = section.header
    header.is_linked_to_previous = False
    paragraph = header.paragraphs[0]
    paragraph.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    paragraph_format = paragraph.paragraph_format
    paragraph_format.space_before = Pt(0)
    paragraph_format.space_after = Pt(0)
    paragraph_format.line_spacing = 1.0
    _append_page_field(paragraph)


def _append_page_field(paragraph):
    """Insert a Word ``PAGE`` field so the number is computed by Word itself."""
    for field_type, payload in (
        ("begin", None),
        ("instrText", " PAGE "),
        ("separate", None),
        ("text", "1"),
        ("end", None),
    ):
        run = paragraph.add_run()
        set_run_font(run, size=BODY_SIZE)
        if field_type == "instrText":
            element = OxmlElement("w:instrText")
            element.set(qn("xml:space"), "preserve")
            element.text = payload
            run._r.append(element)
        elif field_type == "text":
            element = OxmlElement("w:t")
            element.text = payload
            run._r.append(element)
        else:
            element = OxmlElement("w:fldChar")
            element.set(qn("w:fldCharType"), field_type)
            run._r.append(element)


def add_p(
    document,
    text="",
    *,
    align=WD_ALIGN_PARAGRAPH.JUSTIFY,
    first_indent=True,
    size=BODY_SIZE,
    bold=False,
    italic=False,
    before=0,
    after=6,
    line_spacing=1.5,
    keep_with_next=False,
):
    """Add a body paragraph with the contract defaults."""
    paragraph = document.add_paragraph()
    paragraph_format = paragraph.paragraph_format
    paragraph_format.alignment = align
    paragraph_format.first_line_indent = Inches(0.5) if first_indent else Inches(0)
    paragraph_format.space_before = Pt(before)
    paragraph_format.space_after = Pt(after)
    paragraph_format.line_spacing = line_spacing
    paragraph_format.keep_with_next = keep_with_next
    if text:
        set_run_font(paragraph.add_run(text), size=size, bold=bold, italic=italic)
    return paragraph


def add_rich_p(document, segments, *, first_indent=True, after=6, align=WD_ALIGN_PARAGRAPH.JUSTIFY):
    """Add a paragraph made of ``(text, bold, italic)`` segments."""
    paragraph = add_p(document, first_indent=first_indent, after=after, align=align)
    for text, bold, italic in segments:
        set_run_font(paragraph.add_run(text), bold=bold, italic=italic)
    return paragraph


def add_list_item(document, marker, text, *, after=3):
    """Add a hanging list item (used by objectives, rules and recommendations)."""
    paragraph = document.add_paragraph()
    paragraph_format = paragraph.paragraph_format
    paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    paragraph_format.left_indent = Inches(0.75)
    paragraph_format.first_line_indent = Inches(-0.25)
    paragraph_format.space_before = Pt(0)
    paragraph_format.space_after = Pt(after)
    paragraph_format.line_spacing = 1.5
    set_run_font(paragraph.add_run(f"{marker}\t"), bold=False)
    set_run_font(paragraph.add_run(text))
    return paragraph


def _add_heading(document, text, level):
    spec = HEADING_SPEC[level]
    paragraph = document.add_heading(level=level)
    paragraph_format = paragraph.paragraph_format
    paragraph_format.alignment = spec["align"]
    paragraph_format.first_line_indent = Inches(0)
    paragraph_format.space_before = Pt(spec["before"])
    paragraph_format.space_after = Pt(spec["after"])
    paragraph_format.keep_with_next = True
    paragraph_format.line_spacing = 1.5
    set_run_font(
        paragraph.add_run(text),
        size=spec["size"],
        bold=True,
        italic=spec["italic"],
    )
    return paragraph


def add_h1(document, text):
    return _add_heading(document, text, 1)


def add_h2(document, text):
    return _add_heading(document, text, 2)


def add_h3(document, text):
    return _add_heading(document, text, 3)


# ---------------------------------------------------------------------------
# Tables and figures
# ---------------------------------------------------------------------------


@dataclass
class Numbering:
    """Sequential counters for ``Tabla N`` and ``Figura N`` labels."""

    tables: int = 0
    figures: int = 0

    def next_table(self) -> int:
        self.tables += 1
        return self.tables

    def next_figure(self) -> int:
        self.figures += 1
        return self.figures


def _set_table_borders(table):
    """APA rules: 8 pt top/bottom outside, no vertical or internal borders."""
    table_properties = table._tbl.tblPr
    existing = table_properties.find(qn("w:tblBorders"))
    if existing is not None:
        table_properties.remove(existing)

    borders = OxmlElement("w:tblBorders")
    for edge, value, size in (
        ("top", "single", 8),
        ("left", "none", 0),
        ("bottom", "single", 8),
        ("right", "none", 0),
        ("insideH", "none", 0),
        ("insideV", "none", 0),
    ):
        element = OxmlElement(f"w:{edge}")
        element.set(qn("w:val"), value)
        element.set(qn("w:sz"), str(size))
        element.set(qn("w:space"), "0")
        element.set(qn("w:color"), "000000" if value == "single" else "auto")
        borders.append(element)

    table_properties.insert_element_before(
        borders,
        "w:shd",
        "w:tblLayout",
        "w:tblCellMar",
        "w:tblLook",
        "w:tblCaption",
        "w:tblDescription",
    )


def _set_header_cell_bottom_border(cell):
    """Header cells carry a 6 pt bottom rule (APA)."""
    cell_properties = cell._tc.get_or_add_tcPr()
    borders = cell_properties.find(qn("w:tcBorders"))
    if borders is None:
        borders = OxmlElement("w:tcBorders")
        cell_properties.append(borders)
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), "6")
    bottom.set(qn("w:space"), "0")
    bottom.set(qn("w:color"), "000000")
    borders.append(bottom)


def _repeat_header_row(row):
    """Mark the first row as a repeating table header."""
    row_properties = row._tr.get_or_add_trPr()
    header = OxmlElement("w:tblHeader")
    header.set(qn("w:val"), "true")
    row_properties.append(header)


def _format_cell(cell, text, *, size, bold, align, before, after):
    paragraph = cell.paragraphs[0]
    paragraph_format = paragraph.paragraph_format
    paragraph_format.alignment = align
    paragraph_format.first_line_indent = Inches(0)
    paragraph_format.space_before = Pt(before)
    paragraph_format.space_after = Pt(after)
    paragraph_format.line_spacing = 1.15
    set_run_font(paragraph.add_run(text), size=size, bold=bold)


def add_apa_table(document, numbering, title, headers, rows, widths, note):
    """Add a numbered APA table: label, italic title, rules, repeating header, note."""
    number = numbering.next_table()

    label = add_p(
        document,
        f"Tabla {number}",
        align=WD_ALIGN_PARAGRAPH.LEFT,
        first_indent=False,
        bold=True,
        before=12,
        after=2,
        keep_with_next=True,
    )
    label.paragraph_format.line_spacing = 1.5

    caption = add_p(
        document,
        title,
        align=WD_ALIGN_PARAGRAPH.LEFT,
        first_indent=False,
        italic=True,
        before=0,
        after=6,
        keep_with_next=True,
    )
    caption.paragraph_format.line_spacing = 1.5

    table = document.add_table(rows=1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    _set_table_borders(table)

    header_row = table.rows[0]
    _repeat_header_row(header_row)
    for index, text in enumerate(headers):
        cell = header_row.cells[index]
        cell.width = Inches(widths[index])
        _format_cell(
            cell,
            text,
            size=TABLE_HEADER_SIZE,
            bold=True,
            align=WD_ALIGN_PARAGRAPH.CENTER,
            before=4,
            after=4,
        )
        _set_header_cell_bottom_border(cell)

    for values in rows:
        body_row = table.add_row()
        for index, text in enumerate(values):
            cell = body_row.cells[index]
            cell.width = Inches(widths[index])
            centered = widths[index] < 1.2
            _format_cell(
                cell,
                text,
                size=TABLE_BODY_SIZE,
                bold=False,
                align=WD_ALIGN_PARAGRAPH.CENTER if centered else WD_ALIGN_PARAGRAPH.LEFT,
                before=3,
                after=3,
            )

    for index, width in enumerate(widths):
        table.columns[index].width = Inches(width)

    add_note(document, note)


def add_note(document, text, *, label="Nota."):
    """Add the APA note paragraph: italic label plus regular 9.5 pt text."""
    paragraph = document.add_paragraph()
    paragraph_format = paragraph.paragraph_format
    paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    paragraph_format.first_line_indent = Inches(0)
    paragraph_format.space_before = Pt(4)
    paragraph_format.space_after = Pt(12)
    paragraph_format.line_spacing = 1.15
    set_run_font(paragraph.add_run(f"{label} "), size=NOTE_SIZE, italic=True)
    set_run_font(paragraph.add_run(text), size=NOTE_SIZE)
    return paragraph


def picture_width(image_path):
    """Width for a figure: the content width, reduced when the image would overflow the page."""
    image = DocxImage.from_file(str(image_path))
    aspect = image.height / image.width
    if FIGURE_WIDTH * aspect <= FIGURE_MAX_HEIGHT:
        return FIGURE_WIDTH
    return int(FIGURE_MAX_HEIGHT * image.width / image.height)


def add_figure(document, numbering, title, image_path, source_note):
    """Add a numbered APA figure: label, italic title, 5.8 inch image and note."""
    number = numbering.next_figure()

    add_p(
        document,
        f"Figura {number}",
        align=WD_ALIGN_PARAGRAPH.LEFT,
        first_indent=False,
        bold=True,
        before=12,
        after=2,
        keep_with_next=True,
    )
    add_p(
        document,
        title,
        align=WD_ALIGN_PARAGRAPH.LEFT,
        first_indent=False,
        italic=True,
        before=0,
        after=4,
        keep_with_next=True,
    )

    picture_paragraph = document.add_paragraph()
    picture_format = picture_paragraph.paragraph_format
    picture_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
    picture_format.first_line_indent = Inches(0)
    picture_format.space_before = Pt(4)
    picture_format.space_after = Pt(4)
    picture_format.keep_with_next = True
    picture_paragraph.add_run().add_picture(str(image_path), width=picture_width(image_path))

    add_note(document, source_note)


# ---------------------------------------------------------------------------
# Figure catalogue (S12) and discovery
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class FigureSpec:
    key: str
    title: str
    source: str
    aliases: tuple = field(default=())


FIGURE_CATALOG = [
    FigureSpec(
        "casos-de-uso",
        "Diagrama de casos de uso del sistema",
        "docs/uml/01-casos-de-uso.puml, renderizado con PlantUML",
        ("casos-uso", "usecases", "casosdeuso"),
    ),
    FigureSpec(
        "clases",
        "Diagrama de clases del dominio clínico",
        "las entidades JPA del paquete com.clinica.dental",
        ("clases-dominio", "diagrama-clases"),
    ),
    FigureSpec(
        "entidad-relacion",
        "Diagrama entidad-relación de la base de datos",
        "schema.sql y las relaciones declaradas en el modelo relacional",
        ("entidadrelacion", "modelo-datos", "er"),
    ),
    FigureSpec(
        "secuencia-alta-paciente",
        "Diagrama de secuencia del alta de un paciente",
        "PacienteController y PacienteService",
        ("secuencia-paciente", "alta-paciente"),
    ),
    FigureSpec(
        "secuencia-programacion-cita",
        "Diagrama de secuencia de la programación de una cita",
        "CitaController y CitaService",
        ("secuencia-cita",),
    ),
    FigureSpec(
        "secuencia-recordatorio",
        "Diagrama de secuencia del envío de recordatorios",
        "RecordatorioController y RecordatorioService",
        ("secuencia-recordatorios", "secuencia-recordatorios-cita"),
    ),
    FigureSpec(
        "secuencia-emision-factura",
        "Diagrama de secuencia de la emisión de una factura",
        "FacturaController, FacturaService y FacturaPdfService",
        ("secuencia-factura",),
    ),
    FigureSpec(
        "secuencia-pago-parcial",
        "Diagrama de secuencia del registro de un pago parcial",
        "PagoController y PagoService",
        ("secuencia-pago",),
    ),
    FigureSpec(
        "actividades-atencion-paciente",
        "Diagrama de actividades de la atención de un paciente",
        "el flujo de negocio cubierto por S1-S10",
        ("actividades-atencion", "actividad-atencion"),
    ),
    FigureSpec(
        "estados-cita",
        "Diagrama de estados del ciclo de vida de una cita",
        "EstadoCita y las transiciones aplicadas por CitaService",
        ("estado-cita",),
    ),
    FigureSpec(
        "estados-factura",
        "Diagrama de estados del ciclo de vida de una factura",
        "EstadoFactura y el recálculo de saldo de PagoService",
        ("estado-factura",),
    ),
    FigureSpec(
        "componentes",
        "Diagrama de componentes de la solución",
        "los módulos del backend y los servicios del frontend",
        ("componentes-solucion",),
    ),
    FigureSpec(
        "despliegue",
        "Diagrama de despliegue de la solución",
        "docker-compose.yml y nginx.conf",
        ("despliegue-solucion",),
    ),
]


def normalize_key(value: str) -> str:
    """Reduce a file stem or alias to a comparable key."""
    cleaned = re.sub(r"^\d+[\s._-]*", "", value.strip())
    return re.sub(r"[^a-z0-9]", "", cleaned.lower())


def humanize_key(value: str) -> str:
    """Derive a readable title for a PNG that is not in the catalogue."""
    cleaned = re.sub(r"^\d+[\s._-]*", "", value.replace("_", " ").replace("-", " "))
    words = [word for word in cleaned.split() if word.lower() not in ("diagrama", "diagram")]
    if not words:
        return "Diagrama del sistema"
    minor = {"de", "del", "la", "el", "los", "las", "y"}
    title = [words[0].capitalize()]
    title.extend(word if word.lower() in minor else word.capitalize() for word in words[1:])
    phrase = " ".join(title)
    return f"Diagrama de {phrase[0].lower()}{phrase[1:]}"


def discover_figures(output_dir: Path):
    """Return ``(matched, missing, unknown)`` for the PNGs currently on disk."""
    if not output_dir.is_dir():
        return [], [spec.key for spec in FIGURE_CATALOG], []

    available = {}
    for path in sorted(output_dir.glob("*.png")):
        available[normalize_key(path.stem)] = path

    matched = []
    missing = []
    for spec in FIGURE_CATALOG:
        keys = [normalize_key(spec.key)] + [normalize_key(alias) for alias in spec.aliases]
        found = next((available.pop(key) for key in keys if key in available), None)
        if found is None:
            missing.append(spec.key)
        else:
            matched.append((spec, found))

    unknown = [(path.stem, path) for path in sorted(available.values())]
    return matched, missing, unknown


def read_frontend_routes():
    """Read the active and reserved view labels from the frontend router.

    The router keeps the delivered views in ``ROUTES`` and the pending ones in
    ``RUTAS_RESERVADAS``; reading that file keeps the document aligned with the
    frontend that actually ships. Returns ``None`` when the file cannot be read.
    """
    try:
        source = ROUTES_PATH.read_text(encoding="utf-8")
    except OSError:
        return None
    active_source, _, reserved_source = source.partition("RUTAS_RESERVADAS")
    active = re.findall(r'label:\s*"([^"]+)"', active_source)
    reserved = re.findall(r'label:\s*"([^"]+)"', reserved_source)
    return active, reserved


def join_spanish(items):
    """Join a short list with Spanish separators, keeping item text intact."""
    if not items:
        return ""
    if len(items) == 1:
        return items[0]
    if len(items) == 2:
        return f"{items[0]} y {items[1]}"
    return ", ".join(items)


# ---------------------------------------------------------------------------
# Content data (Spanish)
# ---------------------------------------------------------------------------

KEYWORDS_ES = [
    "análisis de sistemas",
    "ingeniería de requisitos",
    "UML",
    "clínica dental",
    "API REST",
    "PostgreSQL",
    "trazabilidad",
]

KEYWORDS_EN = [
    "systems analysis",
    "requirements engineering",
    "UML",
    "dental clinic",
    "REST API",
    "PostgreSQL",
    "traceability",
]

RESUMEN = [
    (
        "Este documento presenta el análisis, el modelado y la especificación de requisitos "
        "de un sistema de gestión para un consultorio dental, construido a partir de la "
        "reutilización de la infraestructura de un ERP de comercio electrónico de calzado. "
        "El trabajo sustituye el dominio original por completo y entrega diez "
        "requerimientos funcionales trazables —registro y baja lógica de pacientes, "
        "historial clínico, programación, reprogramación y cancelación de citas, "
        "recordatorios, catálogo de tratamientos, registro de tratamientos realizados, "
        "facturación y pagos parciales— sobre una API REST en Spring Boot 3.3.4 con Java 21, "
        "una base PostgreSQL 16 normalizada en tercera forma normal y una interfaz de una "
        "sola página en JavaScript sin dependencias de compilación."
    ),
    (
        "La solución conserva y reutiliza la seguridad basada en JSON Web Tokens con control "
        "de acceso por roles, el motor propio de generación de PDF, la convención de paquetes "
        "controller, service, repository y dto, y la orquestación con Docker Compose. El "
        "documento describe el ciclo de vida seguido, los actores y procesos del negocio, el "
        "modelo de dominio, los diagramas UML disponibles, la matriz de requerimientos "
        "funcionales y no funcionales, la matriz de trazabilidad entre especificación, código, "
        "prueba e interfaz, las conclusiones y un instrumento de elicitación por dimensiones. "
        "El archivo se genera mediante un script reproducible que reproduce el formato APA 7 "
        "del documento de referencia del curso."
    ),
]

ABSTRACT = [
    (
        "This document presents the analysis, modelling and requirements specification of a "
        "management system for a dental clinic, built by reusing the infrastructure of a "
        "footwear e-commerce ERP. The original business domain is fully replaced by ten "
        "traceable functional requirements: patient registration and logical deletion, "
        "clinical history, appointment scheduling, rescheduling and cancellation, reminders, "
        "treatment catalogue, performed treatments, invoicing and partial payments. The system "
        "runs on a Spring Boot 3.3.4 REST API with Java 21, a PostgreSQL 16 database normalised "
        "to third normal form and a dependency-free single-page front end."
    ),
    (
        "The solution preserves the JSON Web Token security model with role-based access "
        "control, the in-house PDF engine, the controller, service, repository and dto package "
        "convention, and the Docker Compose orchestration. The document covers the life-cycle "
        "stages, business actors and processes, the domain model, the available UML diagrams, "
        "the functional and non-functional requirement matrices, the traceability matrix linking "
        "specification, code, tests and user interface, the conclusions and a dimensional "
        "elicitation instrument. The file is produced by a reproducible script that matches the "
        "APA 7 format of the course reference document."
    ),
]

STAGE_ROWS = [
    (
        "Elicitación",
        "Entrevistas con recepción, odontología y administración; lectura del documento del "
        "curso; definición del alcance y de las reglas del negocio.",
        "Especificaciones S1-S10, glosario, reglas de negocio y anexo A.",
        "odd/tasks/clinica-dental.md",
    ),
    (
        "Análisis",
        "Descomposición por procesos y actores; identificación de casos de uso, entidades y "
        "validaciones; priorización de los requerimientos.",
        "Casos de uso, modelo conceptual y matriz de trazabilidad.",
        "docs/uml/ y schema.sql",
    ),
    (
        "Diseño",
        "Arquitectura por capas y por funcionalidad, contrato REST, modelo relacional en 3FN, "
        "esquema de seguridad y diseño de la interfaz.",
        "Diagramas de clases, secuencia, estados, componentes y despliegue; diccionario de datos.",
        "com.clinica.dental y frontend/js",
    ),
    (
        "Construcción",
        "Implementación incremental por módulo, con un commit revisable por unidad de trabajo y "
        "pruebas automatizadas junto al código.",
        "API REST, esquema, datos semilla y aplicación de una sola página.",
        "backend/, frontend/, schema.sql y seed_data.sql",
    ),
    (
        "Verificación",
        "Pruebas de servicio y de controlador por módulo, pruebas del motor PDF y pruebas de "
        "autenticación y autorización.",
        "Suite automatizada del backend y verificación del contrato de la API.",
        "backend/src/test (25 clases de prueba)",
    ),
    (
        "Despliegue y operación",
        "Contenerización de base, backend y frontend; configuración por variables de entorno e "
        "inicialización automática del esquema y de los datos semilla.",
        "Composición Docker, proxy inverso y bitácora de recordatorios.",
        "docker-compose.yml y nginx.conf",
    ),
]

FUNCTIONAL_ROWS = [
    (
        "RF-01",
        "Registro de pacientes",
        "Captura y valida nombres, apellidos, fecha de nacimiento, teléfono, dirección y correo. "
        "Rechaza con 400 los campos obligatorios vacíos, la fecha futura y el correo mal formado, "
        "sin persistir el registro.",
        "Recepción, Dentista, Administrador",
        "Alta",
        "POST /api/pacientes · vista Pacientes",
    ),
    (
        "RF-02",
        "Edición y baja de pacientes",
        "Actualiza los datos del paciente y aplica baja lógica: sale de listados y búsquedas, no "
        "admite citas nuevas y conserva citas, tratamientos y facturas históricas. La baja "
        "repetida es idempotente.",
        "Recepción, Administrador",
        "Alta",
        "PUT y DELETE /api/pacientes/{id} · vista Pacientes",
    ),
    (
        "RF-03",
        "Historial clínico por paciente",
        "Devuelve las citas y los tratamientos realizados del paciente, ordenados del más "
        "reciente al más antiguo. Un paciente inexistente responde 404.",
        "Recepción, Dentista, Administrador",
        "Alta",
        "GET /api/pacientes/{id}/historial · vista Historial clínico",
    ),
    (
        "RF-04",
        "Programación de citas",
        "Registra paciente, tratamiento, dentista, fecha y hora de inicio. La cita nace en estado "
        "PROGRAMADA, rechaza con 422 una fecha pasada y con 409 el solapamiento con otra cita "
        "activa del mismo dentista.",
        "Recepción, Dentista",
        "Alta",
        "POST /api/citas · vista Agenda",
    ),
    (
        "RF-05",
        "Recordatorios de citas",
        "Genera un recordatorio pendiente 24 horas antes de cada cita programada, despacha los "
        "vencidos por el canal configurado y registra el resultado en la bitácora. Sin servidor "
        "de correo el envío queda como SIMULADA en lugar de fallar.",
        "Sistema, Recepción",
        "Alta",
        "GET /api/citas/{id}/recordatorios · POST /api/recordatorios/procesar",
    ),
    (
        "RF-06",
        "Reprogramación y cancelación",
        "Reprograma a una nueva fecha u odontólogo aplicando las reglas de RF-04 y deja la cita "
        "en REPROGRAMADA; cancelar la deja en CANCELADA y anula sus recordatorios pendientes. "
        "Ninguna operación se permite sobre una cita ATENDIDA.",
        "Recepción",
        "Alta",
        "PATCH /api/citas/{id}/reprogramar · /cancelar",
    ),
    (
        "RF-07",
        "Catálogo de tratamientos",
        "Crea y edita tratamientos con código único, nombre, descripción, precio mayor que cero y "
        "duración. El código duplicado responde 409 y el listado muestra solo los activos.",
        "Administrador",
        "Alta",
        "POST y PUT /api/tratamientos · vista Catálogo",
    ),
    (
        "RF-08",
        "Tratamientos realizados",
        "Registra paciente, tratamiento, fecha, detalle del procedimiento y dentista. Congela el "
        "costo del catálogo y, si se vincula una cita, la marca ATENDIDA y valida que pertenezca "
        "al mismo paciente (409).",
        "Dentista",
        "Alta",
        "POST /api/tratamientos-realizados · integración en Historial",
    ),
    (
        "RF-09",
        "Facturación",
        "Emite comprobantes agrupando tratamientos realizados, con numeración correlativa sin "
        "huecos, total calculado y estado EMITIDA. Rechaza con 409 un realizado ya facturado o de "
        "otro paciente y entrega el comprobante en PDF.",
        "Recepción, Administrador",
        "Alta",
        "POST /api/facturas · GET /api/facturas/{id}/pdf · vista Facturación",
    ),
    (
        "RF-10",
        "Pagos y pagos parciales",
        "Registra pagos con monto, método y referencia. Impide que la suma de pagos supere el "
        "total (409) y recalcula el saldo: PAGO_PARCIAL mientras quede saldo y PAGADA al llegar "
        "a cero.",
        "Recepción, Administrador",
        "Alta",
        "POST /api/facturas/{id}/pagos · vista Facturación",
    ),
]

NON_FUNCTIONAL_ROWS = [
    (
        "RNF-01",
        "Disponibilidad",
        "El servicio se reinicia solo y la base de datos reporta salud antes de recibir tráfico; "
        "la sesión sin estado permite reemplazar una instancia sin perder trabajo.",
        "Los tres contenedores quedan arriba con una orden, la base responde pg_isready antes de "
        "iniciar el backend y el objetivo es 99 % en horario de atención.",
        "restart: unless-stopped, healthcheck cada 10 s, JWT sin estado de sesión.",
    ),
    (
        "RNF-02",
        "Integridad transaccional",
        "Toda operación que toca varias tablas se ejecuta en una transacción y las reglas críticas "
        "también se declaran en la base de datos.",
        "No existen facturas sin detalle, pagos huérfanos ni tratamientos facturados dos veces; "
        "los registros financieros no se eliminan.",
        "@Transactional, claves foráneas RESTRICT, CHECK de saldo, UNIQUE en factura_detalle y "
        "triggers de inmutabilidad.",
    ),
    (
        "RNF-03",
        "Seguridad por roles",
        "Solo usuarios autenticados acceden a la API y cada operación exige un permiso del rol; "
        "la denegación es el comportamiento por defecto.",
        "Tres roles y veintidós permisos; sin token la respuesta es 401 y con permiso insuficiente "
        "es 403; toda ruta declara su autorización.",
        "SecurityConfig, JWT HS256 de ocho horas, BCrypt con costo 12, matriz role_permission y "
        "audit_log.",
    ),
    (
        "RNF-04",
        "Usabilidad",
        "La interfaz guía el flujo de recepción sin recargar la página, valida en el formulario y "
        "muestra solo las acciones que el perfil puede ejecutar.",
        "Cada flujo cubierto se completa desde una vista con formulario y mensajes de error; el "
        "menú lateral se filtra por permisos.",
        "Aplicación de una sola página con router propio, shell con menú por permisos, "
        "notificaciones y validación previa al envío.",
    ),
    (
        "RNF-05",
        "Portabilidad con Docker",
        "El sistema completo se levanta sin instalar Java ni PostgreSQL en el equipo anfitrión y "
        "la configuración se inyecta por variables de entorno.",
        "Una orden construye y levanta los servicios, inicializa esquema y datos semilla y publica "
        "la aplicación en el puerto 80.",
        "docker-compose.yml, Dockerfile del backend, Nginx, volúmenes nombrados y variables de "
        "entorno para base, JWT, CORS y correo.",
    ),
    (
        "RNF-06",
        "Rendimiento",
        "Las consultas de recepción —búsqueda de pacientes y agenda por odontólogo— se resuelven "
        "por índice y no dependen de recorrer tablas completas.",
        "Los planes de ejecución usan los índices declarados y el objetivo es un percentil 95 "
        "inferior a 500 ms en entorno local; no se reportan mediciones de producción.",
        "Dieciocho índices, pool Hikari de veinte conexiones, hilos virtuales y registro SQL "
        "desactivado.",
    ),
]

TRACEABILITY_ROWS = [
    (
        "RF-01",
        "S1",
        "UC01 Registrar paciente",
        "PacienteController, PacienteService",
        "PacienteControllerTest, PacienteServiceTest",
        "Vista Pacientes",
    ),
    (
        "RF-02",
        "S2",
        "UC02 Editar paciente; UC03 Dar de baja",
        "PacienteController, PacienteService",
        "PacienteServiceTest",
        "Vista Pacientes",
    ),
    (
        "RF-03",
        "S3",
        "UC04 Consultar historial",
        "HistorialController, HistorialService",
        "HistorialControllerTest, HistorialServiceTest",
        "Vista Historial clínico",
    ),
    (
        "RF-04",
        "S4",
        "UC05 Programar cita",
        "CitaController, CitaService",
        "CitaControllerTest, CitaServiceTest",
        "Vista Agenda",
    ),
    (
        "RF-05",
        "S5, S15",
        "UC09 Despachar recordatorio",
        "RecordatorioController, RecordatorioService",
        "RecordatorioControllerTest, RecordatorioServiceTest",
        "Vista Recordatorios",
    ),
    (
        "RF-06",
        "S6",
        "UC06 Reprogramar; UC07 Cancelar",
        "CitaController, CitaService",
        "CitaServiceTest",
        "Vista Agenda",
    ),
    (
        "RF-07",
        "S7",
        "UC10 Gestionar catálogo",
        "TratamientoController, TratamientoService",
        "TratamientoControllerTest, TratamientoServiceTest",
        "Vista Catálogo",
    ),
    (
        "RF-08",
        "S8",
        "UC11 Registrar tratamiento",
        "TratamientoRealizadoController, TratamientoRealizadoService",
        "TratamientoRealizadoControllerTest, TratamientoRealizadoServiceTest",
        "Registro por API; consulta en Historial",
    ),
    (
        "RF-09",
        "S9",
        "UC12 Emitir factura",
        "FacturaController, FacturaService, FacturaPdfService",
        "FacturaControllerTest, FacturaServiceTest, NumeroFacturaTest",
        "Vista Facturación; PDF en API",
    ),
    (
        "RF-10",
        "S10",
        "UC13 Registrar pago",
        "PagoController, PagoService",
        "PagoControllerTest, PagoServiceTest",
        "Vista Facturación",
    ),
]

REFERENCES = [
    [
        ("American Psychological Association. (2020). ", False),
        ("Publication manual of the American Psychological Association", True),
        (" (7th ed.). https://doi.org/10.1037/0000165-000", False),
    ],
    [
        ("Docker Inc. (s. f.). ", False),
        ("Docker Compose overview", True),
        (". Recuperado de https://docs.docker.com/compose/", False),
    ],
    [
        ("Fowler, M. (2002). ", False),
        ("Patterns of enterprise application architecture", True),
        (". Addison-Wesley.", False),
    ],
    [
        ("Kendall, K. E., y Kendall, J. E. (2019). ", False),
        ("Systems analysis and design", True),
        (" (10th ed.). Pearson.", False),
    ],
    [
        ("OWASP Foundation. (s. f.). ", False),
        ("OWASP Top 10:2021", True),
        (". Recuperado de https://owasp.org/Top10/", False),
    ],
    [
        ("PlantUML. (s. f.). ", False),
        ("PlantUML language reference guide", True),
        (". Recuperado de https://plantuml.com/guide", False),
    ],
    [
        ("Pressman, R. S., y Maxim, B. R. (2020). ", False),
        ("Software engineering: A practitioner's approach", True),
        (" (9th ed.). McGraw-Hill Education.", False),
    ],
    [
        ("Sommerville, I. (2016). ", False),
        ("Software engineering", True),
        (" (10th ed.). Pearson.", False),
    ],
    [
        ("Spring. (s. f.). ", False),
        ("Spring Boot reference documentation", True),
        (". Recuperado de https://docs.spring.io/spring-boot/", False),
    ],
    [
        ("The PostgreSQL Global Development Group. (s. f.). ", False),
        ("PostgreSQL 16 documentation", True),
        (". Recuperado de https://www.postgresql.org/docs/16/", False),
    ],
]

QUESTIONNAIRE = [
    (
        "A.1 Gestión de pacientes",
        [
            "¿Qué datos del paciente se registran hoy y cuáles resultan imprescindibles para la atención?",
            "¿Cómo se identifica de forma única a un paciente cuando dos personas comparten nombre y apellido?",
            "¿Qué ocurre actualmente cuando un paciente deja de asistir al consultorio? ¿Se elimina su información?",
            "¿Qué documentos o antecedentes del paciente se consultan con mayor frecuencia durante la atención?",
            "¿Qué información del historial clínico debe conservarse aunque el paciente cause baja?",
        ],
    ),
    (
        "A.2 Agenda y citas",
        [
            "¿Cómo se agenda hoy una cita y cuánto tiempo insume el proceso completo?",
            "¿Qué criterios impiden asignar dos citas al mismo odontólogo en el mismo horario?",
            "¿Qué sucede cuando un paciente necesita reprogramar o cancelar una cita?",
            "¿Qué estados atraviesa una cita desde que se agenda hasta que se atiende o se pierde?",
            "¿Qué información necesita recepción para consultar la agenda diaria de cada odontólogo?",
        ],
    ),
    (
        "A.3 Recordatorios",
        [
            "¿Con cuánta anticipación conviene recordar una cita y por qué canal se prefiere hacerlo?",
            "¿Qué proporción de citas se pierde por falta de recordatorio o por olvido del paciente?",
            "¿Qué mensaje mínimo debe contener el recordatorio para que el paciente lo identifique?",
            "¿Qué debe hacer el sistema cuando el envío del recordatorio falla o no hay servidor de correo disponible?",
        ],
    ),
    (
        "A.4 Catálogo y tratamientos realizados",
        [
            "¿Quién mantiene el catálogo de tratamientos y con qué frecuencia cambian los precios?",
            "¿Qué datos describen un tratamiento: código, nombre, descripción, precio y duración?",
            "¿Cómo se registra un procedimiento realizado y qué detalle clínico debe quedar documentado?",
            "¿Qué debe ocurrir con el costo de un tratamiento si el precio del catálogo cambia después de realizarlo?",
            "¿Qué relación debe existir entre una cita atendida y el tratamiento realizado?",
        ],
    ),
    (
        "A.5 Facturación y pagos",
        [
            "¿Cómo se emite hoy un comprobante y qué datos debe mostrar de forma obligatoria?",
            "¿Se factura un tratamiento por separado o se agrupan varios en un mismo comprobante?",
            "¿Qué medios de pago se aceptan y qué referencia se conserva de cada cobro?",
            "¿Cómo se registra un pago parcial y cómo se informa el saldo pendiente al paciente?",
            "¿Qué ocurre cuando un cobro intenta superar el total de la factura o cuando se factura dos veces el mismo tratamiento?",
        ],
    ),
    (
        "A.6 Seguridad y control de acceso",
        [
            "¿Qué perfiles usan el sistema y qué tareas puede ejecutar cada uno?",
            "¿Qué información clínica o financiera debe quedar restringida a determinados perfiles?",
            "¿Qué registro de auditoría se necesita para reconstruir quién creó, modificó o cobró un registro?",
            "¿Qué política de contraseñas y de expiración de sesión aplica el consultorio?",
        ],
    ),
    (
        "A.7 Usabilidad y operación",
        [
            "¿Qué tareas realiza recepción con mayor volumen durante la jornada y cuánto tiempo demanda cada una?",
            "¿Qué información debe estar visible en la pantalla inicial al comenzar el día?",
            "¿Qué equipos y navegadores se utilizan y qué tamaño de pantalla predomina?",
            "¿Qué respaldo y qué ventana de mantenimiento tolera el consultorio?",
        ],
    ),
    (
        "A.8 Cierre",
        [
            "¿Qué funcionalidad considera prioritaria para una primera entrega y cuál puede esperar?",
            "¿Qué criterio permitiría afirmar que el sistema está listo para reemplazar el proceso actual?",
        ],
    ),
]


# ---------------------------------------------------------------------------
# Section builders
# ---------------------------------------------------------------------------


def build_cover(document):
    for _ in range(6):
        add_p(document, "", first_indent=False, after=0)

    add_p(
        document,
        DOCUMENT_TITLE,
        align=WD_ALIGN_PARAGRAPH.CENTER,
        first_indent=False,
        size=H1_SIZE,
        bold=True,
        after=6,
    )
    add_p(
        document,
        DOCUMENT_SUBTITLE,
        align=WD_ALIGN_PARAGRAPH.CENTER,
        first_indent=False,
        italic=True,
        after=36,
    )
    for text, _ in COVER_LINES:
        add_p(
            document,
            text,
            align=WD_ALIGN_PARAGRAPH.CENTER,
            first_indent=False,
            after=6,
        )
    document.add_page_break()


def build_abstract(document):
    add_h1(document, "1. Resumen y Abstract")
    add_h2(document, "Resumen")
    for paragraph in RESUMEN:
        add_p(document, paragraph)
    add_keywords(document, "Palabras clave", KEYWORDS_ES)

    add_h2(document, "Abstract")
    for paragraph in ABSTRACT:
        add_p(document, paragraph)
    add_keywords(document, "Keywords", KEYWORDS_EN)


def add_keywords(document, label, keywords):
    paragraph = add_p(document, first_indent=True, after=6)
    set_run_font(paragraph.add_run(f"{label}: "), italic=True)
    set_run_font(paragraph.add_run(", ".join(keywords) + "."))


def build_introduction(document):
    add_h1(document, "2. Introducción y Contextualización del Consultorio Dental")

    add_h2(document, "2.1 Contextualización")
    add_p(
        document,
        "El consultorio dental objeto de este análisis es una unidad de atención ambulatoria que "
        "organiza su trabajo en torno a tres funciones inseparables: la recepción del paciente, la "
        "atención clínica y la gestión administrativa del cobro. En la operación previa al sistema, "
        "la agenda se llevaba en soportes dispersos, el historial clínico se reconstruía a partir de "
        "anotaciones y la facturación dependía de cálculos manuales. Esa fragmentación produce "
        "citas duplicadas, pacientes que no asisten por falta de recordatorio, tratamientos "
        "facturados dos veces y serias dificultades para responder consultas sobre saldos.",
    )
    add_p(
        document,
        "La institución solicitó un sistema que concentrara la información en una base de datos "
        "relacional, que permitiera completar los flujos de trabajo desde una interfaz gráfica "
        "amigable y que conservara las garantías de integridad y disponibilidad exigidas por el "
        "documento del curso. El proyecto se apoyó en una base técnica ya probada —un ERP de "
        "comercio electrónico de calzado— de la que se conservaron la seguridad, el motor de "
        "documentos PDF, el cliente HTTP y la orquestación de contenedores, mientras se reemplazaba "
        "por completo el dominio del negocio.",
    )
    add_p(
        document,
        "El resultado es un sistema de gestión odontológica que cubre el ciclo completo de atención: "
        "registro del paciente, programación y seguimiento de citas, recordatorios, registro de "
        "procedimientos realizados, emisión de comprobantes y cobro total o parcial. Cada uno de esos "
        "flujos se especificó como un requerimiento numerado (S1 a S10) y se implementó con su "
        "prueba automatizada, de modo que la documentación y el código pueden auditarse de forma "
        "cruzada.",
    )

    add_h2(document, "2.2 Planteamiento del Problema")
    add_p(
        document,
        "El problema central es la ausencia de una fuente única de verdad para la operación del "
        "consultorio. Sin un registro unificado, la recepción no puede verificar la disponibilidad "
        "real de un odontólogo, el área clínica no accede al historial completo del paciente y la "
        "administración no puede determinar con exactitud cuánto se debe ni cuánto se ha cobrado. "
        "La consecuencia es una pérdida de tiempo operativa, un riesgo de error en los cobros y una "
        "imposibilidad práctica de auditar lo ocurrido.",
    )
    add_p(
        document,
        "El sistema debe resolver ese problema sin introducir nuevas fuentes de error: las reglas "
        "críticas del negocio —solapamiento de citas, fechas pasadas, duplicación de códigos, "
        "facturación única de un tratamiento y tope de los pagos— deben validarse tanto en la capa "
        "de servicio como en la base de datos, y toda operación debe quedar registrada para su "
        "auditoría.",
    )

    add_h2(document, "2.3 Objetivos")
    add_rich_p(
        document,
        [
            (
                "Objetivo general. ",
                True,
                False,
            ),
            (
                "Diseñar, documentar e implementar un sistema de gestión para un consultorio dental "
                "que integre pacientes, citas, tratamientos, facturación y pagos sobre una base de "
                "datos relacional, con acceso seguro por roles y una interfaz gráfica que permita "
                "completar los flujos de trabajo sin recurrir a registros manuales.",
                False,
                False,
            ),
        ],
        first_indent=False,
        after=6,
    )
    for marker, text in (
        (
            "a)",
            "Especificar los requerimientos funcionales y no funcionales del consultorio y "
            "trazarlos a las diez especificaciones del curso (S1-S10).",
        ),
        (
            "b)",
            "Modelar el negocio con los diagramas UML de casos de uso, clases, entidad-relación, "
            "secuencia, actividades, estados, componentes y despliegue.",
        ),
        (
            "c)",
            "Implementar una API REST segura con control de acceso por roles, validaciones de "
            "negocio y persistencia transaccional en PostgreSQL.",
        ),
        (
            "d)",
            "Entregar una interfaz navegable que consuma la API y permita operar los módulos del "
            "consultorio según el perfil del usuario.",
        ),
        (
            "e)",
            "Generar el documento de entrega en formato APA 7 mediante un script reproducible que "
            "incorpore automáticamente las figuras disponibles y las tablas de requerimientos.",
        ),
    ):
        add_list_item(document, marker, text)

    add_h2(document, "2.4 Alcance y Limitaciones")
    add_p(
        document,
        "El alcance cubre los diez requerimientos funcionales del curso y los atributos de calidad "
        "declarados: disponibilidad, integridad transaccional, seguridad por roles, usabilidad, "
        "portabilidad con Docker y rendimiento. Quedan fuera del alcance la gestión de obras "
        "sociales y convenios, el control de inventario de insumos, la facturación electrónica "
        "certificada ante la administración tributaria, la historia clínica con firma digital y la "
        "operación multi-sede. El documento del curso tampoco menciona impuestos, por lo que el "
        "total de la factura es la suma del costo congelado de los tratamientos facturados.",
    )
    add_p(
        document,
        "La entrega se apoya en el código realmente presente en el repositorio. "
        + describe_frontend_coverage()
        + " Los recordatorios se despachan mediante un proceso programado con cadencia "
        "configurable y el correo se envía por SMTP configurable; cuando no hay servidor declarado, "
        "la bitácora registra el intento como simulado en lugar de fallar. Los diagramas UML que "
        "todavía no tienen PNG renderizado se detallan en las conclusiones y en las recomendaciones, "
        "sin ocultarlos en la narrativa del documento.",
    )


def describe_frontend_coverage():
    """Describe the frontend that ships, reading the router instead of guessing."""
    routes = read_frontend_routes()
    if not routes or not routes[0]:
        return (
            "La interfaz se organiza como una aplicación de una sola página que consume la API y "
            "filtra sus rutas según los permisos de la sesión."
        )
    active, reserved = routes
    sentences = [
        "La interfaz expone las vistas activas del menú: " + join_spanish(active) + "."
    ]
    if reserved:
        sentences.append(
            "Las pantallas de "
            + join_spanish(reserved)
            + " permanecen como rutas reservadas en el router; sus servicios de API ya están "
            "escritos y activarlas no requiere cambios en el backend."
        )
    else:
        sentences.append(
            "Todas las rutas declaradas en el router tienen su módulo de pantalla entregado."
        )
    return " ".join(sentences)


def build_methodology(document, numbering):
    add_h1(document, "3. Marco Metodológico por Etapas del Ciclo de Vida")
    add_h2(document, "3.1 Enfoque Adoptado")
    add_p(
        document,
        "Se adoptó un ciclo de vida clásico con entregas incrementales. El análisis y el diseño se "
        "documentaron antes de construir, pero la implementación avanzó por módulos completos, cada "
        "uno con su esquema, su servicio, su controlador, su prueba y su commit revisable. El "
        "método combina el enfoque de análisis estructurado de Kendall y Kendall (2019) para la "
        "comprensión del negocio, las prácticas de ingeniería de software de Pressman y Maxim "
        "(2020) y Sommerville (2016) para la especificación, la construcción y la verificación, y "
        "la separación de responsabilidades por capas descrita por Fowler (2002).",
    )
    add_p(
        document,
        "Las especificaciones S1-S10 funcionaron como contrato de entrada: ninguna funcionalidad se "
        "consideró terminada si no podía rastrearse hasta una especificación, un endpoint, una "
        "prueba automatizada y, cuando corresponde, una vista de la interfaz. El repositorio "
        "conserva además un registro de decisiones (ODD) que documenta el alcance del reemplazo del "
        "dominio, la baja lógica de pacientes, la ausencia de impuestos en la factura y la "
        "reutilización de la infraestructura.",
    )

    add_h2(document, "3.2 Etapas del Ciclo de Vida")
    for stage, description in (
        (
            "Elicitación. ",
            "Se relevaron los procesos de recepción, atención y cobro, se identificaron los actores "
            "y se acordaron las reglas que el sistema debe hacer cumplir. El instrumento del anexo A "
            "sistematiza ese relevamiento por dimensiones.",
        ),
        (
            "Análisis. ",
            "Los procesos se descompusieron en casos de uso y en un modelo conceptual; cada "
            "requerimiento se numeró y se vinculó con su regla de negocio y su respuesta esperada "
            "ante errores.",
        ),
        (
            "Diseño. ",
            "Se definió una arquitectura por capas y por funcionalidad, el contrato REST de cada "
            "recurso, el modelo relacional en tercera forma normal y el esquema de seguridad con "
            "roles y permisos granulares.",
        ),
        (
            "Construcción. ",
            "Cada módulo se implementó sobre la convención controller, service, repository y dto, "
            "con validación en el servicio y restricciones equivalentes en la base de datos.",
        ),
        (
            "Verificación. ",
            "Las pruebas de servicio cubren las reglas del negocio y las de controlador cubren el "
            "contrato HTTP y la seguridad; el motor PDF y la autenticación tienen pruebas propias.",
        ),
        (
            "Despliegue y operación. ",
            "La solución se ejecuta con Docker Compose, inicializa el esquema y los datos semilla al "
            "crear el volumen y se configura por variables de entorno.",
        ),
    ):
        add_rich_p(
            document,
            [(stage, True, False), (description, False, False)],
            first_indent=True,
            after=6,
        )

    add_apa_table(
        document,
        numbering,
        "Etapas del ciclo de vida, artefactos y evidencia en el repositorio",
        ["Etapa", "Actividades principales", "Artefactos", "Evidencia en el repositorio"],
        STAGE_ROWS,
        [1.1, 1.95, 1.65, 1.8],
        "Elaboración propia a partir del ciclo de vida de análisis y diseño de sistemas "
        "(Kendall y Kendall, 2019) y de los artefactos efectivamente versionados en el proyecto.",
    )

    add_h2(document, "3.3 Herramientas y Control de Cambios")
    add_p(
        document,
        "El backend utiliza Spring Boot 3.3.4 sobre Java 21 con Maven, JPA e Hibernate sobre "
        "PostgreSQL 16 (Spring, s. f.; The PostgreSQL Global Development Group, s. f.), y "
        "validación con Jakarta Validation. El frontend es una aplicación de una sola página en "
        "JavaScript nativo, sin paso de compilación, servida por Nginx. Los diagramas se escriben en "
        "PlantUML (PlantUML, s. f.) y se renderizan a PNG; la documentación se genera con "
        "python-docx siguiendo el manual de la American Psychological Association (2020). El "
        "control de versiones se organiza por unidad de trabajo: cada módulo deja el árbol "
        "compilando y la suite en verde, de modo que cualquier cambio puede revisarse de forma "
        "aislada.",
    )


def build_requirements_analysis(document, numbering):
    add_h1(document, "4. Análisis de Requisitos y Modelado del Negocio")

    add_h2(document, "4.1 Actores y Roles")
    add_p(
        document,
        "El sistema reconoce tres perfiles operativos y un actor de sistema. La recepción administra "
        "pacientes, agenda, facturación y recordatorios; el odontólogo consulta el historial, "
        "programa sus citas y registra los procedimientos realizados; el administrador gobierna el "
        "catálogo, los usuarios y la auditoría. El actor de sistema ejecuta el despacho de "
        "recordatorios y el cálculo de saldos. Los permisos se declaran de forma granular y se "
        "asignan a cada rol en la tabla role_permission, lo que permite ajustar el alcance sin "
        "cambiar el código.",
    )

    add_h2(document, "4.2 Procesos del Negocio")
    add_p(
        document,
        "El proceso principal comienza cuando la recepción registra al paciente y verifica que no "
        "exista un registro previo. Luego agenda una cita indicando tratamiento, odontólogo, fecha y "
        "hora; el sistema valida la disponibilidad y genera el recordatorio pendiente. El día de la "
        "atención, el odontólogo consulta el historial, realiza el procedimiento y lo registra con su "
        "detalle y su costo congelado. La recepción agrupa los tratamientos realizados en una "
        "factura, la emite con numeración correlativa y registra los pagos a medida que el paciente "
        "abona, hasta cancelar el saldo o dejarlo como pago parcial.",
    )
    add_p(
        document,
        "Alrededor de ese flujo conviven procesos de soporte: la reprogramación y la cancelación de "
        "citas, la baja lógica de pacientes, la consulta del historial clínico, la administración del "
        "catálogo de tratamientos y la consulta de la bitácora de recordatorios. Cada proceso tiene "
        "un responsable identificable y un conjunto de reglas que el sistema hace cumplir de forma "
        "automática.",
    )
    for marker, text in (
        (
            "1.",
            "Recepción registra o localiza al paciente y confirma sus datos de contacto.",
        ),
        (
            "2.",
            "Recepción o el odontólogo programa la cita; el sistema verifica fecha futura y "
            "solapamiento del odontólogo.",
        ),
        (
            "3.",
            "El proceso de recordatorios despacha el aviso 24 horas antes y registra el resultado.",
        ),
        (
            "4.",
            "El odontólogo atiende la cita y registra el tratamiento realizado; la cita pasa a "
            "ATENDIDA.",
        ),
        (
            "5.",
            "Recepción emite la factura con los tratamientos realizados y entrega el comprobante PDF.",
        ),
        (
            "6.",
            "Recepción registra los pagos, totales o parciales, y el sistema recalcula el saldo y el "
            "estado del comprobante.",
        ),
    ):
        add_list_item(document, marker, text)

    add_h2(document, "4.3 Reglas de Negocio")
    add_p(
        document,
        "Las reglas siguientes son observables en el código y en el esquema, y cada una tiene una "
        "prueba automatizada que la protege. Se enuncian en el lenguaje del negocio para que puedan "
        "validarse con el personal del consultorio.",
    )
    for rule, explanation in (
        (
            "Un paciente no se elimina, se da de baja. ",
            "La baja lógica lo retira de listados y búsquedas y bloquea citas nuevas, pero conserva "
            "intactos sus citas, tratamientos y facturas, tal como exige la integridad del historial.",
        ),
        (
            "Un odontólogo no puede tener dos citas activas superpuestas. ",
            "La validación se aplica al programar y al reprogramar, y devuelve 409 sin crear ni "
            "modificar registros.",
        ),
        (
            "No se agenda en el pasado. ",
            "Una fecha anterior al momento de la solicitud se rechaza con 422.",
        ),
        (
            "El costo de un tratamiento se congela al realizarlo. ",
            "Cambiar el precio del catálogo no reescribe el historial clínico ni lo ya facturado.",
        ),
        (
            "Un tratamiento realizado se factura una sola vez. ",
            "La restricción UNIQUE sobre id_realizado en factura_detalle y la validación del servicio "
            "impiden la duplicación y no consumen numeración cuando rechazan.",
        ),
        (
            "La numeración de facturas es correlativa y sin huecos. ",
            "El número se asigna al final, cuando la solicitud superó todas las validaciones.",
        ),
        (
            "La suma de pagos nunca supera el total. ",
            "El servicio rechaza el exceso con 409 y la base refuerza la invariante con una "
            "restricción CHECK sobre el saldo.",
        ),
        (
            "Las citas atendidas no se reprograman ni se cancelan. ",
            "El estado ATENDIDA es terminal para esas operaciones.",
        ),
    ):
        add_rich_p(
            document,
            [(rule, True, False), (explanation, False, False)],
            first_indent=True,
            after=6,
        )

    add_h2(document, "4.4 Modelo de Dominio")
    add_p(
        document,
        "El modelo relacional se organiza en catálogos de dominio controlado, seguridad, pacientes, "
        "tratamientos, citas, facturación, recordatorios y auditoría. Los catálogos (estado de cita, "
        "canal y estado de notificación, método de pago, estado de factura y tipo de acción de "
        "auditoría) evitan valores libres en columnas críticas. Los usuarios se relacionan con roles "
        "mediante app_user y user_role, y los roles con permisos mediante role_permission.",
    )
    add_p(
        document,
        "Las entidades centrales son paciente, tratamiento, cita, tratamiento_realizado, factura, "
        "factura_detalle, pago y notificacion_cita. La cita referencia al paciente, al tratamiento y "
        "al odontólogo, y conserva el vínculo con la cita que reemplaza cuando se reprograma. El "
        "tratamiento realizado referencia opcionalmente la cita que lo originó y guarda el costo "
        "congelado. La factura agrupa detalles y expone un saldo vivo; cada pago se imputa a una "
        "factura con su método y su referencia. La bitácora de notificaciones registra destino, "
        "fecha objetivo, mensaje, resultado y error.",
    )
    add_p(
        document,
        "El esquema está normalizado en tercera forma normal, usa claves foráneas con integridad "
        "referencial explícita, restricciones CHECK para las invariantes numéricas y de fecha, "
        "triggers para sellar updated_at y proteger los registros financieros, y dieciocho índices "
        "orientados a las consultas de recepción, agenda, historial, facturación y cola de "
        "recordatorios.",
    )

    add_h2(document, "4.5 Diagramas UML del Sistema")
    add_p(
        document,
        "Los diagramas se versionan como fuentes PlantUML en docs/uml/ y se renderizan a PNG en "
        "docs/uml/out/. Este documento los incorpora como figuras numeradas con su título y su nota "
        "de fuente; las figuras se descubren en tiempo de generación, de modo que al renderizar un "
        "diagrama nuevo basta volver a ejecutar el script para que aparezca en su lugar. Los "
        "diagramas que aún no tienen PNG se omiten con una advertencia en la consola y no "
        "interrumpen la construcción del documento.",
    )
    matched, missing, unknown = discover_figures(UML_OUT_DIR)
    for spec, path in matched:
        add_figure(
            document,
            numbering,
            spec.title,
            path,
            f"Elaboración propia a partir de {spec.source}.",
        )
    for stem, path in unknown:
        add_figure(
            document,
            numbering,
            humanize_key(stem),
            path,
            "Elaboración propia a partir del diagrama versionado en docs/uml/.",
        )
    for key in missing:
        print(f"WARNING: missing diagram '{key}' in {UML_OUT_DIR}; skipped in the document.")


def build_functional_requirements(document, numbering):
    add_h1(document, "5. Requerimientos Funcionales")
    add_p(
        document,
        "Los requerimientos funcionales se derivan directamente de las especificaciones S1-S10 del "
        "curso y se numeran como RF-01 a RF-10 para poder trazarlos a los casos de uso, al código y "
        "a las pruebas. La tabla 2 resume, para cada requerimiento, su descripción, el actor que lo "
        "ejecuta, su prioridad y el recurso de la API o la vista que lo materializa.",
    )
    add_apa_table(
        document,
        numbering,
        "Matriz de requerimientos funcionales trazada a las especificaciones S1-S10",
        ["ID", "Nombre", "Descripción", "Actor", "Prioridad", "Endpoint / UI"],
        FUNCTIONAL_ROWS,
        [0.5, 1.0, 2.15, 0.9, 0.5, 1.45],
        "Trazabilidad directa con las especificaciones S1-S10 de odd/tasks/clinica-dental.md. La "
        "columna Endpoint/UI indica el recurso REST y la vista que lo consume.",
    )

    add_h2(document, "5.1 Criterios de Aceptación por Módulo")
    for title, text in (
        (
            "Pacientes (RF-01 a RF-03). ",
            "El alta exige los seis campos obligatorios, valida el formato del correo y rechaza una "
            "fecha de nacimiento futura. La edición actualiza los mismos campos y la baja es "
            "idempotente. El historial devuelve citas y tratamientos ordenados de forma descendente y "
            "responde 404 si el paciente no existe.",
        ),
        (
            "Agenda (RF-04 y RF-06). ",
            "La cita se crea en estado PROGRAMADA y exige paciente, tratamiento, odontólogo y fecha. "
            "El servicio rechaza fechas pasadas con 422 y solapamientos con 409. La reprogramación "
            "conserva el vínculo con la cita de origen y vuelve a aplicar ambas reglas; la "
            "cancelación anula los recordatorios pendientes. Una cita ATENDIDA no admite ninguna de "
            "las dos operaciones.",
        ),
        (
            "Recordatorios (RF-05). ",
            "Cada cita programada genera un recordatorio pendiente con fecha objetivo 24 horas antes. "
            "Un proceso programado cada quince minutos, con cadencia configurable por variables de "
            "entorno, despacha solo los vencidos, registra el destino, el mensaje y el resultado, y "
            "guarda el error cuando el envío falla. El envío usa SMTP configurable y, si no hay "
            "servidor declarado, la bitácora registra SIMULADA. La bitácora de una cita es consultable "
            "y el despacho también puede forzarse desde la API.",
        ),
        (
            "Tratamientos (RF-07 y RF-08). ",
            "El catálogo exige código único, precio mayor que cero y duración positiva; el duplicado "
            "responde 409 y el listado filtra los inactivos. El registro de un tratamiento realizado "
            "toma el precio vigente, lo congela y, si recibe una cita, verifica que pertenezca al "
            "mismo paciente antes de marcarla ATENDIDA.",
        ),
        (
            "Facturación y pagos (RF-09 y RF-10). ",
            "La factura agrupa uno o más tratamientos realizados, calcula el total como suma de los "
            "costos congelados, asigna el número correlativo al final y nace con saldo igual al "
            "total. Cada pago descuenta el saldo y mueve el estado a PAGO_PARCIAL o PAGADA; un pago "
            "que exceda el saldo se rechaza con 409.",
        ),
    ):
        add_rich_p(document, [(title, True, False), (text, False, False)])

    add_h2(document, "5.2 Estados y Transiciones")
    add_p(
        document,
        "El ciclo de vida de la cita reconoce los estados PROGRAMADA, CONFIRMADA, REPROGRAMADA, "
        "ATENDIDA, CANCELADA y NO_ASISTIO. La creación conduce a PROGRAMADA, la reprogramación a "
        "REPROGRAMADA, la atención a ATENDIDA y la cancelación a CANCELADA; ATENDIDA actúa como "
        "estado terminal para las operaciones de agenda. El ciclo de la factura reconoce EMITIDA, "
        "PAGO_PARCIAL, PAGADA y ANULADA; el estado se recalcula en cada pago a partir del saldo, de "
        "modo que no puede quedar desincronizado del importe cobrado.",
    )


def build_non_functional_requirements(document, numbering):
    add_h1(document, "6. Requerimientos No Funcionales")
    add_p(
        document,
        "Los atributos de calidad se declaran como criterios verificables y se anclan en artefactos "
        "concretos de la entrega: configuración de contenedores, restricciones del esquema, matriz "
        "de permisos, decisiones de interfaz e índices de base de datos. La tabla 3 los resume. No se "
        "reportan métricas de producción porque el sistema no está desplegado en un entorno real; "
        "cuando se indica un objetivo, se lo identifica como criterio de aceptación y no como "
        "medición.",
    )
    add_apa_table(
        document,
        numbering,
        "Requerimientos no funcionales, criterios verificables y evidencia en la entrega",
        ["ID", "Categoría", "Requerimiento", "Criterio verificable", "Evidencia en la entrega"],
        NON_FUNCTIONAL_ROWS,
        [0.5, 0.95, 1.7, 1.75, 1.6],
        "Elaboración propia a partir de los atributos de calidad observables en el repositorio. Las "
        "cifras de disponibilidad y rendimiento son objetivos de aceptación, no mediciones de un "
        "entorno productivo.",
    )

    add_h2(document, "6.1 Disponibilidad")
    add_p(
        document,
        "La arquitectura separa base, backend y frontend en tres contenedores con política de "
        "reinicio automático. PostgreSQL declara un healthcheck que el backend espera antes de "
        "arrancar, y la autenticación es stateless: el token JWT contiene la identidad y los "
        "permisos, por lo que reemplazar una instancia del backend no interrumpe las sesiones "
        "válidas. La disponibilidad objetivo se fija en 99 % durante el horario de atención, con la "
        "salvedad de que no existe aún un entorno de producción donde medirla.",
    )

    add_h2(document, "6.2 Integridad Transaccional")
    add_p(
        document,
        "Las operaciones que afectan varias tablas se ejecutan dentro de una transacción y las "
        "invariantes críticas se declaran además en la base: claves foráneas con RESTRICT para los "
        "registros que no deben desaparecer, CHECK para el saldo y los importes positivos, UNIQUE "
        "para impedir que un tratamiento realizado se facture dos veces, y triggers que sellan las "
        "marcas de tiempo e impiden borrar facturas, detalles, pagos y auditoría. Esta duplicación "
        "deliberada de reglas en servicio y esquema protege la información incluso ante un acceso "
        "directo a la base.",
    )

    add_h2(document, "6.3 Seguridad por Roles")
    add_p(
        document,
        "Toda la API está cerrada: no existe portal público. El inicio de sesión valida credenciales "
        "con BCrypt de costo 12 y emite un JWT firmado con HS256 y vigencia de ocho horas. Cada ruta "
        "declara los permisos o roles que la habilitan y cualquier ruta no declarada exige "
        "autenticación. La matriz de datos semilla define tres roles y veintidós permisos "
        "granulares, y la bitácora de auditoría registra tabla, registro, acción, usuario, datos y "
        "dirección de origen. Las respuestas de error de seguridad son JSON con 401 o 403, sin "
        "revelar detalles internos, en línea con las recomendaciones de OWASP Foundation (s. f.).",
    )

    add_h2(document, "6.4 Usabilidad")
    add_p(
        document,
        "La interfaz se organiza como una aplicación de una sola página con un router propio, un "
        "menú lateral construido a partir de las rutas permitidas y vistas que se cargan de forma "
        "diferida. Los formularios validan antes de enviar, las operaciones muestran notificaciones "
        "de éxito o error y un 401 en cualquier petición devuelve al usuario a la compuerta de "
        "inicio de sesión. El criterio de aceptación es que cada flujo cubierto se complete desde "
        "una única vista, sin recargar la página y con mensajes comprensibles para el personal "
        "administrativo.",
    )

    add_h2(document, "6.5 Portabilidad con Docker")
    add_p(
        document,
        "El sistema completo se levanta con una orden de Docker Compose (Docker Inc., s. f.). La base "
        "se inicializa con schema.sql y seed_data.sql montados como scripts de arranque, el backend "
        "se construye desde su Dockerfile y el frontend se sirve con Nginx, que además actúa como "
        "proxy inverso. La configuración sensible se inyecta por variables de entorno para la "
        "conexión a la base, el secreto y la vigencia del JWT, los orígenes permitidos de CORS y los "
        "parámetros del correo. No se requiere instalar Java, Maven ni PostgreSQL en el equipo "
        "anfitrión.",
    )

    add_h2(document, "6.6 Rendimiento")
    add_p(
        document,
        "El backend habilita hilos virtuales y un pool de veinte conexiones, y desactiva el registro "
        "de SQL en ejecución normal. Las consultas de recepción se apoyan en índices específicos: "
        "búsqueda de pacientes por apellido, nombre y correo; agenda por odontólogo y fecha; "
        "historial por paciente y fecha descendente; facturación por paciente y estado; cola de "
        "recordatorios por estado y fecha objetivo. El criterio de aceptación es un percentil 95 "
        "inferior a 500 ms en entorno local con los datos semilla, sin que exista todavía una prueba "
        "de carga formal.",
    )


def build_traceability(document, numbering):
    add_h1(document, "7. Matriz de Trazabilidad")
    add_p(
        document,
        "La matriz vincula cada especificación del curso con su requerimiento funcional, el caso de "
        "uso que lo describe, la implementación en el backend, la prueba automatizada que lo protege "
        "y la interfaz que lo expone. Su propósito es permitir una auditoría bidireccional: dada una "
        "especificación se localiza su código y su prueba, y dado un cambio en el código se "
        "identifica qué requisito y qué caso de uso quedan afectados.",
    )
    add_apa_table(
        document,
        numbering,
        "Matriz de trazabilidad entre especificación, caso de uso, código, prueba e interfaz",
        ["RF", "Spec", "Caso de uso", "Implementación (backend)", "Prueba automatizada", "Interfaz"],
        TRACEABILITY_ROWS,
        [0.55, 0.55, 1.0, 1.55, 1.6, 1.25],
        "Elaboración propia a partir de odd/tasks/clinica-dental.md, docs/uml/01-casos-de-uso.puml y "
        "las clases de prueba de backend/src/test. La columna Interfaz indica la vista del frontend "
        "que consume el recurso; el estado de cada pantalla se detalla en la sección 2.4.",
    )
    add_p(
        document,
        "La cobertura es completa respecto de las especificaciones S1-S10: cada una tiene al menos "
        "un requerimiento funcional, un caso de uso, un controlador, un servicio y una clase de "
        "prueba asociada. Los requerimientos no funcionales se trazan a los artefactos de "
        "infraestructura y seguridad descritos en la tabla 3. Los diagramas UML se incorporan como "
        "figuras en la sección 4.5 y se regeneran desde sus fuentes PlantUML, de modo que la "
        "documentación no puede quedar desactualizada respecto del código sin que el script lo "
        "refleje.",
    )


def build_conclusions(document):
    add_h1(document, "8. Conclusiones y Recomendaciones")
    add_p(
        document,
        "El proyecto demuestra que una base técnica de comercio electrónico puede reutilizarse para "
        "un dominio completamente distinto cuando se conservan las capacidades transversales "
        "—seguridad, persistencia, generación de documentos, contenedores— y se reemplaza sin "
        "concesiones el modelo del negocio. El sistema resultante cubre los diez requerimientos "
        "funcionales del curso con una API REST consistente, un esquema normalizado y una suite de "
        "pruebas que protege las reglas críticas.",
    )
    add_p(
        document,
        "La decisión de implementar la baja de pacientes como baja lógica resolvió la tensión entre "
        "el requisito de eliminar registros y la obligación de conservar el historial clínico y "
        "fiscal. Del mismo modo, congelar el costo del tratamiento al momento de realizarlo y emitir "
        "la numeración de la factura al final de las validaciones elimina dos fuentes clásicas de "
        "error contable sin agregar complejidad innecesaria.",
    )
    add_p(
        document,
        "Quedan brechas identificadas y documentadas. La cobertura de pantallas del frontend se "
        "detalla en la sección 2.4 y depende de los módulos activos del router; los servicios de API "
        "de agenda, facturación, realizados y recordatorios ya están escritos, de modo que activar "
        "una vista no requiere tocar el backend. Varios diagramas UML de la especificación S12 "
        "todavía no están renderizados en docs/uml/out. La suite cubre servicios, controladores, "
        "seguridad, motor PDF y envío SMTP con dobles de prueba, pero no existe todavía una prueba "
        "de extremo a extremo en navegador ni una prueba de carga con datos representativos. Ninguna "
        "de estas brechas afecta la consistencia del núcleo implementado, pero deben cerrarse antes "
        "de considerar el sistema operable por el personal del consultorio.",
    )

    add_h2(document, "Recomendaciones")
    for marker, text in (
        (
            "1.",
            "Consolidar la entrega del frontend con una prueba de humo en navegador que recorra los "
            "flujos S1-S10 con los tres perfiles y que active las rutas que el router todavía "
            "mantenga reservadas.",
        ),
        (
            "2.",
            "Agregar una prueba de integración del envío SMTP contra un servidor de correo simulado y "
            "una alerta operativa cuando la cola de recordatorios acumule pendientes vencidos.",
        ),
        (
            "3.",
            "Renderizar y versionar los diagramas UML faltantes de S12 para que la matriz de "
            "trazabilidad y las figuras cubran el modelo completo.",
        ),
        (
            "4.",
            "Incorporar una prueba de carga con datos representativos que permita verificar el "
            "objetivo de rendimiento y ajustar índices o pool de conexiones con evidencia.",
        ),
        (
            "5.",
            "Definir un entorno de pruebas con datos anonimizados y un procedimiento documentado de "
            "despliegue y reversión, como paso previo a la puesta en operación.",
        ),
        (
            "6.",
            "Revisar la política de respaldos y la rotación de la clave JWT antes del despliegue, y "
            "sustituir los valores por defecto de desarrollo por secretos gestionados.",
        ),
    ):
        add_list_item(document, marker, text)


def build_references(document):
    add_h1(document, "9. Referencias")
    for segments in REFERENCES:
        paragraph = document.add_paragraph()
        paragraph_format = paragraph.paragraph_format
        paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
        paragraph_format.left_indent = Inches(0.5)
        paragraph_format.first_line_indent = Inches(-0.5)
        paragraph_format.space_before = Pt(3)
        paragraph_format.space_after = Pt(6)
        paragraph_format.line_spacing = 1.5
        for text, italic in segments:
            set_run_font(paragraph.add_run(text), italic=italic)


def build_annex(document):
    add_h1(document, "Anexo A. Instrumento de Elicitación de Requisitos")
    add_p(
        document,
        "El siguiente cuestionario se diseñó para relevar los procesos, las reglas y las "
        "expectativas de uso del sistema con la recepción, el área odontológica y la administración "
        "del consultorio. Se organiza por dimensiones para facilitar la conducción de la entrevista y "
        "el análisis posterior. Las preguntas abiertas se complementan con una valoración de "
        "importancia en escala de 1 a 5, donde 1 es irrelevante y 5 es imprescindible. La información "
        "relevada se trata de forma confidencial y se usa exclusivamente para el análisis de "
        "requisitos.",
    )
    add_h2(document, "Datos de la entrevista")
    for text in (
        "Perfil del entrevistado: recepción, odontología o administración.",
        "Antigüedad en el consultorio y tareas que realiza con mayor frecuencia.",
        "Fecha, duración y modalidad de la entrevista.",
    ):
        add_list_item(document, "•", text, after=3)

    for title, questions in QUESTIONNAIRE:
        add_h2(document, title)
        for index, question in enumerate(questions, start=1):
            add_list_item(document, f"{index}.", question, after=3)

    add_h2(document, "Cierre del instrumento")
    add_p(
        document,
        "Al finalizar, el entrevistador resume los puntos acordados y solicita confirmación sobre "
        "tres aspectos: qué proceso duele más en la operación actual, qué información no puede "
        "perderse bajo ninguna circunstancia y qué funcionalidad resultaría indispensable en una "
        "primera versión. Las respuestas se contrastan con las especificaciones S1-S10 y alimentan "
        "la matriz de trazabilidad y la priorización de la tabla 2.",
    )


# ---------------------------------------------------------------------------
# Build and verification
# ---------------------------------------------------------------------------


def build_document() -> Path:
    document = Document()
    configure_page(document.sections[0])
    configure_styles(document)

    properties = document.core_properties
    properties.title = DOCUMENT_TITLE
    properties.subject = DOCUMENT_SUBTITLE
    properties.author = "Proyecto de curso " + COURSE_NAME
    properties.language = "es-ES"
    properties.comments = (
        "Generado por docs/build_documento_clinica.py a partir de docs/FORMATO_DOCUMENTO.md."
    )

    numbering = Numbering()
    build_cover(document)
    build_abstract(document)
    build_introduction(document)
    build_methodology(document, numbering)
    build_requirements_analysis(document, numbering)
    build_functional_requirements(document, numbering)
    build_non_functional_requirements(document, numbering)
    build_traceability(document, numbering)
    build_conclusions(document)
    build_references(document)
    build_annex(document)

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    document.save(str(OUTPUT_PATH))
    return OUTPUT_PATH


def verify_document(path: Path) -> dict:
    """Reopen the generated file with python-docx and print its structure."""
    document = Document(str(path))
    tables = len(document.tables)
    images = len(document.inline_shapes)
    paragraphs = len(document.paragraphs)

    print("")
    print("Verification (python-docx reopen)")
    print(f"  file: {path}")
    print(f"  size: {path.stat().st_size} bytes")
    print(f"  paragraphs: {paragraphs}")
    print(f"  tables: {tables}")
    print(f"  inline images: {images}")
    print(f"  sections: {len(document.sections)}")
    for index, table in enumerate(document.tables, start=1):
        print(
            f"  table {index}: {len(table.rows)} rows x {len(table.columns)} columns "
            f"(header: {table.rows[0].cells[0].text.strip()} ...)"
        )
    for index, shape in enumerate(document.inline_shapes, start=1):
        print(
            f"  image {index}: {shape.width.inches:.2f} x {shape.height.inches:.2f} in"
        )

    if paragraphs == 0 or tables == 0:
        print("WARNING: the document looks incomplete (no paragraphs or no tables).")

    return {
        "paragraphs": paragraphs,
        "tables": tables,
        "images": images,
        "sections": len(document.sections),
    }


def main() -> int:
    print(f"Building {OUTPUT_PATH.name} from {PROJECT_ROOT}")
    matched, missing, unknown = discover_figures(UML_OUT_DIR)
    for spec, _ in matched:
        print(f"  figure found: {spec.key}")
    for stem, _ in unknown:
        print(f"  extra figure: {stem}")
    for key in missing:
        print(f"  figure missing (will be skipped): {key}")

    path = build_document()
    print(f"Document written: {path}")

    summary = verify_document(path)
    print(
        "Summary: "
        f"{summary['paragraphs']} paragraphs, {summary['tables']} tables, "
        f"{summary['images']} images, {summary['sections']} section(s)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
