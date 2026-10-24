# Despliegue y puesta en marcha

Guía de operación para levantar el sistema de clínica dental desde cero y para
volver a un estado limpio. Complementa a `docker-compose.yml` y `schema.sql`.

---

## 1. Qué levanta el stack

| Servicio | Imagen | Puerto | Rol |
|----------|--------|--------|-----|
| `database` | `postgres:16-alpine` | 5432 | Base transaccional. Se inicializa desde `schema.sql` + `bootstrap_produccion.sql`. |
| `backend` | build local (`./backend`) | 8085 → 8080 | API REST en Spring Boot. Crea el administrador inicial si no existe. |
| `frontend` | `nginx:alpine` | 80 | SPA clínica y proxy `/api/` hacia el backend. |

Volumen nombrado: `clinica_dental_pgdata`. Red: `clinica_dental_net`.

---

## 2. Variables de entorno

`docker compose` lee un archivo `.env` en la raíz del proyecto. Ese archivo **no
se versiona** (está en `.gitignore`): contiene secretos. Crearlo a mano con estas
claves.

### Administrador inicial (obligatorio la primera vez)

| Variable | Default en compose | Descripción |
|----------|--------------------|-------------|
| `CLINICA_ADMIN_USERNAME` | `admin` | Usuario administrador. |
| `CLINICA_ADMIN_PASSWORD` | *(vacío)* | **Obligatorio.** Sin este valor el backend no arranca: falla con un mensaje explícito en lugar de crear un administrador con contraseña conocida. |
| `CLINICA_ADMIN_EMAIL` | `admin@clinicaldental.com` | Correo del administrador. |
| `CLINICA_ADMIN_FULLNAME` | `Administrador de la Clinica` | Nombre visible. |

El usuario se crea **solo si no existe ningún administrador activo**. Cambiar la
variable después del primer arranque no cambia la contraseña: eso se hace desde
la pantalla *Usuarios y roles*.

### Seguridad de la sesión

| Variable | Default | Descripción |
|----------|---------|-------------|
| `CLINICA_JWT_SECRET` | secreto de desarrollo | Firma del JWT. En producción, generar uno propio de 32 bytes o más. |
| `CLINICA_JWT_EXPIRATION_SECONDS` | `28800` | Vigencia del token (8 h). |

### Recordatorios por correo (opcional)

| Variable | Descripción |
|----------|-------------|
| `CLINICA_SMTP_HOST`, `CLINICA_SMTP_PORT` | Servidor SMTP. |
| `CLINICA_SMTP_USERNAME`, `CLINICA_SMTP_PASSWORD` | Credenciales SMTP. |
| `CLINICA_SMTP_FROM` | Remitente de los avisos. |

Sin SMTP configurado el sistema **no falla**: cada envío queda registrado como
`SIMULADA` en la bitácora de recordatorios.

---

## 3. Primera puesta en marcha

```bash
# 1. Crear el archivo de secretos (no se versiona)
#    Completar CLINICA_ADMIN_PASSWORD y CLINICA_JWT_SECRET.
printf 'CLINICA_ADMIN_PASSWORD=%s\nCLINICA_JWT_SECRET=%s\n' "$(openssl rand -base64 18)" "$(openssl rand -base64 32)" > .env

# 2. Construir y levantar
docker compose up -d --build

# 3. Verificar
docker compose ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost/
```

Entrar en <http://localhost> con el usuario y la contraseña definidos arriba.

La base arranca **sin datos de negocio**: solo catálogos, los tres roles base con
su matriz de permisos y ocho tratamientos de odontología general con precio de
referencia. No hay pacientes, citas, facturas ni pagos.

### Primeros pasos en la aplicación

1. **Usuarios y roles** → dar de alta los odontólogos (`rol = Odontólogo`). Sin
   al menos un odontólogo activo la agenda no puede programar citas: el
   formulario lo informa y enlaza a esta pantalla.
2. Dar de alta al personal de recepción.
3. Revisar la matriz de permisos de cada rol si la clínica necesita otro reparto.
4. Ajustar los precios del catálogo de tratamientos.

---

## 4. Volver a un estado limpio

Destruye **todos** los datos: pacientes, citas, historiales, facturas, pagos,
recordatorios y usuarios. El `schema.sql` y el bootstrap se reaplican solos.

```bash
docker compose down
docker volume rm clinica_dental_pgdata
docker compose up -d
```

El administrador inicial se vuelve a crear desde las variables de entorno.

---

## 5. Datos de demostración (opcional)

`seed_data.sql` **no** se monta en el init automático. Contiene una base poblada
(6 pacientes, 10 citas en distintos estados, 4 facturas con pagos parciales y
totales, recordatorios y un catálogo ampliado de tratamientos) útil para demos y
para mostrar la interfaz con movimiento.

El smoke de navegador **no** lo necesita: desde S24 el escenario arranca de una
base limpia y crea sus propios datos (ver la sección 7).

Se aplica a mano, sobre una base ya inicializada:

```bash
docker compose exec -T database psql -U postgres -d clinica_dental < seed_data.sql
```

No ejecutarlo sobre una base con datos reales: inserta pacientes y comprobantes
de ejemplo.

---

## 6. Scripts de inicialización

| Archivo | Cuándo se ejecuta | Contenido |
|---------|-------------------|-----------|
| `schema.sql` | Al crear el volumen | 20 tablas, catálogos, restricciones e índices. |
| `bootstrap_produccion.sql` | Al crear el volumen, después del schema | Catálogos, 3 roles, catálogo de permisos, matrices y 8 tratamientos. Cero datos de negocio. |
| `seed_data.sql` | Nunca automáticamente | Base de demostración completa. |

Los dos primeros se montan en `docker-entrypoint-initdb.d`; solo corren cuando el
volumen está vacío.

---

## 7. Smoke de navegador (verificación del flujo desde cero)

`tools/browser-smoke.mjs` es un arnés de CDP sin dependencias (no usa Playwright
ni Puppeteer): abre un Chrome headless, recorre la aplicación como lo haría el
personal de la clínica y deja `report.json` con un paso por pantalla, captura de
pantalla y hallazgos de consola/red.

Qué verifica (S24): login del administrador bootstrap → alta de un odontólogo y
una recepcionista desde *Usuarios y roles* → matriz de permisos de un rol base →
agenda con el odontólogo recién creado → alta y **edición** de paciente →
alta/edición de un tratamiento y su 409 de código duplicado → cita A →
**reprogramación** de la cita A → cita B creada y **cancelada** → tratamiento
realizado → comprobante → pago total → PDF → auditoría → reportes →
recordatorios → baja lógica del paciente. **No necesita `seed_data.sql`**: la base
tiene que estar limpia y el escenario crea todo lo que necesita con
identificadores únicos por corrida.

La reprogramación y la cancelación usan **dos citas distintas** a propósito: la
cita A se reprograma y su cita nueva queda vigente (la usan el tratamiento
realizado y los reportes), y la cancelación se ejerce sobre una cita B creada
para eso. Consecuencia en la bitácora de recordatorios (S5/S6): reprogramar
anula el pendiente de la cita reemplazada y programa el de la nueva, cancelar
anula el de la cita cancelada, así que la corrida deja **1 recordatorio
`PENDIENTE` (cita reprogramada) y 2 `CANCELADA` (cita original y cita B)**, que
es exactamente lo que verifica el paso de recordatorios junto con el filtro
`CANCELADA`.

```bash
# 1. Verificación estática de los selectores (no necesita backend ni Chrome)
node tools/browser-smoke.mjs --check-selectors

# 2. Escenario completo contra el stack levantado
docker compose up -d
node tools/browser-smoke.mjs
#    report.json + capturas + PDF en tools/artifacts/
```

El preflight compara cada selector del escenario contra el archivo del frontend
que lo define: si una pantalla cambia un id, la corrida falla antes de abrir
Chrome en lugar de pasar en falso.

### Variables de entorno del smoke

| Variable | Default | Descripción |
|----------|---------|-------------|
| `SMOKE_ADMIN_USER` | `admin` | Usuario administrador con el que arranca el escenario. Debe coincidir con `CLINICA_ADMIN_USERNAME`. |
| `SMOKE_ADMIN_PASSWORD` | *(sin default, obligatorio)* | Contraseña del administrador. Debe coincidir con `CLINICA_ADMIN_PASSWORD`. No tiene valor por omisión a propósito: una contraseña versionada es una contraseña publicada, así que el arnés corta antes de lanzar Chrome si falta. |
| `BASE_URL` | `http://localhost` | Origen del frontend. |
| `CHROME_BIN` | Chrome instalado | Ruta absoluta al binario de Chrome. |
| `CDP_PORT` | `9333` | Puerto de depuración remota del Chrome que lanza el arnés. |
| `OUT_DIR` | `tools/artifacts` | Carpeta de capturas, PDF y `report.json`. |

```bash
SMOKE_ADMIN_USER=admin SMOKE_ADMIN_PASSWORD='<la del .env>' node tools/browser-smoke.mjs
```

### Qué deja en la base

El escenario es repetible y se limpia solo: el usuario descartable y el paciente
se dan de baja lógica, y cada corrida usa un identificador nuevo (`STAMP`) para
usuarios, correo, teléfono, código de tratamiento y referencias de pago. Los
usuarios de personal, el tratamiento, la cita, el realizado, el comprobante, el
pago y el recordatorio quedan registrados a propósito: la API no expone borrado
para ellos (S2 y S6 usan baja lógica) y forman el historial clínico y fiscal. El
detalle queda en `report.cleanup` de `report.json`.
