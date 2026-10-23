from enum import Enum

from pydantic import BaseModel, Field


class UserRole(str, Enum):
    BORROWER = "borrower"
    BROKER = "broker"
    UNDERWRITER = "underwriter"


class UserProfile(BaseModel):
    uid: str
    email: str
    role: UserRole
    display_name: str = Field(default="")

class UserProfileCreate(BaseModel):
    display_name: str = Field(default="", max_length=100)