# AI-RAG Java (Spring Boot)

This is a minimal Java/Spring Boot port of the Python PDF -> QA example in this repo. It:
- extracts text from a PDF using Apache PDFBox
- reads questions from `questions.csv` using OpenCSV
- calls Azure OpenAI Chat Completions to ask each question (includes `dataSources` payload for Azure Cognitive Search)

RAG (Retrieval-Augmented Generation)
----------------------------------

This version also implements a simple RAG pipeline:

- The PDF text is chunked into overlapping chunks by `ChunkService`.
- Chunks are embedded using the Azure OpenAI Embeddings endpoint via `EmbeddingService`.
- Embeddings are stored in an in-memory vector index `VectorStoreService`.
- For each question we embed the question, retrieve the top-k similar chunks, join them as 'context', then call the chat completions endpoint with the retrieved context included in the user message. The controller coordinates this flow.

The pipeline is intentionally small and self-contained (no external FAISS dependency). You can swap the vector store for FAISS/Azure Cognitive Search if desired.

Prerequisites
- Java 17 or later
- Maven
- PDF file `sustainable-finance-impact-report.pdf` and `questions.csv` placed in the project root (same directory as `java-app`) or adjust `application.properties`
- Azure environment variables configured (or place values in `src/main/resources/application.properties`):
  - AZURE_OAI_ENDPOINT
  - AZURE_OAI_KEY
  - AZURE_OAI_DEPLOYMENT
  - AZURE_SEARCH_ENDPOINT
  - AZURE_SEARCH_KEY
  - AZURE_SEARCH_INDEX

Build & run

From the `java-app` folder:

```powershell
mvn -q -DskipTests package
java -jar target/ai-rag-java-0.1.0.jar
```

The application exposes an endpoint:

- GET http://localhost:8080/qa-pdf/

It returns JSON like the Python service where each question is asked and responses are returned per-question.

Notes
- The Azure OpenAI chat completions endpoint used here mirrors the Python usage by adding `dataSources` in the request body. The preview contract for Azure OpenAI varies across versions — you may need to adjust `api-version` or the extension payload to match your Azure subscription and API variety.

Tips: tuning and next steps
- Increase chunk size or adjust overlap in `ChunkService` to trade performance vs context fidelity.
- Persist the embedding index (to disk or a DB) to avoid re-embedding on each run. You can serialize `EmbeddingRecord` objects for this.
- Swap in FAISS (native or via JNI) or a managed vector DB for large data sets. Azure Cognitive Search can be used instead of the in-memory index by uploading vector fields.
