import base64
import io
import os
import sys
import traceback
from typing import Optional

from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse
from PIL import Image
from openai import OpenAI

# NEW: load .env in this folder (so OPENAI_API_KEY/OPENAI_MODEL work)
from dotenv import load_dotenv
load_dotenv()

app = FastAPI(title="FlexiDraw Review Service")

MODEL = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")

try:
    if not OPENAI_API_KEY:
        raise RuntimeError("OPENAI_API_KEY environment variable is not set.")
    client = OpenAI(api_key=OPENAI_API_KEY)
except Exception as e:
    client = None
    print(f"[startup] OpenAI client not initialized: {e}", file=sys.stderr)

@app.get("/ping")
def ping():
    return {"ok": True, "model": MODEL, "has_api_key": bool(OPENAI_API_KEY)}

# NEW: the launcher will probe this; keep it simple 200 OK
@app.get("/healthz")
def healthz():
    return {"ok": True}

def ensure_png(data: bytes) -> bytes:
    try:
        im = Image.open(io.BytesIO(data))
        out = io.BytesIO()
        im.convert("RGBA").save(out, format="PNG")
        return out.getvalue()
    except Exception:
        return data

@app.post("/review")
async def review(image: UploadFile = File(...)):
    try:
        if client is None:
            return JSONResponse(
                status_code=500,
                content={"error": "OpenAI client not initialized. Check OPENAI_API_KEY."},
            )

        blob = await image.read()
        if not blob:
            return JSONResponse(status_code=400, content={"error": "Empty image upload."})

        png = ensure_png(blob)
        b64 = base64.b64encode(png).decode("ascii")
        data_url = f"data:image/png;base64,{b64}"

        user_content = [
            {"type": "text",
             "text": ("You are an art critic. Rate this drawing from 1–10 and provide "
                      "three strengths and three suggestions for improvement. "
                      "Be concise and kind. Return HTML with headings and bullet lists.")},
            {"type": "image_url", "image_url": {"url": data_url}},
        ]

        resp = client.chat.completions.create(
            model=MODEL,
            messages=[{"role": "user", "content": user_content}],
            temperature=0.5,
        )

        html: Optional[str] = None
        if resp.choices and resp.choices[0].message and resp.choices[0].message.content:
            html = resp.choices[0].message.content

        if not html:
            return JSONResponse(status_code=502, content={"error": "Model returned no content."})

        wrapped = f"""
        <div style="font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;line-height:1.45;font-size:14px;">
          {html}
        </div>
        """.strip()
        return {"html": wrapped}

    except Exception as e:
        traceback.print_exc(file=sys.stderr)
        return JSONResponse(status_code=500, content={"error": str(e)})