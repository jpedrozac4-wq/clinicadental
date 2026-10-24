# Clínica Dental — sistema de gestión odontológica

Aplicación web para operar un consultorio dental: pacientes e historial clínico,
agenda de citas con recordatorios, catálogo de tratamientos, facturación con pagos
parciales, reportes, administración de usuarios con permisos por rol y auditoría de
las operaciones críticas.

- **Backend**: Spring Boot 3.3.4 · Java 21 · PostgreSQL 16 · JWT + RBAC
- **Frontend**: SPA en JavaScript vainilla con módulos ES nativos, **sin build step**
- **Infraestructura**: Docker Compose (PostgreSQL + backend + Nginx)

---

## Arranque rápido

```bash
# 1. Secretos locales (no se versionan)
printf 'CLINICA_ADMIN_PASSWORD=%s\nCLINICA_JWT_SECRET=%s\n' \
  "$(openssl rand -base64 18)" "$(openssl rand -base64 32)" > .env

# 2. Construir y levantar
docker compose up -d --build

# 3. Verificar
curl -s -o /dev/null -w '%{http_code}\n' http://localhost/
```

Abrir <http://localhost> e ingresar con el usuario administrador definido en
`.env` (`CLINICA_ADMIN_USERNAME`, por defecto `admin`).

La base arranca **sin datos de negocio**: sólo catálogos, los tres roles base con
su matriz de permisos y el catálogo de tratamientos de odontología general. No hay
pacientes, citas, facturas, pagos ni usuarios además del administrador inicial.

**Primeros pasos**: *Usuarios y roles* → dar de alta los odontólogos y el personal
de recepción. Sin al menos un odontólogo activo la agenda no puede programar citas.

| Servicio | Puerto |
|---|---|
| Frontend (Nginx) | 80 |
| Backend (Spring Boot) | 8085 → 8080 |
| PostgreSQL | 5432 |

---

## Módulos

| Grupo | Módulo |
|---|---|
| General | Panel general |
| Atención clínica | Pacientes · Agenda de citas · Catálogo de tratamientos |
| Administración | Facturación y pagos · Recordatorios · Reportes · Usuarios y roles · Auditoría |
| Mi cuenta | Mi cuenta |

El sidebar se arma por permisos: cada ítem aparece sólo si el perfil autenticado
puede usar la ruta, y la navegación por hash respeta la misma guarda.

---

## Verificación

```bash
# Backend: suite de pruebas
cd backend && mvn -B test

# Frontend: preflight estático de selectores (no abre Chrome)
node tools/browser-smoke.mjs --check-selectors

# Escenario end-to-end sobre el stack levantado (23 pasos, base limpia)
SMOKE_ADMIN_PASSWORD='<la del .env>' node tools/browser-smoke.mjs
```

El escenario de humo recorre el flujo completo desde una base recreada: login del
administrador → alta de odontólogo y recepción → paciente → cita → reprogramación →
cancelación → tratamiento realizado → factura → pago parcial → pago total → PDF →
auditoría → reportes → recordatorios, y cierra con la baja lógica del paciente.
Deja capturas y `report.json` en `tools/artifacts/` (no versionado).

La contraseña del administrador **no** está en el repositorio: se pasa por entorno.

---

## Documentación

| Documento | Contenido |
|---|---|
| [`docs/DESPLIEGUE.md`](docs/DESPLIEGUE.md) | Variables de entorno, puesta en marcha, volver a un estado limpio, datos de demostración opcionales |
| [`docs/Analisis Clinica Dental.docx`](docs/Analisis%20Clinica%20Dental.docx) | Documento de análisis del sistema (requerimientos, casos de uso, diagramas) |
| [`docs/uml/`](docs/uml/) | Fuentes PlantUML de los 13 diagramas y sus renders en `docs/uml/out/` |
| [`docs/FORMATO_DOCUMENTO.md`](docs/FORMATO_DOCUMENTO.md) | Contrato de formato del documento (APA 7) |
| [`frontend/README.md`](frontend/README.md) | Arquitectura de la SPA, cómo agregar un módulo, contrato de DOM y storage |

Para regenerar el documento después de tocar diagramas o infraestructura:

```bash
python docs/build_documento_clinica.py
```

---

## Estructura

```
backend/            API REST (Spring Boot): pacientes, citas, tratamientos,
                    facturación, pagos, recordatorios, usuarios, auditoría, reportes
frontend/           SPA vainilla: shell, router por permisos y un módulo por ruta
docs/               Documento de análisis, diagramas UML y guía de despliegue
tools/              Arnés de pruebas de navegador (CDP puro, sin dependencias)
schema.sql          DDL completo de la base
bootstrap_produccion.sql  Catálogos, roles, permisos y tratamientos base
seed_data.sql       Base de demostración (no se carga automáticamente)
docker-compose.yml  PostgreSQL + backend + Nginx
nginx.conf          SPA y proxy /api/ hacia el backend
```

---

## Notas de seguridad

- El `.env` con secretos no se versiona; `.gitignore` lo excluye.
- El backend no arranca sin `CLINICA_ADMIN_PASSWORD`: prefiere un fallo explícito
  antes que crear un administrador con una contraseña conocida.
- Cambiar `CLINICA_ADMIN_PASSWORD` después del primer arranque no cambia la
  contraseña del administrador existente: eso se hace desde *Usuarios y roles*.
- Toda operación crítica se audita en la misma transacción que la operación de
  negocio: si la escritura de auditoría falla, la operación se revierte.
