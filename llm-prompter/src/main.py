from fastapi import FastAPI

from src.llm import get_text_completion
from src.models.TaskRequest import TaskRequest

app = FastAPI()
glossary = Glossary()


@app.post("/question")
async def question(
        task_request: TaskRequest
):
    text_completion = get_text_completion(task_text = task_request.task_text, language = task_request.language)
    return text_completion
