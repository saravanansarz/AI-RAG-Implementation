

# RAG with LangChain, local models, and FAISS
from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse
import os
import fitz  # PyMuPDF
import csv
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_community.embeddings.huggingface import HuggingFaceEmbeddings
from langchain_community.vectorstores.faiss import FAISS
from langchain_community.llms.huggingface_pipeline import HuggingFacePipeline
from langchain_classic.chains.retrieval_qa.base import RetrievalQA
from transformers import pipeline, AutoTokenizer, AutoModelForQuestionAnswering

app = FastAPI()

PDF_PATH = "sustainable-finance-impact-report.pdf"
CSV_PATH = "questions.csv"

def extract_pdf_text(pdf_path):
    try:
        doc = fitz.open(pdf_path)
        text = "\n".join(page.get_text() for page in doc)
        doc.close()
        return text
    except Exception as e:
        raise RuntimeError(f"Failed to read PDF: {str(e)}")

def read_questions(csv_path):
    questions = []
    with open(csv_path, newline='', encoding='utf-8') as csvfile:
        reader = csv.DictReader(csvfile)
        for row in reader:
            questions.append({
                "question": row.get("question", "").strip(),
                "type": row.get("type", "").strip(),
                "expected_values": [v.strip() for v in row.get("expected values", "").strip('"').split(',')] if row.get("expected values") else []
            })
    return questions


# Manual RAG pipeline for Hugging Face QA
def build_rag_pipeline(pdf_text):
    splitter = RecursiveCharacterTextSplitter(chunk_size=500, chunk_overlap=50)
    docs = splitter.create_documents([pdf_text])
    embeddings = HuggingFaceEmbeddings(model_name="sentence-transformers/all-MiniLM-L6-v2")
    vectordb = FAISS.from_documents(docs, embeddings)
    qa_pipeline = pipeline("question-answering", model="distilbert-base-uncased-distilled-squad")
    def rag_qa(question, top_k=4):
        if not question or not question.strip():
            return {"answer": "Question is empty.", "explanation": "No question provided."}
        retriever = vectordb.as_retriever()
        relevant_docs = retriever.invoke(question)
        # Use the most relevant chunk as context
        context_chunks = [doc.page_content for doc in relevant_docs[:top_k]]
        context = "\n".join(context_chunks)
        result = qa_pipeline({"question": question, "context": context})
        # Explanation: show the chunk(s) used
        explanation = f"Answer derived from the following context chunk(s):\n---\n{context}\n---"
        return {"answer": result["answer"] if isinstance(result, dict) and "answer" in result else result, "explanation": explanation}
    return rag_qa

@app.get("/qa-pdf/")
def qa_pdf():
    # Check files exist
    if not os.path.exists(PDF_PATH):
        raise HTTPException(status_code=404, detail=f"PDF file '{PDF_PATH}' not found.")
    if not os.path.exists(CSV_PATH):
        raise HTTPException(status_code=404, detail=f"CSV file '{CSV_PATH}' not found.")

    # Extract PDF text
    pdf_text = extract_pdf_text(PDF_PATH)
    # Read questions
    questions = read_questions(CSV_PATH)
    # Build RAG pipeline
    rag_qa = build_rag_pipeline(pdf_text)

    results = []
    for q in questions:
        question = q["question"]
        expected_values = q.get("expected_values", [])
        qa_result = rag_qa(question)
        answer = qa_result["answer"]
        # Always choose the closest expected value as the answer
        if expected_values:
            # Use simple similarity: pick the expected value with the most word overlap with the answer
            def score(ev):
                return sum(1 for w in ev.lower().split() if w in str(answer).lower())
            best = max(expected_values, key=score)
            final_answer = best
        else:
            final_answer = answer
        # Clean up answer and explanation for better readability
        clean_answer = str(final_answer).replace("\n", " ").strip()
        clean_explanation = str(qa_result["explanation"]).replace("\n", " ").strip()
        results.append({
            "question": question,
            "answer": clean_answer,
            "explanation": clean_explanation
        })
    return JSONResponse(content={"results": results})
