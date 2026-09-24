# EOS Chile Storage Service - API Reference

## Check Output
This microservice provides a generic interface for storing, retrieving, and listing files in Azure Blob Storage. It is designed to be agnostic of the business domain, allowing different consumers (Claims, Policy, etc.) to specify their own containers and folder structures dynamically.

## Base URL
**Local (Direct)**: `http://localhost:8080/v1/domain/storage`
**Gateway (Public)**: `https://latam.dev.internal.apis.metlife.com/chile/storage/api/files`

## Authentication & Headers
All requests must include the standard GSSP headers for tracing and security.

| Header Name | Required | Description |
|---|---|---|
| `x-gssp-transactionid` | Yes | Unique ID for tracking the transaction across services. |
| `Content-Type` | Yes | `application/json` |

---

## 1. Send File (Upload)
Uploads a file to a specific container and folder (entity ID) in Azure Blob Storage.

*   **Method**: `POST`
*   **Path**: `/sendFile`
*   **Service ID**: `0901`

### Request Body Parameters
| Parameter | Type | Required | Description | Validation |
|---|---|---|---|---|
| `containerName` | String | Yes | The target Azure Blob Container name (e.g., `claims-docs`, `policy-files`). | Alphanumeric, dots, dashes, underscores. |
| `entityId` | String | Yes | The logical folder or ID to group files under (e.g., Policy Number, Claim ID, or nested path `provider/2026/reports`). | Alphanumeric, dots, dashes, underscores, slashes. No `..`. |
| `fileName` | String | Yes | The name of the file to be saved (including extension). | Non-empty string. |
| `base64` | String | Yes | The Base64 encoded content of the file. | Non-empty string. |

### Example Request (Local)
```json
POST http://localhost:8080/v1/domain/storage/sendFile
Headers:
  x-gssp-transactionid: 12345-abcde
  Content-Type: application/json

Body:
{
    "containerName": "policy-documents",
    "entityId": "POL-99887766",
    "fileName": "contract_signed.pdf",
    "base64": "JVBERi0xLjcKCjEgMCBvYmogICB..."
}
```

### Example Response (Success)
```json
{
    "message": "File Uploaded Successfully",
    "statusCode": "OK",
    "metadata": {
        "gsspTrxId": "12345-abcde",
        "apiName": "Chile Data Hub Storage Service",
        "timestamp": "2025-01-05T12:00:00.000"
    }
}
```

### Error Responses
The API returns a standardized GSSP error structure for all 4xx/500 responses.

**Example Error Response (404 Not Found):**
```json
{
  "errors": [
    {
      "code": "API-0901-404.1",
      "description": "The specified container does not exist",
      "element": "",
      "extension": {
        "providerCode": "404.1",
        "providerMessage": "The specified container does not exist for POST /v1/domain/storage/sendFile"
      }
    }
  ]
}
```

*   **400 Bad Request**: Invalid Input (e.g., path traversal characters) or Azure 409 Conflict mapped to Bad Request.
*   **404 Not Found**: Container or Entity/Blob not found.
*   **500 Internal Server Error**: System or Connectivity failures.

---

## 2. Retrieve File (Download)
Retrieves a specific file from Azure Blob Storage and returns its content in Base64 format.

*   **Method**: `GET`
*   **Path**: `/retrieveFile`
*   **Service ID**: `0902` (Shared)

### Query Parameters
| Parameter | Type | Required | Description |
|---|---|---|---|
| `containerName` | String | Yes | The Azure Blob Container name. |
| `entityId` | String | Yes | The logical folder/ID. |
| `fileName` | String | Yes | The name of the file to retrieve. |

**Example URL**:
`/retrieveFile?containerName=policy-documents&entityId=POL-99887766&fileName=contract_signed.pdf`

### Example Request (Local)
```http
GET http://localhost:8080/v1/domain/storage/retrieveFile?containerName=policy-documents&entityId=POL-99887766&fileName=contract_signed.pdf
Headers:
  x-gssp-transactionid: 12345-abcde
```

### Example Response (Success)
```json
{
    "entityId": "POL-99887766",
    "fileName": "contract_signed.pdf",
    "base64": "JVBERi0xLjcKCjEgMCBvYmogICB...",
    "metadata": {
        "gsspTrxId": "12345-abcde",
        "timestamp": "2025-01-05T12:05:00.000"
    }
}
```

### Error Responses
See [Standard Error Response Structure](#error-responses).
*   **404 Not Found**: File not found in Blob Storage.
*   **400 Bad Request**: Missing parameters.

---

## 3. List Files
Lists all files contained within a specific "folder" (Entity ID) inside a container.

*   **Method**: `GET`
*   **Path**: `/listFiles`
*   **Service ID**: `0902`

### Query Parameters
| Parameter | Type | Required | Description |
|---|---|---|---|
| `containerName` | String | Yes | The Azure Blob Container name. |
| `entityId` | String | Yes | The logical folder/ID. |

**Example URL**:
`/listFiles?containerName=policy-documents&entityId=POL-99887766`

### Example Request (Local)
```http
GET http://localhost:8080/v1/domain/storage/listFiles?containerName=policy-documents&entityId=POL-99887766
Headers:
  x-gssp-transactionid: 12345-abcde
```

### Example Response (Success)
```json
{
    "items": [
        {
            "name": "contract_signed.pdf",
            "size": 1024045,
            "createdOn": "2025-01-01T10:00:00Z",
            "lastModified": "2025-01-01T10:00:00Z"
        },
        {
            "name": "id_card_scan.jpg",
            "size": 51200,
            "createdOn": "2025-01-01T10:05:00Z",
            "lastModified": "2025-01-01T10:05:00Z"
        }
    ],
    "metadata": {
        "gsspTrxId": "12345-abcde",
        "timestamp": "2025-01-05T12:10:00.000"
    }
}
```

### Error Responses
*   **404 Not Found**: Container or Entity ID prefix not found.
*   **400 Bad Request**: Missing parameters or invalid characters.

---



## 4. Send File Multipart (Upload - Efficient)
Uploads a file using standard `multipart/form-data`. This is the most network-efficient method as it streams binary data directly without JSON overhead.

*   **Method**: `POST`
*   **Path**: `/sendFileMultipart`
*   **Service ID**: `0905`

### Body Parameters (Multipart Form)
| Parameter | Type | Required | Description |
|---|---|---|---|
| `file` | File | Yes | The file binary stream. |
| `containerName` | String | Yes | Target container. |
| `entityId` | String | Yes | Target folder (nested paths allowed). |

### Example Request
`POST /sendFileMultipart` (Body: form-data)

---

