# PDF AI RAG

A Python project for Retrieval-Augmented Generation (RAG) with PDF support. This project will serve as a base for integrating PDF parsing and AI models.

## Project Structure
- `src/` : Source code for the project
- `requirements.txt` : Python dependencies
- `.venv/` : Virtual environment (not included in version control)

## Setup
1. Create a virtual environment:
   ```powershell
   python -m venv .venv
   .venv\Scripts\activate
   ```
2. Install dependencies:
   ```powershell
   pip install -r requirements.txt
   ```

## Future Features
- PDF parsing
- AI model integration for RAG

---
Replace this README as the project evolves.

## Java port

This repository also contains a small Java/Spring Boot port in the `java-app` folder which provides the same basic functionality as the python example: a REST endpoint `/qa-pdf/` that will read `questions.csv`, extract text from `sustainable-finance-impact-report.pdf`, and call Azure OpenAI chat completions with a `dataSources` payload.

See `java-app/README.md` for Java-specific build & run instructions.

