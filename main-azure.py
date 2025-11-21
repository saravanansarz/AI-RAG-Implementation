
from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse
import os
import fitz  # PyMuPDF
import openai
import csv

app = FastAPI()

# Set your Azure OpenAI API key and endpoint as environment variables or directly here
AZURE_OPENAI_API_KEY = os.getenv("AZURE_OPENAI_API_KEY", "<YOUR_AZURE_OPENAI_API_KEY>")
AZURE_OPENAI_ENDPOINT = os.getenv("AZURE_OPENAI_ENDPOINT", "<YOUR_AZURE_OPENAI_ENDPOINT>")
AZURE_OPENAI_DEPLOYMENT = os.getenv("AZURE_OPENAI_DEPLOYMENT", "<YOUR_DEPLOYMENT_NAME>")

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

def ask_openai(context, question, restrict_values=None, reasoning_for=None):
    prompt = f"Context: {context}\n\nQuestion: {question}\nAnswer:"
    if restrict_values:
        prompt += f"\nChoose one of the following: {', '.join(restrict_values)}."
    if reasoning_for:
        prompt += f"\nProvide reasoning for the previous answer: {reasoning_for}"
    try:
        response = openai.ChatCompletion.create(
            engine=AZURE_OPENAI_DEPLOYMENT,
            api_key=AZURE_OPENAI_API_KEY,
            api_base=AZURE_OPENAI_ENDPOINT,
            api_type="azure",
            api_version="2023-05-15",
            messages=[{"role": "system", "content": "You are a helpful assistant."},
                      {"role": "user", "content": prompt}]
        )
        answer = response.choices[0].message["content"].strip()
    except Exception as e:
        answer = f"Error: {str(e)}"
    return answer

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

    results = []
    prev_answer = None
    for idx, q in enumerate(questions):
        question = q["question"]
        qtype = q["type"]
        expected_values = q["expected_values"]
        answer = None
        reasoning = None

        if qtype == "dropdown":
            answer = ask_openai(pdf_text, question, restrict_values=expected_values)
            # Try to match expected value
            matched = next((v for v in expected_values if v.lower() in answer.lower()), None)
            answer = matched if matched else answer
        elif qtype == "textarea":
            # For textarea, provide reasoning for previous answer
            reasoning_for = prev_answer["answer"] if prev_answer else None
            answer = ask_openai(pdf_text, question, reasoning_for=reasoning_for)
            reasoning = reasoning_for
        else:
            answer = ask_openai(pdf_text, question)

        result = {
            "question": question,
            "type": qtype,
            "expected_values": expected_values,
            "answer": answer
        }
        if qtype == "textarea":
            result["reasoning_for"] = reasoning
        results.append(result)
        prev_answer = result

    return JSONResponse(content={"results": results})
