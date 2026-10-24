# Diagramas UML y regeneración de la entrega

Todo el material gráfico del documento se genera desde fuentes PlantUML versionadas en esta carpeta.
Ningún PNG se edita a mano: si un diagrama cambia, se vuelve a renderizar y se regenera el `.docx`.

## Fuentes

| Archivo | Diagrama | Spec |
|---------|----------|------|
| `01-casos-de-uso.puml` | Casos de uso del sistema | S12 |
| `02-clases.puml` | Clases del backend clínico | S12 |
| `03-entidad-relacion.puml` | Modelo entidad-relación (`schema.sql`) | S12 |
| `04-secuencia-alta-paciente.puml` | Secuencia: alta de paciente | S1, S12 |
| `05-secuencia-programacion-cita.puml` | Secuencia: programación de cita | S4, S12 |
| `06-secuencia-recordatorio.puml` | Secuencia: despacho de recordatorios | S5, S15, S12 |
| `07-secuencia-emision-factura.puml` | Secuencia: emisión de factura | S9, S12 |
| `08-secuencia-pago-parcial.puml` | Secuencia: pago parcial | S10, S12 |
| `09-actividades-atencion.puml` | Actividades: atención integral del paciente | S12 |
| `10-estados-cita.puml` | Estados del ciclo de vida de la cita | S6, S12 |
| `11-estados-factura.puml` | Estados del ciclo de vida de la factura | S10, S12 |
| `12-componentes.puml` | Componentes del sistema | S12 |
| `13-despliegue.puml` | Despliegue (Docker Compose + Nginx) | S12 |

Los PNG renderizados viven en `out/` y son los que consume `docs/build_documento_clinica.py`.

## Cómo renderizar

Requiere Java 21 (ya es requisito del backend). El renderizador es PlantUML en su JAR oficial:

```bash
# una sola vez: descarga del JAR (no se versiona, lo ignora .gitignore)
curl -L -o tools/plantuml.jar \
  https://github.com/plantuml/plantuml/releases/latest/download/plantuml.jar

# render de todas las fuentes a docs/uml/out/
for f in docs/uml/*.puml; do java -jar tools/plantuml.jar -tpng -o out "$f"; done
```

Si preferís Docker, la imagen oficial funciona igual (mismo resultado):

```bash
docker run --rm -v "$PWD/docs/uml:/data" plantuml/plantuml -tpng -o /data/out /data/02-clases.puml
```

En esta máquina el daemon de Docker no estaba corriendo, así que el render documentado y usado fue
`java -jar tools/plantuml.jar` (JAR 1.2025.x). Cualquiera de las dos vías produce PNG equivalentes.

## Regenerar el documento

```bash
python docs/build_documento_clinica.py
```

El script descubre automáticamente los PNG presentes en `out/` y los incrusta como Figuras numeradas
en el orden del catálogo, con su nota de fuente. Si agregás un diagrama, alcanza con respetar el
nombre del catálogo en `docs/build_documento_clinica.py`. El formato APA 7 que aplica está
especificado en `docs/FORMATO_DOCUMENTO.md`.
