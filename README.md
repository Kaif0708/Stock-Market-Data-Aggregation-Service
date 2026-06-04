# Stock Market Data Aggregation Service

A lightweight REST API built with Spring Boot and Apache Cassandra that aggregates minute‑level stock candles into higher‑timeframe bars. The repository includes a CLI client and a Vue.js dashboard.

## Workspace Layout

```
submission/
├── server/          # Spring Boot API & data ingestion
├── client/          # Backend CLI client
├── web-client/      # Vue.js dashboard
├── media/           # Demo video
├── schema.cql       # Cassandra schema
├── README.md        # This guide
```

## Prerequisites
- **Java JDK 17** or newer
- **Apache Maven**
- **Docker** (recommended for Cassandra)

## Database Setup (Docker)
```bash
# Pull and run a Cassandra container
docker run --name cassandra-dev -p 9042:9042 -d cassandra:latest
# Wait a few seconds for Cassandra to start
sleep 20
# Apply the schema
docker cp schema.cql cassandra-dev:/schema.cql
docker exec -it cassandra-dev cqlsh -f /schema.cql
```

## Running the API Server
```bash
cd server
mvn spring-boot:run
```
The server starts on `http://localhost:8080` and automatically ingests `stock_data.csv`.

## Running the CLI Client
```bash
cd ../client
mvn clean package
java -jar target/client-1.0.0-jar-with-dependencies.jar RELIANCE 15m "2024-01-15 09:15:00" "2024-01-15 09:45:00"
```

## Running the Web Dashboard
```bash
cd ../web-client
# Open directly (Windows)
start index.html
# Or serve locally
python -m http.server 8000   # then visit http://localhost:8000
```

## Demo Video
> **Note:** The demonstration video `dashboard-demo.mp4` is stored in `media/` (i.e., `submission/media/dashboard-demo.mp4`). The relative path works when the README is viewed from the repository root.

```html
<video src="media/dashboard-demo.mp4" controls width="800" style="border-radius:8px;box-shadow:0 8px 24px rgba(0,0,0,0.2);"></video>
```

---

## 📚 API Reference

### `GET /api/v1/candles`

Gets aggregated candle data for a stock symbol.

**Path**: `/api/v1/candles`  
**Method**: `GET`

#### Query Parameters

| Parameter   | Type    | Required | Description                                                                 | Example |
|------------|---------|----------|-----------------------------------------------------------------------------|---------|
| `symbol`   | string  | ✅       | Stock ticker symbol (case‑insensitive)                                      | `RELIANCE` |
| `timeframe`| string  | ✅       | Aggregation timeframe – one of `1m`, `5m`, `15m`, `30m`, `1h`, `1d`          | `15m` |
| `start_date`| string | ✅       | Start date/time in UTC (`yyyy‑MM‑dd HH:mm:ss` **or** ISO‑8601)               | `2024-01-15 09:15:00` |
| `end_date` | string  | ✅       | End date/time in UTC (same format as `start_date`)                           | `2024-01-15 09:45:00` |
| `page`     | int     | ❌       | Zero‑based page index for pagination (default: all results)                | `0` |
| `size`     | int     | ❌       | Page size (must be > 0) – how many candles per page                         | `10` |

#### Successful Response (`200 OK`)

```json
{
  "symbol": "RELIANCE",
  "timeframe": "15m",
  "candles": [
    {
      "datetime": "2024-01-15T09:15:00Z",
      "open": 2450.5,
      "high": 2472.3,
      "low": 2448.1,
      "close": 2465.8,
      "volume": 260300
    },
    {
      "datetime": "2024-01-15T09:30:00Z",
      "open": 2465.8,
      "high": 2480.0,
      "low": 2463.5,
      "close": 2475.6,
      "volume": 241100
    }
  ],
  "count": 2
}
```

#### Error Responses

| Status | Condition | Example Message |
|--------|------------|-----------------|
| `400 Bad Request` | Missing/invalid parameter (e.g., unsupported `timeframe`) | "Unsupported timeframe '12m'. Supported: [1m, 5m, 15m, 30m, 1h, 1d]" |
| `400 Bad Request` | `start_date` after `end_date` | "start_date cannot be after end_date" |
| `404 Not Found` | No data found for the given criteria | "No candlestick data found for symbol: 'INFY' in range [2024-01-15 09:15:00 to 2024-01-15 15:30:00]" |

#### cURL Example (PowerShell)

> **Note:** PowerShell aliases `curl` → `Invoke-WebRequest`. Use `curl.exe` for the classic CLI.

```bash
curl.exe -G "http://localhost:8080/api/v1/candles" \
  --data-urlencode "symbol=RELIANCE" \
  --data-urlencode "timeframe=15m" \
  --data-urlencode "start_date=2024-01-15 09:15:00" \
  --data-urlencode "end_date=2024-01-15 09:45:00"
```

*Add optional pagination parameters `page` and `size` in the same way if you need them.*

---

### How the endpoint works (high‑level)

1. **Validation** – Checks that all required parameters are present and that `timeframe` belongs to the supported list.
2. **Date parsing** – Accepts ISO‑8601, `yyyy‑MM‑dd HH:mm:ss`, or plain `yyyy‑MM‑dd`. Normalised to UTC `Instant`.
3. **Aggregation** – Delegates to `CandleAggregationService` which groups raw 1‑minute candles.
4. **Pagination** – If `page` & `size` are supplied, the result list is sliced.
5. **Response** – Wrapped in `CandleResponse`.

All of this logic lives in `src/main/java/com/stock/aggregator/controller/StockCandleController.java`.
### `GET /api/v1/candles`
Retrieves aggregated candle data.

**Query Parameters**
| Parameter | Type | Required | Description | Example |
| :--- | :--- | :--- | :--- | :--- |
| `symbol` | String | Yes | Stock ticker symbol | `RELIANCE` |
| `timeframe` | String | Yes | Aggregation timeframe (`1m`, `5m`, `15m`, `30m`, `1h`, `1d`) | `15m` |
| `start_date` | String | Yes | Start date in UTC (`yyyy-MM-dd HH:mm:ss` or ISO‑8601) | `2024-01-15 09:15:00` |
| `end_date` | String | Yes | End date in UTC (`yyyy-MM-dd HH:mm:ss` or ISO‑8601) | `2024-01-15 09:45:00` |
| `page` | Integer | No | Page index (0‑based) | `0` |
| `size` | Integer | No | Page size (must be >0) | `10` |

**cURL Example** (use `curl.exe` on PowerShell):
```bash
curl.exe -G "http://localhost:8080/api/v1/candles" \
  --data-urlencode "symbol=RELIANCE" \
  --data-urlencode "timeframe=15m" \
  --data-urlencode "start_date=2024-01-15 09:15:00" \
  --data-urlencode "end_date=2024-01-15 09:45:00"
```
