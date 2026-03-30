"""Shared SlowAPI limiter for route decorators (see [main.create_app])."""

from slowapi import Limiter
from slowapi.util import get_remote_address

limiter = Limiter(key_func=get_remote_address)
