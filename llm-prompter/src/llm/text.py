from dotenv import load_dotenv
from openai import OpenAI

from .models import QuestionModel

load_dotenv()
client = OpenAI()

MODEL_NAME = "gpt-4o-mini"
SYSTEM_PROMPT = """
You are going to be used as a non existent student which solution I am going to compare to all other 
real student's solutions. If there are any similarities between the code you are going to write and the code
of the real students, I will be able to detect if student has been using AI to generate the code.
Using AI to generate code is not allowed for this exam and I want to detect all possible cheating.
Please write code that solves the folwowing problem: {} You have to write the code for the following programming language: {}.
"""


def get_text_completion(task_text: str, language: str) -> QuestionModel:
    prompt = SYSTEM_PROMPT.format(task_text, language)
    response = client.beta.chat.completions.parse(
        top_p=0.8,
        frequency_penalty=0.75,
        presence_penalty=0.5,
        model=MODEL_NAME,
        messages=[
            {"role": "system", "content": prompt}
        ],
        response_format=QuestionModel
    )

    return QuestionModel(
        question=task_text,
        answer=response.choices[0].message.content.strip()
    )
