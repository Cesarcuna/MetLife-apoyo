# Soyio Sync Architecture (MVP)

```mermaid
flowchart LR
  %% Soyio Sync MVP Architecture

  subgraph SCH[Scheduler]
    A[Function Timer / Logic App\nCada 6 horas]
  end

  subgraph API[Storage API]
    B[POST /v1/domain/storage/soyio/sync]
    C[GET /v1/domain/storage/soyio/sync/status]
    D[Read watermark.json]
    E[Compute incremental window\n+ overlap]
    F[Create export]
    G[Poll export status]
    H[Download export file]
    I[Upload dataset to CDZ]
    J[Update watermark.json\nONLY on success]
  end

  subgraph SOYIO[Soyio API]
    S1[POST /api/v1/exports]
    S2[GET /api/v1/exports/{id}]
    S3[download_url]
  end

  subgraph BLOB[Azure Blob Storage]
    B1[Control path\ncdz/.../soyio/_control/watermark.json]
    B2[Data path\ncdz/.../soyio/exports/{resource}/yyyy/MM/dd]
  end

  subgraph SEC[Secrets]
    K[Key Vault / Secure vars\nSOYIO_API_KEY]
    R[Rotation scripts\noutside runtime]
  end

  A --> B
  C --> B1
  B --> K
  B --> D --> E --> F
  F --> S1
  G --> S2
  H --> S3
  B --> G
  B --> H
  B --> I --> B2
  B --> J --> B1

  %% Error paths
  S1 -. 401/403 .-> X1[Fail run\nNo watermark advance]
  S2 -. export.failed .-> X2[Fail run\nNo watermark advance]
  G -. timeout .-> X3[Fail run\nNo watermark advance]
  H -. file > soyio.max-download-bytes .-> X4[Fail run\nSplit window and retry]
  I -. upload failure .-> X5[Fail run\nNo watermark advance]

  %% Legend
  L1[Legend:\nSolid lines = happy path\nDotted lines = error path\nWatermark advances only in successful run]

  classDef ok fill:#e9f7ef,stroke:#1e8449,color:#145a32;
  classDef warn fill:#fdecea,stroke:#c0392b,color:#7b241c;
  classDef note fill:#eef5ff,stroke:#2e86c1,color:#1b4f72;

  class A,B,C,D,E,F,G,H,I,J,S1,S2,S3,B1,B2,K,R ok;
  class X1,X2,X3,X4,X5 warn;
  class L1 note;
```

## Notes

- Rotation of Soyio keys is out-of-runtime (scripts + Key Vault).
- Watermark advances only when the run is fully successful.
- `soyio.max-download-bytes` protects runtime memory for oversized exports.
