# IMCD Operations Tool

Web application for IMCD warehouse operations. It imports carrier delivery files and compares those delivery dates with IMCD promised and earliest viable dates.

The app is a Spring Boot WAR served at `/operationstool`. Pages live in `src/main/webapp`. APIs and security live under `com.IMCDOperationsTool`. Excel import and export come from `imcd-platform-lib`.

## Features

- **Carrier import** (`import.html`). Upload an Excel file for a branch plant. The first row must be the header, and columns must appear in this order: Carrier Code, Pick Number, Status, SKU, Delivery Date, Client, Address, Locality, Postal Code, Country.
- **Transport Performance Dashboard** (`dashboard.html`). Filter deliveries by promised-date range, account owner, carrier zone, result, pick, order, product, and account. The page shows on-time and late KPIs, a monthly trend, and a paginated delivery list. Results can be exported to Excel.
- **Pick details** (`/pick/{pick}/details`). Opens the pick header, order lines, carrier rows, EDI records, and related cases.

Matching, dates, and KPIs are calculated in SQL Server by `dbo.TransportPerformance_Consult` and the related consult procedures.

## Stack

- Java 25
- Spring Boot 3.5.11 (web, security, JDBC)
- Microsoft SQL Server (`OPERATIONSTOOL`)
- JWT cookies for local login; container user or JWT in production
- AWS Secrets Manager for production database credentials
- `imcd-platform-lib` 1.2.0 for Excel import and export

## Prerequisites

- JDK 25
- Maven 3.9+
- Access to the `OPERATIONSTOOL` SQL Server database, plus `COMMON_APPS` and `MOSAICTOOL` for local login
- The `com.imcd:imcd-platform-lib:1.2.0` artifact in the local or corporate Maven repository

## Profiles

Activate exactly one Maven profile. It sets `spring.profiles.include` through `@profileActive@` in `application.properties`.

| Profile | Use | Database credentials | Sign-in |
| --- | --- | --- | --- |
| `loc` | Local development | JDBC URL and `ENC(...)` username/password in `application-loc.properties` | `login.html` posts to `POST /login_dev` |
| `pro` | Deployed WAR | AWS Secrets Manager secret `pro/JavaApps` in `eu-central-1` | JWT cookie or the servlet container user. Unauthenticated browsers go to Common Apps |

`use.aws.secrets` defaults to `true`. The local profile sets it to `false`.

## Run locally

1. Set the key that decrypts `ENC(...)` values in `application-loc.properties`. The app looks for it in this order:
   - property `security.enc.master-key`
   - environment variable `MOSAIC_MASTER_KEY`
   - system property `mosaic.master.key`

2. Start the app:

```bash
mvn spring-boot:run -Ploc
```

3. Open [http://localhost:8084/operationstool/login.html](http://localhost:8084/operationstool/login.html).

Local login checks the password against `MOSAICTOOL.dbo.ConfigurationMosaicTool` (`CFG_KEY_LOCAL`) and the user against an active row in `COMMON_APPS.dbo.UsersCommonApps` (`UCA_LocalADUser`). A successful login sets an HTTP-only `jwt` cookie (8 hours) and stores the user in `localStorage` under `operationstoolusuario`.

`GET /ping_dev` returns `OK_DB` when the local profile can reach SQL Server, and `ERROR_DB` when it cannot. Both `/login_dev` and `/ping_dev` exist only on the `loc` profile.

## Build

```bash
mvn clean package -Ploc
mvn clean package -Ppro
```

Both profiles produce `target/IMCDOperationsTool.war`. Tomcat is `provided`, so production runs the WAR on an external servlet container. The context path is `/operationstool` and the configured port is `8084`.

## Configuration

Shared settings are in `src/main/resources/application.properties`:

- `server.servlet.context-path=/operationstool`
- multipart uploads up to 200 MB
- `commonsapp.url`, used when a production request has no valid session

Do not commit real secrets. Keep database passwords as `ENC(...)` values, and keep the master key and `jwt.secretKey` outside source control when you rotate them.

Uploaded workbooks are copied to `CFG_DirectoryImport` in `dbo.ConfigurationOperationsTool` (`/OT10_DOCS/IMPORTS`).

## Pages

| Path | Page |
| --- | --- |
| `/login.html` | Local login (loc profile only) |
| `/import.html` | Carrier import |
| `/dashboard.html` | Transport Performance Dashboard |
| `/pick/{pick}/details` | Pick details |

Static files under `/js`, `/styles`, `/imgs`, and `/components` are public. Every other request requires a valid JWT or a container user.

## API

Base path: `/operationstool`.

**Import**

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/import-reports/branchPlants` | Branch plants for the import form |
| `POST` | `/import-reports/carrier` | Stage the workbook and load carrier rows (`file`, `brpId`, `localAdUser`, `mail`, `userName`) |
| `GET` | `/import-reports/getImportReportsCombo` | Import report catalog |
| `POST` | `/import-reports/import` | Generic workbook import (`file`, `reportId`, `userLogged`) |
| `GET` | `/import-reports/alertMessage` | Alert copy from configuration |

**Transport performance**

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/transport-performance/consult` | KPIs, monthly trend, and delivery rows |
| `GET` | `/transport-performance/export` | Excel export of the current filter |
| `GET` | `/transport-performance/unmatched` | Carrier rows that did not match a pick |
| `GET` | `/transport-performance/picks/{pick}` | Pick header, lines, carrier, EDI, and cases |
| `GET` | `/transport-performance/account-owners` | Account owner list |
| `GET` | `/transport-performance/zones` | Carrier zone list |
| `GET` | `/transport-performance/products?term=` | Product search (at least 2 characters) |
| `GET` | `/transport-performance/accounts?term=` | Account search (at least 2 characters) |

## Database

The application calls stored procedures in `OPERATIONSTOOL`. The main ones are:

- `dbo.TransportPerformance_Consult`
- `dbo.TransportPerformance_Unmatched_Consult`
- `dbo.TransportPerformance_Pick_Consult`
- `dbo.TransportPerformance_AccountOwners_Consult`
- `dbo.TransportPerformance_Zones_Consult`
- `dbo.TransportPerformance_Product_Search`
- `dbo.TransportPerformance_Account_Search`
- `dbo.CarrierImport_Report_Consult`
- `dbo.Carrier_Imported_Data_Load`
- `dbo.Import_Reports_Consult`
- `dbo.BranchPlants_Consult`
- `dbo.ConfigurationOperationsTool` (`CFG_DirectoryImport` for the saved workbook)
- `dbo.Configuration_AlertMessage_Consult`

## Project layout

```
src/main/java/com/IMCDOperationsTool
  config/          security filter, data source
  controllers/     import, dashboard, pick page, local login
  security/        profile check and ENC() decryption
  services/        file storage
  utils/           JWT and AWS credentials
src/main/resources
  application.properties
  application-loc.properties
  application-pro.properties
src/main/webapp
  import.html, dashboard.html, pick.html, login.html
  js/              page scripts and shared shell
  styles/          IMCD styles
  components/      header, menu, footer
```
