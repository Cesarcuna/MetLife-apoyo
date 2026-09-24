# EOS Chile Storage Service - CDZ Usage Examples

Based on your Azure Data Lake environment, here are the exact requests to interact with the **`cdz`** container and the **`proveedores_externos`** folder.

## Data Context
*   **Container Name**: `cdz`
*   **Entity ID (Folder)**: `proveedores_externos`
*   **File Name**: `procesos_pipelines.csv`

---

## Nested Path Example (Rutas Anidadas)
You can use slashes `/` in the `entityId` to organize files in subfolders.

**Request:**
```http
GET http://localhost:8080/v1/domain/storage/listFiles?containerName=cdz&entityId=proveedores_externos/serodi/tabla00
```
*(This retrieves files specifically from the `serodi/tabla00` subfolder inside `proveedores_externos`)*

---

## 1. List Files (Listar Archivos)
To see the files inside `cdz/proveedores_externos`.

**Request:**
```http
GET http://localhost:8080/v1/domain/storage/listFiles?containerName=cdz&entityId=proveedores_externos
```

**Headers:**
```txt
x-gssp-transactionid: test-trx-001
Content-Type: application/json
```

**Expected Response:**
```json
{
  "items": [
    {
      "name": "procesos_pipelines.csv",
      "size": 1234,
      "createdOn": "2026-06-01T17:41:30Z",
      "lastModified": "2026-06-01T17:41:30Z"
    }
  ],
  "metadata": { ... }
}
```

---

## 2. Retrieve File (Descargar Archivo)
To download the `procesos_pipelines.csv` file.

**Request:**
```http
GET http://localhost:8080/v1/domain/storage/retrieveFile?containerName=cdz&entityId=proveedores_externos&fileName=procesos_pipelines.csv
```

**Headers:**
```txt
x-gssp-transactionid: test-trx-002
Content-Type: application/json
```

**Expected Response:**
```json
{
  "entityId": "proveedores_externos",
  "fileName": "procesos_pipelines.csv",
  "base64": "SEMlc2VsZWN0ICwgbm9tYnJlICwgYXBlbGxpZG8gZnJvbSB...",
  "metadata": { ... }
}
```

---

## 3. Send File (Subir Archivo)
To upload a new file (e.g., `nuevo_reporte.csv`) to that same folder.

**Request:**
`POST http://localhost:8080/v1/domain/storage/sendFile`

**Headers:**
```txt
x-gssp-transactionid: test-trx-003
Content-Type: application/json
```

**Body:**
```json
{
  "containerName": "cdz",
  "entityId": "proveedores_externos",
  "fileName": "nuevo_reporte.csv",
  "base64": "U2VydmljaW8sRXN0YWRvCkFwaSxPSwp..."
}
```

**Expected Response:**
```json
{
  "message": "File Uploaded Successfully",
  "statusCode": "OK",
  "metadata": { ... }
}
```



## 4. Send File Multipart (Subir Archivo Multipart)
To upload a file using **Standard Multipart Form Data** (most efficient way).

**Request:**
`POST http://localhost:8080/v1/domain/storage/sendFileMultipart`

**Body (Multipart Form):**
*   `containerName`: `cdz` (Text)
*   `entityId`: `proveedores_externos` (Text)
*   `file`: select file from disk (File)

**Expected Response:**
```json
{
  "message": "File Uploaded Successfully",
  "statusCode": "OK",
  "fileName": "my_document.pdf",
  "size": 10240,
  "metadata": { ... }
}
```

---
