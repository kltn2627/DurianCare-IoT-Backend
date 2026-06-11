from typing import Literal

from pydantic import BaseModel, Field, field_validator


class ChatQuestion(BaseModel):
    question: str = Field(min_length=1, max_length=2000)

    @field_validator("question")
    @classmethod
    def question_must_not_be_blank(cls, value: str) -> str:
        question = value.strip()
        if not question:
            raise ValueError("Question must not be blank")
        return question


class ChatAnswerData(BaseModel):
    answer: str
    sources: list[str]


class ChatAnswerResponse(BaseModel):
    status: Literal["success"]
    data: ChatAnswerData
