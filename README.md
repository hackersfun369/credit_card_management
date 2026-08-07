# Credence — Credit Card Management System

Credence is a role-based credit-card operations platform built with Angular, Spring Boot microservices, Oracle Database, JWT security, and a confirmation-gated AI assistant. It provides isolated manager and customer portals for customers, cards, requests, merchants, transactions, repayments, renewals, alerts, and analytics.

> Never commit .env, database passwords, JWT secrets, or AI keys. Use .env.example.

## Contents

- [Features](#features)
- [Architecture](#architecture)
- [Roles and ownership](#roles-and-ownership)
- [Business rules](#business-rules)
- [Workflows](#workflows)
- [Data model](#data-model)
- [Data handling](#data-handling)
- [Services and APIs](#services-and-apis)
- [Frontend routes](#frontend-routes)
- [AI assistant](#ai-assistant)
- [Validation and security](#validation-and-security)
- [Setup and run](#setup-and-run)
- [Testing](#testing)
- [Structure](#structure)
- [Troubleshooting](#troubleshooting)

## Features

### Manager portal

- Register/login as a manager and automatically refresh JWT access.
- Portfolio dashboard: owned customers, card status, outstanding balance, purchases, transaction curve, pending requests, attention items, and recent transactions.
- Create, search, edit, inspect, paginate, and delete assigned customers.
- Allocate/update/disable customer login credentials.
- Issue, inspect, search, edit, block, activate, deactivate, renew, and delete cards.
- Review cards by status and attention.
- Approve, reject, or hold customer card requests.
- Create, inspect, search, update status, and delete merchants.
- Create, filter, inspect, settle, cancel, fail, repay, and delete transactions.
- Ask live questions and run explicitly confirmed operations through Credence AI.

### Customer portal

- Login only with manager-assigned credentials; no customer self-registration.
- Personal dashboard, cards, merchants, transactions, requests, analytics, and attention.
- Block an owned card immediately.
- Renew an owned card only after physical-card expiry.
- Request PRIMARY/ADD_ON when fewer than two current or pending cards exist.
- Track PENDING, ON_HOLD, APPROVED, and REJECTED requests.
- Make transactions with an eligible card and active merchant.

### UI

- Responsive standalone Angular components and role-aware navigation.
- Modal create/edit/delete/confirmation flows; no browser alerts.
- Inline concise errors without stack traces.
- Search, independent filters, amount range, and pagination (10 rows per page).
- INR formatting and Indian date/time.
- Smooth curved responsive SVG charts.
- Tier-specific card colors; sensitive card-number masking.
- Draggable, streaming AI chat with named/deletable history.

## Technology

| Layer | Technology |
|---|---|
| Frontend | Angular 22, TypeScript 6, RxJS, Angular Router |
| Backend | Java 17, Spring Boot, Spring Web, Spring Data JPA |
| Auth | JWT access/refresh tokens, BCrypt |
| Discovery | Netflix Eureka |
| Data | Oracle FreePDB1 |
| AI | OpenAI-compatible Cline API, SSE |
| Build | npm/Angular CLI, Maven |

## Architecture

~~~mermaid
flowchart LR
  subgraph Web[Angular :4200]
    UI[Manager + customer portals]
    JWT[Session / refresh]
    AI[Credence AI UI]
  end
  subgraph API[Spring Boot]
    M[Manager/Auth/Portals/AI :8085]
    C[Customer :8081]
    K[CreditCards :8082]
    T[Transactions :8083]
    R[Merchant :8084]
    E[Eureka :4596]
  end
  DB[(Oracle FreePDB1)]
  P[AI provider]

  UI --> M
  UI --> C
  UI --> K
  UI --> T
  UI --> R
  JWT --> M
  AI -->|SSE + confirmed actions| M
  M --> C
  M --> K
  M --> T
  M --> R
  T -->|credit validation/update| K
  T -->|merchant validation| R
  C -->|deactivate before deletion| K
  C --> DB
  K --> DB
  T --> DB
  R --> DB
  M --> DB
  M --> P
  M -.-> E
  C -.-> E
  K -.-> E
  T -.-> E
  R -.-> E
~~~

| App | Port | Responsibility |
|---|---:|---|
| RegistryServer | 4596 | Eureka registry |
| Customer | 8081 | Records, unique/account generation, deletion orchestration |
| CreditCards | 8082 | Issue, tier limit, shared balance/dates, status, renewal |
| Transactions | 8083 | Lifecycle, credit reservations, repayment, history |
| Merchant | 8084 | Records, generated MID/account, status/category |
| Manager | 8085 | Auth, ownership, portals, requests, AI |
| Angular | 4200 | Manager/customer UI |

## Roles and ownership

| Capability | Manager | Customer |
|---|:---:|:---:|
| Self-register | Yes | No |
| Create customer/login credentials | Yes | No |
| View customers | Assigned only | Self only |
| Issue card directly | Yes | No |
| Request card | Not needed | Yes |
| Decide request | Assigned requests | No |
| Block/renew | Yes | Own card |
| Create transaction | Yes | Own eligible card |
| Manage merchants | Yes | View/use active |
| Manager AI | Yes | No |

MANAGED_CUSTOMER assigns every customer to one manager username. Manager dashboards, customers, cards, transactions, and requests are filtered through those owned customer IDs. CardRequest also stores managerUsername. Backend authorization—not UI hiding—prevents cross-manager and cross-customer access.

## Business rules

### Customers

- Manager-only creation; IDs are generated.
- Phone is exactly 10 digits and unique.
- Aadhaar is exactly 12 digits and unique.
- Account number is a generated unique 12-digit value.
- createdDate is automatic UTC ISO; missing legacy dates are backfilled.
- Customer deletion first changes every associated card to INACTIVE. Failure to deactivate cancels deletion.
- Portal credentials are separate records; passwords are BCrypt hashes.

### Shared card account

A customer has at most one PRIMARY and one ADD_ON current physical card. Together they are one credit account and must share tier, limit, available credit, due date, and expiry date. A transaction through either card updates both. Duplicate types are rejected. A lower tier is rejected when its limit is below current outstanding.

| Tier | Limit |
|---|---:|
| SILVER | ₹50,000 |
| GOLD | ₹1,50,000 |
| PLATINUM | ₹3,00,000 |
| ULTRA_PREMIUM | ₹5,00,000 |

Card numbers are generated, unique 12-digit values. Status is ACTIVE, INACTIVE, or BLOCKED. Only active, unexpired, unreplaced cards transact. Expiry is terminal: expired physical cards cannot be active again. Due date must be before expiry; monthly due advancement uses calendar arithmetic (including February).

### Renewal

Renewal creates new physical cards while preserving history:

1. Validate real new dates and due-before-expiry.
2. Load the current PRIMARY/ADD_ON pair.
3. Create a replacement for each with a new card number.
4. Carry customer, type, tier, shared limit and available balance.
5. Mark old cards INACTIVE.
6. Link old/new through replacedByCreditId and replacementOfCreditId.
7. Keep historical transactions on original card numbers.
8. Route future balances, repayments, dues, and alerts to replacements.

~~~mermaid
flowchart LR
  Old[Expired card] --> Valid{Dates valid?}
  Valid -- No --> Reject[Reject]
  Valid -- Yes --> Pair[Current card pair]
  Pair --> New[Create replacements]
  New --> Link[Link old/new IDs]
  Link --> Retire[Old INACTIVE]
  Link --> Active[New ACTIVE + shared balance]
~~~

### Transactions

- Positive amount; eligible card; active merchant for non-PAYMENT.
- PURCHASE and AUTHORIZATION are debit types.
- New debit starts PENDING or AUTHORIZED.
- Available credit is reserved at creation, so pending items cannot later overrun the limit.
- COMPLETED retains the reservation without double charge.
- FAILED/CANCELLED release it.
- COMPLETED/FAILED/CANCELLED are terminal.
- Transaction creation uses Asia/Kolkata.

~~~mermaid
stateDiagram-v2
  [*] --> PENDING: create/reserve
  [*] --> AUTHORIZED: create/reserve
  PENDING --> AUTHORIZED
  PENDING --> COMPLETED: settle
  AUTHORIZED --> COMPLETED: settle
  PENDING --> FAILED: release
  AUTHORIZED --> FAILED: release
  PENDING --> CANCELLED: release
  AUTHORIZED --> CANCELLED: release
  COMPLETED --> [*]
  FAILED --> [*]
  CANCELLED --> [*]
~~~

### Repayment

PAYMENT is shown as Card repayment/Debt repayment with merchant Self. It cannot exceed outstanding (limit minus available) and is refused if no balance exists. Valid repayment completes immediately, restores shared availability, updates both current cards, advances due date, and stays in history.

### Attention

Current cards appear for blocked/inactive state, expiry/expiring soon, overdue/due soon, or low credit. Warnings use last six digits. Replaced historical cards do not keep producing current-account warnings.

## Workflows

### Login and automatic refresh

~~~mermaid
sequenceDiagram
  actor User
  participant UI as Angular
  participant S as Session
  participant A as Auth :8085
  participant G as Guard

  User->>UI: Login with role
  UI->>A: POST /api/auth/login
  A-->>S: Access + refresh tokens
  S->>S: Schedule refresh before expiry
  User->>G: Protected route
  G->>S: ensureAccessToken
  alt valid
    S-->>G: allow
  else expiring/401
    S->>A: POST /api/auth/refresh
    A-->>S: rotated tokens
    S-->>G: allow/retry once
  else refresh rejected
    S->>UI: clear session + save return URL
    UI-->>User: login, then return
  end
~~~

### Card request

~~~mermaid
sequenceDiagram
  actor Customer
  participant CP as Customer portal
  participant G as Manager service
  actor Manager
  participant MP as Manager portal
  participant Card as Card service

  Customer->>CP: Request type/tier
  CP->>G: POST customer-portal/requests
  G->>G: Validate ownership/count/type/tier
  G-->>CP: PENDING
  G-->>MP: Dashboard + /card-requests
  alt Hold
    Manager->>G: ON_HOLD
  else Reject
    Manager->>G: REJECTED
  else Approve
    Manager->>G: APPROVED + dates
    G->>Card: Issue card
    Card-->>G: Generated card
  end
  G-->>CP: Current request status
~~~

### Purchase

~~~mermaid
flowchart TD
  Start[Card + merchant + amount + type/method] --> Card{Eligible card?}
  Card -- No --> Reject1[Reject]
  Card -- Yes --> Merchant{Active merchant?}
  Merchant -- No --> Reject2[Reject]
  Merchant -- Yes --> Amount{Positive and within availability?}
  Amount -- No --> Reject3[Inline error]
  Amount -- Yes --> Reserve[Reserve shared credit]
  Reserve --> Pending[Save PENDING/AUTHORIZED]
  Pending --> Settle{Decision}
  Settle -- Complete --> Done[COMPLETED]
  Settle -- Fail/cancel --> Release[Release credit]
~~~

## Data model

~~~mermaid
erDiagram
  MANAGER ||--o{ MANAGED_CUSTOMER : owns
  CUSTOMER ||--o| MANAGED_CUSTOMER : assigned
  CUSTOMER ||--o| CUSTOMER_ACCOUNT : authenticates
  CUSTOMER ||--o{ CREDIT_CARD : holds
  CUSTOMER ||--o{ CARD_REQUEST : submits
  MANAGER ||--o{ CARD_REQUEST : reviews
  CREDIT_CARD ||--o{ TRANSACTION : records
  MERCHANT ||--o{ TRANSACTION : receives
  CREDIT_CARD o|--o| CREDIT_CARD : replaces
  ASSISTANT_CONVERSATION ||--o{ ASSISTANT_MESSAGE : contains

  CUSTOMER {
    int custId PK
    long phoneNumber UK
    long aadharNumber UK
    long accountNumber UK
    datetime createdDate
  }
  CUSTOMER_ACCOUNT {
    int customerId UK
    string username UK
    string passwordHash
    boolean active
  }
  CREDIT_CARD {
    int creditId PK
    int customerId FK
    long cardNumber UK
    string cardName
    string cardType
    double cardLimit
    double availableCredit
    date dueDate
    date expiryDate
    string status
    int replacementOfCreditId
    int replacedByCreditId
  }
  CARD_REQUEST {
    long id PK
    int customerId FK
    string managerUsername
    string cardName
    string cardType
    string status
  }
  MERCHANT {
    long merchantId PK
    string mid UK
    long merchantAccountNmber UK
    string merchantCategory
    string status
  }
  TRANSACTION {
    long transactionId PK
    long cardNumber
    long merchantId
    decimal amount
    string transactionType
    string status
    string paymentMethod
    datetime timestamp
    boolean creditReserved
  }
~~~

> merchantAccountNmber is the current backend spelling and must be used exactly in its JSON schema.

## Data handling

### Generated fields

| Value | Owner | Behavior |
|---|---|---|
| Customer/credit/merchant/transaction ID | Oracle/JPA | Immutable |
| Customer account | Customer | Unique 12 digits |
| Card number | CreditCards | Unique 12 digits |
| Card limit | CreditCards | Tier-derived |
| MID | Merchant | Unique 15 digits |
| Merchant account | Merchant | Unique 12 digits |
| Timestamps | Owning service | Server generated |

Angular contains no hard-coded domain records. Create forms and AI omit generated values; authoritative values come back from services.

PRIMARY/ADD_ON state is synchronized in CreditCards. The assistant resolves customer by ID/account/Aadhaar/phone/unique name; card by credit ID/card number; merchant by ID/MID/account/unique name; transaction by ID/reference. Ambiguity fails instead of guessing.

Successful mutations refresh affected views. AI confirmation is replaced with the actual result. Errors render only the backend message inside the current dialog; no raw JSON/trace. Temporary success messages dismiss after about three seconds.

## Services and APIs

### Customer :8081

| Method | Endpoint | Purpose |
|---|---|---|
| GET/POST | /customer | List/create |
| GET | /customers/{page}/{size} | Paginate |
| GET | /customer/{id} | Detail |
| PUT/PATCH | /putCustomer/{id}, /patchCustomer/{id} | Update |
| DELETE | /customer/{id} | Deactivate cards/delete |
| GET | /customers/group/month, /week, /year, /{type} | Analytics |

### CreditCards :8082

| Method | Endpoint | Purpose |
|---|---|---|
| GET/POST | /card | List/issue |
| GET | /cards/{page}/{size}, /card/{id} | Paginate/detail |
| PUT/PATCH | /putCard/{id}, /patchCard/{id} | Update/status |
| POST | /cards/{id}/renew | Replace pair |
| DELETE | /card/{id} | Delete |
| GET | /cards/customer/{customerId} | Customer cards |
| GET | /cards/group/month, /week, /year, /{type} | Analytics |
| PUT | /cards/customer/{id}/deactivate | Deactivate all |
| PUT | /card/number/{number}/advance-due-date | Advance due |
| PUT | /card/number/{number}/available-credit?delta=... | Shared credit |

### Transactions :8083

| Method | Endpoint | Purpose |
|---|---|---|
| POST/GET | /transaction, /transactions | Create/list |
| GET | /transactions/{page}/{size} | Paginate |
| GET | /transactions/card/{number} | Card history |
| GET | /transactions/merchant/{id} | Merchant history |
| GET | /transactions/date, /transactions/merchant/{id}/date | Date queries |
| GET | /search, /transactions/group/{type} | Search/analytics |
| PUT | /transaction/{id}/status | Settle/status |
| DELETE | /transaction/{id} | Delete/release |

### Merchant :8084

| Method | Endpoint | Purpose |
|---|---|---|
| GET | /merchants | List |
| POST | /merchant | Create/generated MID/account |
| PUT/PATCH/DELETE | /merchant/{id} | Update/status/delete |

### Manager/Auth :8085

| Area | Operations |
|---|---|
| /api/auth | register, login, refresh, create/get/update customer credentials |
| /api/manager-portal | owned dashboard, create/assign customer, decide request |
| /api/customer-portal | dashboard, cards, transactions, merchants, requests, block, renew |
| /api/assistant | conversations, history, SSE/non-SSE chat, confirmed execution, result persistence |

Important exact endpoints include:

- POST /api/auth/register, /login, /refresh
- POST/GET/PUT /api/auth/customers/{id}/credentials
- GET /api/manager-portal/dashboard
- POST /api/manager-portal/customers
- PATCH /api/manager-portal/card-requests/{id}
- GET /api/customer-portal/dashboard, /cards, /transactions, /merchants, /requests
- POST /api/customer-portal/transactions, /requests, /cards/{id}/block, /cards/{id}/renew
- GET /api/assistant/conversations and /history
- DELETE /api/assistant/conversations/{conversationId}
- POST /api/assistant/chat, /chat/stream, /actions/execute
- POST /api/assistant/conversations/{conversationId}/messages/{messageId}/action-result

## Frontend routes

### Manager

| Route | Page |
|---|---|
| / | Portfolio dashboard and creation dialogs |
| /cards | Cards/search/status/issue |
| /cards/attention | Needs attention |
| /cards/status/:status | Status list |
| /cards/:id | Card/account analytics and operations |
| /customers | Customers/register |
| /customers/:id | Profile, credentials, cards, edit/delete |
| /card-requests | Owned requests and decisions |
| /merchants | Merchants/add/delete |
| /merchants/:id | Status, cards, filtered history |
| /transactions | Search/type/status/method/range/create |
| /transactions/:filter/:value | Filtered list |
| /transactions/:id | Detail/status/links/masking |

### Customer

| Route | Page |
|---|---|
| /my-account | Dashboard |
| /my-account/cards, /cards/:id | Own cards/details |
| /my-account/merchants, /merchants/:id | Associated merchants |
| /my-account/transactions, /transactions/:id | Own history/detail |
| /my-account/attention | Own alerts |

Public route: /login. Route guards enforce manager/customer roles.

## AI assistant

Read tools cover portfolio summary, entity lists/details, highest transaction, aggregate count/sum/average/min/max, and outstanding ranking. Replies contain what the user asked for rather than a source dump.

Confirmed actions cover create/update/patch/delete customer; create/update/patch/block/activate/renew/delete card; activate plus ADD_ON; create/update/patch/delete merchant; create repayment/transaction; update/delete transaction.

~~~mermaid
sequenceDiagram
  actor Manager
  participant Chat
  participant Gateway
  participant Model
  participant Domain
  Manager->>Chat: Question or action request
  Chat->>Gateway: JWT + SSE
  Gateway->>Model: Credence-only context
  Model-->>Chat: Stream deltas
  alt Read
    Gateway->>Domain: allow-listed read
    Domain-->>Chat: concise live result
  else Mutation
    Chat-->>Manager: summary + Confirm and run
    Manager->>Gateway: confirmed=true
    Gateway->>Gateway: validate JWT/schema/reference
    Gateway->>Domain: allow-listed HTTP action
    Domain-->>Chat: authoritative result
    Chat->>Chat: replace confirm + refresh UI
  end
~~~

The assistant refuses off-topic work, cannot invent generated fields, cannot claim success early, rejects ambiguity, requires confirmation, persists results/history, names chats, supports delete-on-hover, streams progressively, and renders Markdown.

## Validation and security

### Schemas

- Card tier: SILVER, GOLD, PLATINUM, ULTRA_PREMIUM.
- Card type: PRIMARY, ADD_ON.
- Card status: ACTIVE, INACTIVE, BLOCKED.
- Merchant category: GROCERY, RESTAURANT, HOSPITAL, HOTEL, FUEL, PHARMACY, EDUCATION, TRAVEL, ENTERTAINMENT, ECOMMERCE, ELECTRONICS, UTILITIES, OTHER.
- Merchant status: ACTIVE, INACTIVE, BLOCKED, SUSPENDED.
- Transaction type: PURCHASE, REFUND, AUTHORIZATION, REVERSAL, CHARGEBACK, PAYMENT.
- Transaction status: PENDING, AUTHORIZED, COMPLETED, FAILED, CANCELLED.
- Payment method: CHIP, SWIPE, CONTACTLESS, ONLINE, MOBILE_WALLET.

### Security

- APP_JWT_SECRET signs JWTs.
- Access token is short-lived; refresh token is seven days.
- Angular refreshes about one minute before expiry and retries one 401.
- Failed refresh clears session, remembers target route, and redirects to login.
- Manager/customer usernames are globally unique.
- Password minimum is eight characters and BCrypt hashed.
- Backend revalidates roles and resource ownership.
- AI operations are authenticated, allow-listed, normalized, and confirmation-gated.

## Setup and run

### Requirements

Java 17, Maven 3.9+, Node.js/npm compatible with Angular 22, and Oracle FreePDB1.

Create local configuration:

~~~powershell
cd C:\Project
Copy-Item .env.example .env
~~~

~~~dotenv
DB_USERNAME=system
DB_PASSWORD=YOUR_ORACLE_PASSWORD
APP_JWT_SECRET=REPLACE_WITH_A_RANDOM_SECRET_AT_LEAST_32_CHARACTERS_LONG
CLINE_API_KEY=YOUR_CLINE_API_KEY
CLINE_API_URL=https://api.cline.bot/api/v1/chat/completions
CLINE_MODEL=minimax/minimax-m2.5
~~~

Services import service-local .env and ../.env. AI variables are optional for core operations. Never commit real values. Development JDBC URL is jdbc:oracle:thin:@//localhost:1521/freepdb1 with JPA ddl-auto update. Production should use a dedicated least-privilege Oracle user.

Start RegistryServer, then Customer/CreditCards/Merchant/Transactions, then Manager, each with:

~~~powershell
mvn spring-boot:run
~~~

Start Angular:

~~~powershell
cd C:\Project\Frontend\credIT-angular
npm install
npm start
~~~

Open <http://localhost:4200/>. Eureka: <http://localhost:4596/>.

## Testing

~~~powershell
cd C:\Project\Frontend\credIT-angular
npm test
npm run build
~~~

Run in each service:

~~~powershell
mvn test
mvn package
~~~

Recommended smoke path: manager register/login → create customer → credentials → customer request → manager approve → create active merchant → purchase → verify paired availability → fail/cancel reservation → repayment → block transaction denial → expired-card renewal → ownership denial → token refresh → confirmed AI action and UI refresh.

## Structure

~~~text
C:\Project
├── .env.example
├── README.md
├── RegistryServer/          # :4596
├── Customer/                # :8081
├── CreditCards/             # :8082
├── Transactions/            # :8083
├── Merchant/                # :8084
├── Manager/                 # :8085 auth/portals/requests/AI
└── Frontend/
    └── credIT-angular/
        └── src/app/
            ├── core/        # API, JWT, guards, time/holds
            ├── core/shell/  # role-aware shell
            ├── features/    # route pages
            └── shared/      # modal/card/pagination/chat
~~~

## Troubleshooting

- **8085 refused:** Manager is stopped. Start it.
- **Port in use:** Get-NetTCPConnection -LocalPort 8085, inspect OwningProcess, stop the stale instance or change all matching URLs.
- **401:** verify Bearer token, refresh service, and stable APP_JWT_SECRET; clear stale storage if refresh expired.
- **403:** verify role and manager/customer ownership.
- **CORS:** allow http://localhost:4200, Authorization, PATCH, PUT, and DELETE.
- **ORA-28000/ORA-65146:** connect to the correct root/PDB; unlock from root using an authorized DBA. Prefer a dedicated app user.
- **Oracle constraint:** use exact supported enum values; do not bypass constraints.
- **200 response but loading UI:** inspect component exceptions and normalize null/optional fields before string methods.
- **Raw stack trace:** render only response.message through the shared error extractor.
- **Missing src/styles.css:** verify the file and angular.json path/casing.
- **Future date disabled:** correct input min/max while retaining real-date and due-before-expiry validation.

---

Credence is designed around server-authoritative data, strict ownership, synchronized card-account invariants, safe credit reservations, explicit confirmations, and consistent manager/customer workflows.
