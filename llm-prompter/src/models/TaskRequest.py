from pydantic import BaseModel

class TaskRequest(BaseModel):
    task_text: str
    language: str