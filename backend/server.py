from fastapi import FastAPI, APIRouter, HTTPException
from fastapi.staticfiles import StaticFiles
from dotenv import load_dotenv
from starlette.middleware.cors import CORSMiddleware
from motor.motor_asyncio import AsyncIOMotorClient
import os
import logging
from pathlib import Path
from pydantic import BaseModel, Field
from typing import List, Literal
import uuid
from datetime import datetime
from fastapi.concurrency import run_in_threadpool

from downloads import DownloadAnalyzeError, analyze_playlist_formats, analyze_url
from download_queue import DOWNLOAD_ROOT, download_queue


ROOT_DIR = Path(__file__).parent
load_dotenv(ROOT_DIR / '.env')

# MongoDB connection
mongo_url = os.environ.get("MONGO_URL")
db_name = os.environ.get("DB_NAME")
client = AsyncIOMotorClient(mongo_url) if mongo_url and db_name else None
db = client[db_name] if client and db_name else None
memory_status_checks: list["StatusCheck"] = []

# Create the main app without a prefix
app = FastAPI()

# Create a router with the /api prefix
api_router = APIRouter(prefix="/api")


# Define Models
class StatusCheck(BaseModel):
    id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    client_name: str
    timestamp: datetime = Field(default_factory=datetime.utcnow)

class StatusCheckCreate(BaseModel):
    client_name: str


class DownloadAnalyzeRequest(BaseModel):
    url: str


class PlaylistFormatsRequest(BaseModel):
    url: str
    selected_entry_ids: List[str] = Field(default_factory=list)
    preferred_mode: Literal["audio", "video"] = "audio"


class DownloadQueueItemInput(BaseModel):
    entry_id: str
    title: str
    source: str = ""
    thumbnail: str | None = None
    original_url: str
    duration_seconds: int | None = None
    duration_label: str = "0:00"


class DownloadQueueEnqueueRequest(BaseModel):
    mode: Literal["audio", "video"]
    format_id: str
    format_label: str
    items: List[DownloadQueueItemInput] = Field(default_factory=list)


class DownloadQueueIdsRequest(BaseModel):
    entry_ids: List[str] = Field(default_factory=list)

# Add your routes to the router instead of directly to app
@api_router.get("/")
async def root():
    return {"message": "Hello World"}

@api_router.post("/status", response_model=StatusCheck)
async def create_status_check(input: StatusCheckCreate):
    status_dict = input.dict()
    status_obj = StatusCheck(**status_dict)
    if db is not None:
        _ = await db.status_checks.insert_one(status_obj.dict())
    else:
        memory_status_checks.append(status_obj)
    return status_obj

@api_router.get("/status", response_model=List[StatusCheck])
async def get_status_checks():
    if db is not None:
        status_checks = await db.status_checks.find().to_list(1000)
        return [StatusCheck(**status_check) for status_check in status_checks]
    return memory_status_checks


@api_router.post("/downloads/analyze")
async def analyze_download(input: DownloadAnalyzeRequest):
    try:
        return await run_in_threadpool(analyze_url, input.url)
    except DownloadAnalyzeError as error:
        raise HTTPException(status_code=400, detail={"code": error.code, "message": error.message}) from error


@api_router.post("/downloads/playlist-formats")
async def analyze_download_playlist_formats(input: PlaylistFormatsRequest):
    try:
        return await run_in_threadpool(analyze_playlist_formats, input.url, input.selected_entry_ids)
    except DownloadAnalyzeError as error:
        raise HTTPException(status_code=400, detail={"code": error.code, "message": error.message}) from error


@api_router.get("/downloads/queue")
async def get_download_queue():
    return await run_in_threadpool(download_queue.snapshot)


@api_router.post("/downloads/queue/enqueue")
async def enqueue_downloads(input: DownloadQueueEnqueueRequest):
    if not input.items:
        raise HTTPException(status_code=400, detail={"message": "No hay canciones para descargar."})
    return await run_in_threadpool(
        download_queue.enqueue,
        input.mode,
        input.format_id,
        input.format_label,
        [item.model_dump() for item in input.items],
    )


@api_router.post("/downloads/queue/remove")
async def remove_downloads(input: DownloadQueueIdsRequest):
    return await run_in_threadpool(download_queue.remove, input.entry_ids)


@api_router.post("/downloads/queue/reorder")
async def reorder_downloads(input: DownloadQueueIdsRequest):
    return await run_in_threadpool(download_queue.reorder, input.entry_ids)


@api_router.post("/downloads/queue/cancel-all")
async def cancel_all_downloads():
    return await run_in_threadpool(download_queue.cancel_all)

# Include the router in the main app
app.include_router(api_router)
app.mount("/media/downloads", StaticFiles(directory=str(DOWNLOAD_ROOT)), name="downloads-media")

app.add_middleware(
    CORSMiddleware,
    allow_credentials=True,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

@app.on_event("shutdown")
async def shutdown_db_client():
    if client is not None:
        client.close()
