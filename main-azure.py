
from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse
import os
import fitz  # PyMuPDF
import csv
from dotenv import load_dotenv
from openai import AzureOpenAI

app = FastAPI()
 # Get configuration settings 
load_dotenv()
azure_oai_endpoint = os.getenv("AZURE_OAI_ENDPOINT")
azure_oai_key = os.getenv("AZURE_OAI_KEY")
azure_oai_deployment = os.getenv("AZURE_OAI_DEPLOYMENT")
azure_search_endpoint = os.getenv("AZURE_SEARCH_ENDPOINT")
azure_search_key = os.getenv("AZURE_SEARCH_KEY")
azure_search_index = os.getenv("AZURE_SEARCH_INDEX")

# Initialize the Azure OpenAI client
client = AzureOpenAI(
base_url=f"{azure_oai_endpoint}/openai/deployments/{azure_oai_deployment}/extensions",
api_key=azure_oai_key,
api_version="2023-09-01-preview")
# Configure your data source
extension_config = dict(dataSources = [  
        { 
            "type": "AzureCognitiveSearch", 
            "parameters": { 
                "endpoint":azure_search_endpoint, 
                "key": azure_search_key, 
                "indexName": azure_search_index,
            }
        }]
)

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

def ask_openai(question, restrict_values=None, reasoning_for=None):
    """Ask Azure OpenAI using the OpenAI v1 client.

    Returns the assistant text (string). If the OpenAI client could not be
    instantiated this returns an error string instead of raising.
    """
    prompt = f"Question: {question}"
    if restrict_values:
        prompt += f"\nChoose one of the following: {', '.join(restrict_values)}."
    if reasoning_for:
        prompt += f"\nProvide reasoning for the previous answer: {reasoning_for}"

    if client is None:
        return "OpenAI client not available (not installed or failed to instantiate)"
    
    print(question)
    try:
        response = client.chat.completions.create(
            model = azure_oai_deployment,
            temperature = 0.5,
            max_tokens = 1000,
            messages = [
                {"role": "system", "content": "You are a helpful Assistant"},
                {"role": "user", "content": question}
            ],
            extra_body = extension_config
        )
        # Print response
        print("Response: " + response.choices[0].message.content + "\n")

        # New OpenAI v1 client returns choices with a message mapping
        choice = response.choices[0]
        answer = getattr(choice.message, "content", None) or (choice.get("message") or {}).get("content", "")
        return str(answer).strip()
    except Exception as e:
        return f"Error: {str(e)}"

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

        # if qtype == "dropdown":
        #     answer = ask_openai(pdf_text, question, restrict_values=expected_values)
        #     # Try to match expected value
        #     matched = next((v for v in expected_values if v.lower() in answer.lower()), None)
        #     answer = matched if matched else answer
        # elif qtype == "textarea":
        #     # For textarea, provide reasoning for previous answer
        #     reasoning_for = prev_answer["answer"] if prev_answer else None
        #     answer = ask_openai(pdf_text, question, reasoning_for=reasoning_for)
        #     reasoning = reasoning_for
        # else:
        answer = ask_openai(question)

        result = {
            "question": question,
            "type": qtype,
            "expected_values": expected_values,
            "answer": answer
        }
        # if qtype == "textarea":
        #     result["reasoning_for"] = reasoning
        results.append(result)
        # prev_answer = result

    return JSONResponse(content={"results": results})
