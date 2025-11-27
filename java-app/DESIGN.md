# AI-RAG (Java) — Design Document

Status: Draft — a compact, developer-facing design doc describing the Java port of the PDF → RAG → Q/A pipeline.

Overview
--------
This service is a small Java Spring Boot application that replicates the original Python RAG prototype. It ingests PDFs and a CSV of questions and provides an endpoint /qa-pdf/ that returns answers created by sending the question (optionally enriched with retrieved context) to Azure OpenAI Chat Completions. The app includes a simple in-memory RAG pipeline: text chunking, embeddings (Azure OpenAI), an in-memory vector store (cosine similarity), retrieve top-k context, and call the chat completion API with context.

Goals and non-goals
-------------------
- Goals:
  - Provide a simple, readable Java implementation that mirrors the Python implementation.
  - Demonstrate a RAG flow: chunking → embeddings → vector retrieval → GPT prompt with context.
  - Keep the example runnable without heavy infra (therefore an in-memory vector store used).
- Non-goals (for this iteration):
  - Not competing with production-grade vector DBs or FAISS integrations.
  - Not implementing fine-grained concurrency control or distributed indexing.

High-level Architecture
-----------------------

App component diagram (logical)

  Client
    |
    | HTTP GET /qa-pdf/
    v
  QaPdfController  <---- application.properties / env
    |  ^                         (AZURE_OAI_ENDPOINT, AZURE_OAI_KEY,...)
    |  | orchestrates
    |  +--> PdfService (PDFBox) --extract--> text
    |  +--> CsvService (OpenCSV) --reads--> questions
    |  +--> ChunkService --split--> chunk texts
    |  +--> EmbeddingService --call Azure OAI Embeddings--> vectors
    |  +--> VectorStoreService --index + search (cosine similarity)-->
    |  +--> AzureOpenAiService --chat completions (context included)--> model
    v
  Return JSON (answers, raw response, context)

Components & Responsibilities
-----------------------------
- QaPdfController
  - Orchestrates the pipeline.
  - Reads PDF and CSV (via services), builds the embedding index, retrieves relevant context for each question, and calls the chat completion with that context.

- PdfService
  - Uses Apache PDFBox to extract raw text from a PDF file.

- CsvService
  - Reads `questions.csv` (OpenCSV) and maps rows to `Question` objects.

- ChunkService
  - Splits large documents into overlapped text chunks to preserve context across chunk boundaries.
  - Tunable chunk size and overlap.

- EmbeddingService
  - Calls the Azure OpenAI embeddings endpoint and returns vectors for each chunk or user question.
  - Returns List<double[]> for each input text.

- VectorStoreService
  - Simple in-memory index of `EmbeddingRecord` (id, text, vector).
  - Provides nearest neighbors using cosine similarity.
  - Small scale; designed for illustrating the concept — replaceable by FAISS/or Azure Search.

- AzureOpenAiService
  - Calls Azure OpenAI chat completions endpoint (`/openai/deployments/{deployment}/chat/completions`) with an optional `dataSources` extension payload and supports submitting a combined context + question.

- Models
  - Question: holds CSV data (question, type, expected values).
  - EmbeddingRecord: id, text, vector.
  - DocChunk: short-lived representation of a chunk (id/text/page) used in chunking flows.

Data Flow (detailed)
--------------------
1. PDF extracted: PdfService loads the PDF file and returns the concatenated text.
2. Chunking: ChunkService breaks the text into overlapping windows of characters. The overlap avoids losing information at chunk boundaries.
3. Embedding: EmbeddingService sends a batch request to Azure OpenAI Embeddings endpoint for all chunks and obtains vectors.
4. Indexing: VectorStoreService stores EmbeddingRecords (id, text, vector). This store is ephemeral in the current design.
5. Question handling: For each question in questions.csv:
   - The question is embedded via EmbeddingService.
   - VectorStoreService.nearestNeighbors returns top-K matching chunks.
   - The retrieved chunk texts are concatenated as `context`.
   - AzureOpenAiService.askWithContext sends a user message containing the context and question to the model. The model reply is returned to the client.

Design decisions and rationale
-----------------------------
- In-memory vector store
  - Rationale: keeps the repo and example dependency-light, easy to run locally. Suitable for small test documents.
  - Extension: replace with FAISS, Qdrant, Milvus, or use Azure Cognitive Search with vector capabilities for production scaling.

- Azure OpenAI (via REST)
  - The application uses OkHttp + Jackson directly to illustrate the API calls similar to the given Python sample.
  - The payload contains `dataSources` for Azure-specific extension usage (the Python code used `extra_body`). This is preserved for feature parity.

- Chunking by characters
  - Simpler, predictable token size control. You may choose to chunk by sentences and tokens for better semantics (token-based chunking recommended when embedding costs matter).

API & Local contract
--------------------
- GET /qa-pdf/
  - Behavior: reads `sustainable-finance-impact-report.pdf` (default location), reads `questions.csv` (default), runs RAG, and returns JSON.
  - Response example (abridged):

  {
    "results": [
      {
        "question": "Has the client executed any sustainable finance transaction previously",
        "type": "dropdown",
        "expected_values": ["Yes","No"],
        "answer": "Yes — example response",
        "context": "...retrieved chunk text...",
        "raw": { /* raw azure response */ }
      }
    ]
  }

Configuration & Environment variables
------------------------------------
All configuration can be set either in `src/main/resources/application.properties` or as environment variables.

- Required/Useful variables
  - AZURE_OAI_ENDPOINT — e.g. https://your-resource.openai.azure.com
  - AZURE_OAI_KEY
  - AZURE_OAI_DEPLOYMENT — chat model deployment name
  - AZURE_OAI_EMBEDDINGS_DEPLOYMENT — embeddings deployment name (optional fallback to same as chat deployment)
  - AZURE_SEARCH_ENDPOINT, AZURE_SEARCH_KEY, AZURE_SEARCH_INDEX — if using Azure Cognitive Search features
  - PDF_PATH — path to PDF file (config property app.pdf.path)

Observability, errors & fault handling
-------------------------------------
- Existing error handling
  - Services surface IO exceptions (PDF or CSV missing), and controllers return HTTP 404 for missing resources.
  - Embeddings and chat calls return error maps when requests fail.

- Improvements recommended for production
  - Centralized, structured logging (e.g., Logback + context fields) rather than System.err.
  - Retries with exponential backoff for transient network failures when calling Azure endpoints.
  - Circuit breaker (Resilience4j) and metrics for embedding/chat calls.
  - Health endpoints and readiness/liveness (Spring Boot Actuator).

Performance & scaling considerations
-----------------------------------
- For small PDFs, the in-memory approach is fine. For larger datasets or many documents:
  - Persist embeddings and store them in a vector DB (FAISS, Qdrant, Pinecone, Azure Cognitive Search with vector field) — avoids recomputing embeddings.
  - Use batched embedding requests and server-side parallelism while respecting rate limits.
  - Shard or partition vector indices and load on demand.

Security considerations
-----------------------
- Keep secrets out of code and source control. Use environment variables or secret stores.
- Use managed identity or KeyVault where possible for Azure services.
- Sanitize and rate-limit user inputs. Do not trust model outputs for critical decisions; add policy enforcement and/or content filtering if required.

Testing strategy
----------------
- Unit tests
  - VectorStoreService: nearest neighbor tests (already added in repo).
  - CsvService / PdfService: small file-based tests; add sample fixtures under src/test/resources.

- Integration tests
  - Replace Azure endpoints with a local mock (WireMock) for embeddings and chat to test the end-to-end RAG flow.
  - CI should run integration tests against mock endpoints; e2e can be gated and run only in secure pipelines with real keys.

CI/CD & Deployment
------------------
- Build: Maven, package a FAT JAR via spring-boot-maven-plugin.
- Containerization: Provide a small Dockerfile (multi-stage) to build and run the JAR.
- Deploy: simple container to Azure App Service / Container Apps / AKS, or a VM/container on any cloud provider.

Example roadmap for next improvements
------------------------------------
1. Persist embeddings to a vector DB (FAISS or managed) and implement incremental updates.
2. Add a re-ranker or LLM-based filter for retrieved chunks to reduce noisy context.
3. Introduce caching and resilient retries for Azure API calls.
4. Add health endpoints, structured logging, metrics, and distributed tracing.
5. Add production-grade security: KeyVault integration and secrets rotation.

Appendix — Files and where to look
---------------------------------
- `src/main/java/com/example/airag/controller/QaPdfController.java` — main orchestration
- `src/main/java/com/example/airag/service/PdfService.java` — PDF extraction
- `src/main/java/com/example/airag/service/CsvService.java` — questions parser
- `src/main/java/com/example/airag/service/ChunkService.java` — chunking
- `src/main/java/com/example/airag/service/EmbeddingService.java` — Azure embeddings
- `src/main/java/com/example/airag/service/VectorStoreService.java` — in-memory vector store
- `src/main/java/com/example/airag/service/AzureOpenAiService.java` — chat completions

Contact / context
-----------------
This design doc was generated based on the current java-app implementation in this repository and follows the Python prototype design patterns in the top-level workspace. For follow-up actions, I can: add Dockerfile + CI, switch the vector store to an external DB (FAISS or Azure Cognitive Search), or add end-to-end tests using a mocked Azure OpenAI service.
