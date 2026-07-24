import hmac
from typing import Annotated

from fastapi import Header, HTTPException

from app.config import settings


def verify_internal_token(provided: str | None, expected: str) -> None:
    if not provided or not hmac.compare_digest(provided, expected):
        raise HTTPException(status_code=401, detail="invalid internal token")


def require_internal_token(
    x_internal_token: Annotated[str | None, Header(alias="X-Internal-Token")] = None,
) -> None:
    verify_internal_token(x_internal_token, settings.internal_api_token)
