from pydantic import BaseModel


class QuestionModel(BaseModel):
    question: str
    answer: str
