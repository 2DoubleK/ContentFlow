import pytest
from fastapi import HTTPException

from app.internal_auth import verify_internal_token


def test_internal_token_accepts_exact_match():
    verify_internal_token("internal-token", "internal-token")


@pytest.mark.parametrize("provided", [None, "", "wrong-token"])
def test_internal_token_rejects_missing_or_incorrect_value(provided):
    with pytest.raises(HTTPException) as exception:
        verify_internal_token(provided, "internal-token")

    assert exception.value.status_code == 401
