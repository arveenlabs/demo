"""
LMS Student App Backend (FastAPI + MongoDB)
JWT auth, courses, lectures, quizzes, notifications, wishlist, progress.
"""
from datetime import datetime, timedelta, timezone
from os import getenv
from pathlib import Path
from secrets import token_urlsafe
from typing import List, Optional
import logging
import uuid

from fastapi import FastAPI, APIRouter, HTTPException, Depends, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import OAuth2PasswordBearer
from jose import jwt, JWTError
from motor.motor_asyncio import AsyncIOMotorClient
from pwdlib import PasswordHash
from pydantic import BaseModel, EmailStr, Field
from dotenv import load_dotenv
import razorpay
import hmac
import hashlib
import httpx

ROOT_DIR = Path(__file__).parent
load_dotenv(ROOT_DIR / ".env")

# ---------- Config ----------
MONGO_URL = getenv("MONGO_URL")
DB_NAME = getenv("DB_NAME", "lms_app")
JWT_SECRET = getenv("JWT_SECRET", "dev-secret-change-me-32chars-min-12345")
JWT_ALG = "HS256"
ACCESS_TTL_MIN = 60 * 24  # 1 day
REFRESH_TTL_DAYS = 30
OTP_TTL_MIN = 10

RAZORPAY_KEY_ID = getenv("RAZORPAY_KEY_ID", "rzp_test_placeholder")
RAZORPAY_KEY_SECRET = getenv("RAZORPAY_KEY_SECRET", "placeholder_secret")
RAZORPAY_WEBHOOK_SECRET = getenv("RAZORPAY_WEBHOOK_SECRET", "placeholder_webhook_secret")
_rzp_client = None
try:
    _rzp_client = razorpay.Client(auth=(RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET))
except Exception:
    _rzp_client = None

# ---------- Emergent Push (managed FCM/APNs relay) ----------
EMERGENT_PUSH_BASE_URL = "https://integrations.emergentagent.com"
EMERGENT_PUSH_KEY = getenv("EMERGENT_PUSH_KEY", "placeholder")
_push_client = httpx.AsyncClient(
    base_url=EMERGENT_PUSH_BASE_URL,
    headers={"X-Push-Key": EMERGENT_PUSH_KEY},
    timeout=10.0,
)


async def send_push(recipients: List[str], data: dict, idempotency_key: Optional[str] = None) -> None:
    """Send a push via the Emergent-managed SuprSend relay. Never blocks callers."""
    if not recipients:
        return
    if len(recipients) > 100:
        raise ValueError("max 100 recipients per push")
    if "title" not in data or "message" not in data:
        raise ValueError("data must include title and message")
    payload: dict = {"recipients": recipients, "data": data}
    if idempotency_key:
        payload["$idempotency_key"] = idempotency_key
    try:
        resp = await _push_client.post("/api/v1/push/trigger", json=payload)
        if resp.status_code >= 400:
            logging.getLogger("lms.push").warning(
                "push send returned %s: %s", resp.status_code, resp.text[:200]
            )
    except Exception as e:
        logging.getLogger("lms.push").warning("push send failed: %s", e)

pwd = PasswordHash.recommended()
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/auth/login")

# ---------- App ----------
app = FastAPI(title="LMS Student API")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

client = AsyncIOMotorClient(MONGO_URL)
db = client[DB_NAME]

api = APIRouter(prefix="/api")
auth_api = APIRouter(prefix="/api/auth", tags=["auth"])

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("lms")


# ---------- Models ----------
class SignupIn(BaseModel):
    email: EmailStr
    password: str
    name: str


class LoginIn(BaseModel):
    email: EmailStr
    password: str


class RefreshIn(BaseModel):
    refresh_token: str


class ForgotIn(BaseModel):
    email: EmailStr


class ResetIn(BaseModel):
    email: EmailStr
    otp: str
    new_password: str


class TokenOut(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class UserOut(BaseModel):
    id: str
    email: EmailStr
    name: str
    avatar: Optional[str] = None
    phone: Optional[str] = None
    is_admin: bool = False


class UpdateProfileIn(BaseModel):
    name: Optional[str] = None
    phone: Optional[str] = None
    avatar: Optional[str] = None


class ProgressIn(BaseModel):
    course_id: str
    lecture_id: str
    watched_seconds: int
    completed: bool = False


class QuizAnswerIn(BaseModel):
    question_id: str
    answer: List[str]  # array to support multi-answer


class QuizSubmitIn(BaseModel):
    quiz_id: str
    answers: List[QuizAnswerIn]
    time_taken_seconds: int


class CreateOrderIn(BaseModel):
    course_id: str


class VerifyPaymentIn(BaseModel):
    razorpay_order_id: str
    razorpay_payment_id: str
    razorpay_signature: str
    course_id: str


class AttendLiveIn(BaseModel):
    joined_at: Optional[str] = None


class RegisterPushIn(BaseModel):
    platform: str  # "android" | "ios"
    device_token: str


# ---------- Helpers ----------
def now_utc():
    return datetime.now(timezone.utc)


def create_token(sub: str, typ: str, ttl: timedelta, jti: str) -> str:
    exp = now_utc() + ttl
    payload = {"sub": sub, "type": typ, "jti": jti, "exp": exp}
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALG)


async def issue_tokens(user_id: str):
    a_jti, r_jti = token_urlsafe(12), token_urlsafe(12)
    access = create_token(user_id, "access", timedelta(minutes=ACCESS_TTL_MIN), a_jti)
    refresh = create_token(user_id, "refresh", timedelta(days=REFRESH_TTL_DAYS), r_jti)
    await db.users.update_one({"id": user_id}, {"$set": {"refresh_jti": r_jti}})
    return access, refresh


def user_public(u: dict) -> dict:
    return {
        "id": u["id"],
        "email": u["email"],
        "name": u.get("name", ""),
        "avatar": u.get("avatar"),
        "phone": u.get("phone"),
        "is_admin": bool(u.get("is_admin", False)),
    }


async def get_current_user(token: str = Depends(oauth2_scheme)) -> dict:
    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALG])
        if payload.get("type") != "access":
            raise JWTError("wrong type")
        user_id = payload["sub"]
    except JWTError:
        raise HTTPException(status_code=401, detail="Could not validate credentials")
    user = await db.users.find_one({"id": user_id}, {"_id": 0})
    if not user or user.get("disabled"):
        raise HTTPException(status_code=401, detail="User not found")
    return user


async def require_admin(current=Depends(get_current_user)) -> dict:
    if not current.get("is_admin"):
        raise HTTPException(status_code=403, detail="Admin access required")
    return current


# ---------- Auth Routes ----------
@auth_api.post("/signup", response_model=TokenOut)
async def signup(body: SignupIn):
    if await db.users.find_one({"email": body.email.lower()}):
        raise HTTPException(status_code=409, detail="Email already registered")
    user_id = str(uuid.uuid4())
    doc = {
        "id": user_id,
        "email": body.email.lower(),
        "name": body.name,
        "password_hash": pwd.hash(body.password),
        "avatar": None,
        "phone": None,
        "disabled": False,
        "created_at": now_utc().isoformat(),
    }
    await db.users.insert_one(doc)
    access, refresh = await issue_tokens(user_id)
    return TokenOut(access_token=access, refresh_token=refresh)


@auth_api.post("/login", response_model=TokenOut)
async def login(body: LoginIn):
    user = await db.users.find_one({"email": body.email.lower()})
    if not user or not pwd.verify(body.password, user["password_hash"]):
        raise HTTPException(status_code=401, detail="Incorrect email or password")
    access, refresh = await issue_tokens(user["id"])
    return TokenOut(access_token=access, refresh_token=refresh)


@auth_api.post("/refresh", response_model=TokenOut)
async def refresh(body: RefreshIn):
    try:
        payload = jwt.decode(body.refresh_token, JWT_SECRET, algorithms=[JWT_ALG])
        if payload.get("type") != "refresh":
            raise JWTError("bad type")
        user_id = payload["sub"]
        jti = payload["jti"]
    except JWTError:
        raise HTTPException(status_code=401, detail="Invalid refresh token")
    user = await db.users.find_one({"id": user_id, "refresh_jti": jti})
    if not user:
        raise HTTPException(status_code=401, detail="Refresh token revoked")
    access, refresh_t = await issue_tokens(user_id)
    return TokenOut(access_token=access, refresh_token=refresh_t)


@auth_api.post("/forgot-password")
async def forgot_password(body: ForgotIn):
    # In production, email the OTP. For demo we return it.
    import random
    otp = f"{random.randint(0, 999999):06d}"
    await db.users.update_one(
        {"email": body.email.lower()},
        {"$set": {
            "otp": otp,
            "otp_exp": (now_utc() + timedelta(minutes=OTP_TTL_MIN)).isoformat(),
        }},
    )
    return {"message": "OTP sent (demo)", "demo_otp": otp}


@auth_api.post("/reset-password")
async def reset_password(body: ResetIn):
    user = await db.users.find_one({"email": body.email.lower()})
    if not user or user.get("otp") != body.otp:
        raise HTTPException(status_code=400, detail="Invalid OTP")
    if datetime.fromisoformat(user["otp_exp"]) < now_utc():
        raise HTTPException(status_code=400, detail="OTP expired")
    await db.users.update_one(
        {"id": user["id"]},
        {"$set": {"password_hash": pwd.hash(body.new_password)},
         "$unset": {"otp": "", "otp_exp": "", "refresh_jti": ""}},
    )
    return {"message": "Password reset successful"}


@auth_api.post("/logout")
async def logout(current=Depends(get_current_user)):
    await db.users.update_one({"id": current["id"]}, {"$unset": {"refresh_jti": ""}})
    return {"message": "Logged out"}


@auth_api.get("/me", response_model=UserOut)
async def me(current=Depends(get_current_user)):
    return user_public(current)


@auth_api.patch("/me", response_model=UserOut)
async def update_me(body: UpdateProfileIn, current=Depends(get_current_user)):
    updates = {k: v for k, v in body.dict().items() if v is not None}
    if updates:
        await db.users.update_one({"id": current["id"]}, {"$set": updates})
    user = await db.users.find_one({"id": current["id"]})
    return user_public(user)


# ---------- Courses ----------
@api.get("/categories")
async def list_categories():
    cats = await db.categories.find({}, {"_id": 0}).to_list(100)
    return cats


@api.get("/courses")
async def list_courses(
    category: Optional[str] = None,
    q: Optional[str] = None,
    sort: str = "trending",  # trending | latest | rating
    limit: int = 50,
):
    query = {}
    if category and category.lower() != "all":
        query["category_id"] = category
    if q:
        query["$or"] = [
            {"title": {"$regex": q, "$options": "i"}},
            {"instructor": {"$regex": q, "$options": "i"}},
        ]
    sort_key = {"latest": "created_at", "rating": "rating", "trending": "students"}.get(sort, "students")
    cursor = db.courses.find(query, {"_id": 0}).sort(sort_key, -1).limit(limit)
    return await cursor.to_list(limit)


@api.get("/courses/recommended")
async def recommended_courses():
    return await db.courses.find({}, {"_id": 0}).sort("rating", -1).limit(6).to_list(6)


@api.get("/courses/trending")
async def trending_courses():
    return await db.courses.find({}, {"_id": 0}).sort("students", -1).limit(6).to_list(6)


@api.get("/courses/{course_id}")
async def get_course(course_id: str, current=Depends(get_current_user)):
    course = await db.courses.find_one({"id": course_id}, {"_id": 0})
    if not course:
        raise HTTPException(status_code=404, detail="Course not found")
    # Enrichment for the user
    enrolled = await db.enrollments.find_one({"user_id": current["id"], "course_id": course_id})
    wished = await db.wishlist.find_one({"user_id": current["id"], "course_id": course_id})
    lectures = await db.lectures.find({"course_id": course_id}, {"_id": 0}).sort("order", 1).to_list(500)
    # progress
    progresses = await db.progress.find(
        {"user_id": current["id"], "course_id": course_id}, {"_id": 0}
    ).to_list(1000)
    prog_map = {p["lecture_id"]: p for p in progresses}
    completed = sum(1 for p in progresses if p.get("completed"))
    total = len(lectures) or 1
    percent = round((completed / total) * 100)
    for lec in lectures:
        p = prog_map.get(lec["id"])
        lec["watched_seconds"] = p["watched_seconds"] if p else 0
        lec["completed"] = p["completed"] if p else False
    course["lectures"] = lectures
    course["enrolled"] = bool(enrolled)
    course["wishlisted"] = bool(wished)
    course["progress_percent"] = percent
    course["completed_lectures"] = completed
    course["total_lectures"] = len(lectures)
    return course


@api.post("/courses/{course_id}/enroll")
async def enroll_course(course_id: str, current=Depends(get_current_user)):
    course = await db.courses.find_one({"id": course_id})
    if not course:
        raise HTTPException(status_code=404, detail="Course not found")
    existing = await db.enrollments.find_one({"user_id": current["id"], "course_id": course_id})
    if existing:
        return {"message": "Already enrolled"}
    await db.enrollments.insert_one({
        "id": str(uuid.uuid4()),
        "user_id": current["id"],
        "course_id": course_id,
        "enrolled_at": now_utc().isoformat(),
    })
    await db.courses.update_one({"id": course_id}, {"$inc": {"students": 1}})
    return {"message": "Enrolled successfully"}


@api.get("/my/courses")
async def my_courses(current=Depends(get_current_user)):
    enrolls = await db.enrollments.find({"user_id": current["id"]}, {"_id": 0}).to_list(500)
    course_ids = [e["course_id"] for e in enrolls]
    if not course_ids:
        return []
    courses = await db.courses.find({"id": {"$in": course_ids}}, {"_id": 0}).to_list(500)
    # attach progress
    for c in courses:
        total = await db.lectures.count_documents({"course_id": c["id"]})
        completed = await db.progress.count_documents({
            "user_id": current["id"], "course_id": c["id"], "completed": True
        })
        c["progress_percent"] = round((completed / total) * 100) if total else 0
    return courses


# ---------- Wishlist ----------
@api.post("/wishlist/{course_id}")
async def add_wishlist(course_id: str, current=Depends(get_current_user)):
    existing = await db.wishlist.find_one({"user_id": current["id"], "course_id": course_id})
    if existing:
        return {"message": "Already in wishlist"}
    await db.wishlist.insert_one({
        "id": str(uuid.uuid4()),
        "user_id": current["id"],
        "course_id": course_id,
        "created_at": now_utc().isoformat(),
    })
    return {"message": "Added to wishlist"}


@api.delete("/wishlist/{course_id}")
async def remove_wishlist(course_id: str, current=Depends(get_current_user)):
    await db.wishlist.delete_one({"user_id": current["id"], "course_id": course_id})
    return {"message": "Removed from wishlist"}


@api.get("/wishlist")
async def get_wishlist(current=Depends(get_current_user)):
    items = await db.wishlist.find({"user_id": current["id"]}, {"_id": 0}).to_list(500)
    ids = [i["course_id"] for i in items]
    if not ids:
        return []
    return await db.courses.find({"id": {"$in": ids}}, {"_id": 0}).to_list(500)


# ---------- Lectures / Progress ----------
@api.get("/lectures/{lecture_id}")
async def get_lecture(lecture_id: str, current=Depends(get_current_user)):
    lec = await db.lectures.find_one({"id": lecture_id}, {"_id": 0})
    if not lec:
        raise HTTPException(status_code=404, detail="Lecture not found")
    p = await db.progress.find_one(
        {"user_id": current["id"], "lecture_id": lecture_id}, {"_id": 0}
    )
    lec["watched_seconds"] = p["watched_seconds"] if p else 0
    lec["completed"] = p["completed"] if p else False
    return lec


@api.post("/progress")
async def save_progress(body: ProgressIn, current=Depends(get_current_user)):
    await db.progress.update_one(
        {"user_id": current["id"], "lecture_id": body.lecture_id},
        {"$set": {
            "user_id": current["id"],
            "course_id": body.course_id,
            "lecture_id": body.lecture_id,
            "watched_seconds": body.watched_seconds,
            "completed": body.completed,
            "updated_at": now_utc().isoformat(),
        }},
        upsert=True,
    )
    return {"message": "saved"}


# ---------- Quiz ----------
@api.get("/courses/{course_id}/quizzes")
async def course_quizzes(course_id: str, current=Depends(get_current_user)):
    quizzes = await db.quizzes.find({"course_id": course_id}, {"_id": 0}).to_list(50)
    # Do not expose correct answers
    for q in quizzes:
        for question in q.get("questions", []):
            question.pop("correct", None)
    return quizzes


@api.get("/quizzes/{quiz_id}")
async def get_quiz(quiz_id: str, current=Depends(get_current_user)):
    quiz = await db.quizzes.find_one({"id": quiz_id}, {"_id": 0})
    if not quiz:
        raise HTTPException(status_code=404, detail="Quiz not found")
    for question in quiz.get("questions", []):
        question.pop("correct", None)
    return quiz


@api.post("/quizzes/submit")
async def submit_quiz(body: QuizSubmitIn, current=Depends(get_current_user)):
    quiz = await db.quizzes.find_one({"id": body.quiz_id}, {"_id": 0})
    if not quiz:
        raise HTTPException(status_code=404, detail="Quiz not found")
    ans_map = {a.question_id: a.answer for a in body.answers}
    correct = 0
    review = []
    for q in quiz["questions"]:
        user_ans = sorted([a.lower().strip() for a in ans_map.get(q["id"], [])])
        correct_ans = sorted([a.lower().strip() for a in q.get("correct", [])])
        is_correct = user_ans == correct_ans
        if is_correct:
            correct += 1
        review.append({
            "question_id": q["id"],
            "question": q["question"],
            "user_answer": ans_map.get(q["id"], []),
            "correct_answer": q.get("correct", []),
            "is_correct": is_correct,
        })
    total = len(quiz["questions"])
    score = round((correct / total) * 100) if total else 0
    result = {
        "id": str(uuid.uuid4()),
        "user_id": current["id"],
        "quiz_id": body.quiz_id,
        "course_id": quiz.get("course_id"),
        "score": score,
        "correct": correct,
        "total": total,
        "time_taken_seconds": body.time_taken_seconds,
        "submitted_at": now_utc().isoformat(),
        "review": review,
    }
    await db.quiz_results.insert_one(result.copy())
    result.pop("_id", None)
    return result


@api.get("/my/quiz-results")
async def my_quiz_results(current=Depends(get_current_user)):
    results = await db.quiz_results.find(
        {"user_id": current["id"]}, {"_id": 0}
    ).sort("submitted_at", -1).limit(50).to_list(50)
    return results


# ---------- Notifications ----------
@api.get("/notifications")
async def list_notifications(current=Depends(get_current_user)):
    items = await db.notifications.find(
        {"$or": [{"user_id": current["id"]}, {"broadcast": True}]}, {"_id": 0}
    ).sort("created_at", -1).limit(100).to_list(100)
    reads = await db.notification_reads.find(
        {"user_id": current["id"]}, {"_id": 0}
    ).to_list(500)
    read_ids = {r["notification_id"] for r in reads}
    for i in items:
        i["read"] = i["id"] in read_ids
    return items


@api.post("/notifications/{notif_id}/read")
async def mark_read(notif_id: str, current=Depends(get_current_user)):
    await db.notification_reads.update_one(
        {"user_id": current["id"], "notification_id": notif_id},
        {"$set": {"user_id": current["id"], "notification_id": notif_id,
                  "read_at": now_utc().isoformat()}},
        upsert=True,
    )
    return {"message": "marked read"}


# ---------- Dashboard ----------
@api.get("/dashboard")
async def dashboard(current=Depends(get_current_user)):
    # Continue learning (most recent progress)
    recent = await db.progress.find(
        {"user_id": current["id"]}, {"_id": 0}
    ).sort("updated_at", -1).limit(3).to_list(3)
    continue_courses = []
    for p in recent:
        c = await db.courses.find_one({"id": p["course_id"]}, {"_id": 0})
        if c:
            total = await db.lectures.count_documents({"course_id": c["id"]})
            completed = await db.progress.count_documents({
                "user_id": current["id"], "course_id": c["id"], "completed": True
            })
            c["progress_percent"] = round((completed / total) * 100) if total else 0
            c["last_lecture_id"] = p["lecture_id"]
            continue_courses.append(c)

    total_enrolled = await db.enrollments.count_documents({"user_id": current["id"]})
    completed_courses_count = 0
    enrolls = await db.enrollments.find({"user_id": current["id"]}).to_list(500)
    for e in enrolls:
        total = await db.lectures.count_documents({"course_id": e["course_id"]})
        completed = await db.progress.count_documents({
            "user_id": current["id"], "course_id": e["course_id"], "completed": True
        })
        if total > 0 and completed == total:
            completed_courses_count += 1

    quiz_count = await db.quiz_results.count_documents({"user_id": current["id"]})
    certificates_count = completed_courses_count

    # Upcoming live classes (only for enrolled courses)
    enrolled_ids = [e["course_id"] for e in enrolls]
    if enrolled_ids:
        live_query: dict = {
            "course_id": {"$in": enrolled_ids},
            "end_time": {"$gte": now_utc().isoformat()},
        }
        upcoming_live = await db.live_classes.find(live_query, {"_id": 0}).sort("start_time", 1).limit(3).to_list(3)
        for lc in upcoming_live:
            c = await db.courses.find_one({"id": lc["course_id"]}, {"_id": 0, "title": 1, "thumbnail": 1, "instructor": 1})
            lc["course"] = c
    else:
        upcoming_live = []

    # Weekly learning fabricated from progress updates
    week = []
    now = now_utc()
    for i in range(6, -1, -1):
        day = (now - timedelta(days=i)).date()
        count = await db.progress.count_documents({
            "user_id": current["id"],
            "updated_at": {"$regex": f"^{day.isoformat()}"},
        })
        week.append({"day": day.strftime("%a"), "minutes": count * 12})

    return {
        "continue_learning": continue_courses,
        "stats": {
            "enrolled": total_enrolled,
            "completed_courses": completed_courses_count,
            "certificates": certificates_count,
            "quizzes_taken": quiz_count,
        },
        "weekly_learning": week,
        "upcoming_live_classes": upcoming_live,
    }


# ---------- Payments (Razorpay) ----------
@api.get("/payments/config")
async def payment_config():
    return {"key_id": RAZORPAY_KEY_ID, "currency": "INR"}


@api.post("/payments/create-order")
async def create_order(body: CreateOrderIn, current=Depends(get_current_user)):
    course = await db.courses.find_one({"id": body.course_id}, {"_id": 0})
    if not course:
        raise HTTPException(status_code=404, detail="Course not found")
    already = await db.enrollments.find_one({"user_id": current["id"], "course_id": body.course_id})
    if already:
        raise HTTPException(status_code=400, detail="Already enrolled")
    price = float(course.get("discount_price") or course.get("price") or 0)
    if price <= 0:
        raise HTTPException(status_code=400, detail="Course is free — use enroll endpoint")
    amount_paise = int(round(price * 100))
    receipt = f"lum-{body.course_id[:12]}-{int(now_utc().timestamp())}"[:40]

    if _rzp_client and not RAZORPAY_KEY_ID.startswith("rzp_test_placeholder"):
        rzp_order = _rzp_client.order.create({
            "amount": amount_paise,
            "currency": "INR",
            "receipt": receipt,
            "payment_capture": 1,
            "notes": {"user_id": current["id"], "course_id": body.course_id},
        })
        order_id = rzp_order["id"]
    else:
        # Demo/mock mode when keys are placeholders — enables end-to-end flow for preview.
        order_id = f"order_demo_{uuid.uuid4().hex[:16]}"

    order_doc = {
        "id": order_id,
        "user_id": current["id"],
        "course_id": body.course_id,
        "amount": amount_paise,
        "currency": "INR",
        "receipt": receipt,
        "status": "created",
        "created_at": now_utc().isoformat(),
        "demo": _rzp_client is None or RAZORPAY_KEY_ID.startswith("rzp_test_placeholder"),
    }
    await db.orders.insert_one(order_doc.copy())
    return {
        "order_id": order_id,
        "amount": amount_paise,
        "currency": "INR",
        "receipt": receipt,
        "key_id": RAZORPAY_KEY_ID,
        "course_title": course["title"],
        "demo": order_doc["demo"],
    }


def _verify_rzp_signature(order_id: str, payment_id: str, signature: str) -> bool:
    msg = f"{order_id}|{payment_id}".encode()
    expected = hmac.new(
        RAZORPAY_KEY_SECRET.encode(), msg, hashlib.sha256
    ).hexdigest()
    return hmac.compare_digest(expected, signature)


@api.post("/payments/verify")
async def verify_payment(body: VerifyPaymentIn, current=Depends(get_current_user)):
    order = await db.orders.find_one({"id": body.razorpay_order_id, "user_id": current["id"]}, {"_id": 0})
    if not order:
        raise HTTPException(status_code=404, detail="Order not found")

    is_demo = order.get("demo")
    if is_demo:
        signature_ok = body.razorpay_signature == "demo_signature"
    else:
        signature_ok = _verify_rzp_signature(
            body.razorpay_order_id, body.razorpay_payment_id, body.razorpay_signature
        )

    if not signature_ok:
        await db.orders.update_one(
            {"id": body.razorpay_order_id},
            {"$set": {"status": "failed", "updated_at": now_utc().isoformat()}},
        )
        raise HTTPException(status_code=400, detail="Signature verification failed")

    await db.orders.update_one(
        {"id": body.razorpay_order_id},
        {"$set": {
            "status": "paid",
            "payment_id": body.razorpay_payment_id,
            "paid_at": now_utc().isoformat(),
        }},
    )
    # Enroll user
    existing = await db.enrollments.find_one({"user_id": current["id"], "course_id": body.course_id})
    if not existing:
        await db.enrollments.insert_one({
            "id": str(uuid.uuid4()),
            "user_id": current["id"],
            "course_id": body.course_id,
            "enrolled_at": now_utc().isoformat(),
            "paid": True,
            "order_id": body.razorpay_order_id,
            "amount": order["amount"],
        })
        await db.courses.update_one({"id": body.course_id}, {"$inc": {"students": 1}})

    return {"message": "Payment verified & course unlocked", "course_id": body.course_id}


@api.get("/payments/orders")
async def my_orders(current=Depends(get_current_user)):
    orders = await db.orders.find({"user_id": current["id"]}, {"_id": 0}).sort("created_at", -1).limit(50).to_list(50)
    for o in orders:
        c = await db.courses.find_one({"id": o["course_id"]}, {"_id": 0, "id": 1, "title": 1, "thumbnail": 1})
        o["course"] = c
    return orders


# ---------- Live Classes (Google Meet-based) ----------
@api.get("/live-classes")
async def list_live_classes(current=Depends(get_current_user), upcoming: bool = True):
    # Enrolled courses only — no enrollments means no visible live classes.
    enrolls = await db.enrollments.find({"user_id": current["id"]}, {"_id": 0}).to_list(500)
    enrolled_ids = [e["course_id"] for e in enrolls]
    if not enrolled_ids:
        return []
    query: dict = {"course_id": {"$in": enrolled_ids}}
    if upcoming:
        query["end_time"] = {"$gte": now_utc().isoformat()}
    items = await db.live_classes.find(query, {"_id": 0}).sort("start_time", 1).limit(50).to_list(50)
    # attach course info
    for it in items:
        c = await db.courses.find_one({"id": it["course_id"]}, {"_id": 0, "title": 1, "instructor": 1, "thumbnail": 1})
        it["course"] = c
    return items


@api.get("/live-classes/{class_id}")
async def get_live_class(class_id: str, current=Depends(get_current_user)):
    lc = await db.live_classes.find_one({"id": class_id}, {"_id": 0})
    if not lc:
        raise HTTPException(status_code=404, detail="Live class not found")
    # attach course + attendance flag
    c = await db.courses.find_one({"id": lc["course_id"]}, {"_id": 0, "title": 1, "instructor": 1, "thumbnail": 1})
    lc["course"] = c
    att = await db.live_attendance.find_one(
        {"user_id": current["id"], "live_class_id": class_id}, {"_id": 0}
    )
    lc["attended"] = bool(att)
    return lc


@api.post("/live-classes/{class_id}/attend")
async def attend_live(class_id: str, current=Depends(get_current_user)):
    lc = await db.live_classes.find_one({"id": class_id})
    if not lc:
        raise HTTPException(status_code=404, detail="Live class not found")
    await db.live_attendance.update_one(
        {"user_id": current["id"], "live_class_id": class_id},
        {"$set": {
            "user_id": current["id"],
            "live_class_id": class_id,
            "course_id": lc["course_id"],
            "joined_at": now_utc().isoformat(),
        }},
        upsert=True,
    )
    return {"message": "Attendance recorded", "meet_url": lc.get("meet_url")}


# ---------- Downloads Tracking ----------
class DownloadUpsertIn(BaseModel):
    lecture_id: str
    course_id: str
    size_bytes: int
    encrypted: bool = True


@api.get("/downloads")
async def list_downloads(current=Depends(get_current_user)):
    items = await db.downloads.find({"user_id": current["id"]}, {"_id": 0}).sort("downloaded_at", -1).to_list(500)
    for it in items:
        lec = await db.lectures.find_one({"id": it["lecture_id"]}, {"_id": 0, "title": 1, "type": 1, "duration_seconds": 1})
        crs = await db.courses.find_one({"id": it["course_id"]}, {"_id": 0, "title": 1, "thumbnail": 1})
        it["lecture"] = lec
        it["course"] = crs
    return items


@api.post("/downloads")
async def record_download(body: DownloadUpsertIn, current=Depends(get_current_user)):
    await db.downloads.update_one(
        {"user_id": current["id"], "lecture_id": body.lecture_id},
        {"$set": {
            "user_id": current["id"],
            "lecture_id": body.lecture_id,
            "course_id": body.course_id,
            "size_bytes": body.size_bytes,
            "encrypted": body.encrypted,
            "downloaded_at": now_utc().isoformat(),
        }},
        upsert=True,
    )
    return {"message": "recorded"}


@api.delete("/downloads/{lecture_id}")
async def delete_download(lecture_id: str, current=Depends(get_current_user)):
    await db.downloads.delete_one({"user_id": current["id"], "lecture_id": lecture_id})
    return {"message": "deleted"}


# ---------- Admin ----------
admin_api = APIRouter(prefix="/api/admin", tags=["admin"], dependencies=[Depends(require_admin)])


class AdminCourseIn(BaseModel):
    title: str
    instructor: str
    instructor_bio: Optional[str] = ""
    category_id: str
    category: Optional[str] = ""
    thumbnail: str
    banner: Optional[str] = None
    description: Optional[str] = ""
    duration_minutes: int = 0
    language: str = "English"
    level: str = "Beginner"
    price: float = 0
    discount_price: Optional[float] = None
    requirements: List[str] = []
    outcomes: List[str] = []
    faqs: List[dict] = []
    certificate: bool = True


class AdminLectureIn(BaseModel):
    course_id: str
    title: str
    type: str  # "video" | "pdf"
    url: str
    duration_seconds: int = 0
    order: int = 1
    description: Optional[str] = ""
    notes: Optional[str] = ""


class AdminQuizIn(BaseModel):
    course_id: str
    title: str
    duration_minutes: int = 10
    questions: List[dict]


class AdminLiveIn(BaseModel):
    course_id: str
    title: str
    description: Optional[str] = ""
    meet_url: str
    start_time: str
    end_time: str
    duration_minutes: int = 60
    instructor: str
    recording_url: Optional[str] = None


class AdminBroadcastIn(BaseModel):
    title: str
    body: str
    type: str = "announcement"
    user_id: Optional[str] = None  # None = broadcast to all


@admin_api.get("/stats")
async def admin_stats(_admin=Depends(require_admin)):
    return {
        "students": await db.users.count_documents({"is_admin": {"$ne": True}}),
        "courses": await db.courses.count_documents({}),
        "lectures": await db.lectures.count_documents({}),
        "quizzes": await db.quizzes.count_documents({}),
        "live_classes": await db.live_classes.count_documents({}),
        "enrollments": await db.enrollments.count_documents({}),
        "orders_paid": await db.orders.count_documents({"status": "paid"}),
        "revenue_paise": sum(
            [o.get("amount", 0) async for o in db.orders.find({"status": "paid"})]
        ),
    }


# Courses
@admin_api.post("/courses")
async def admin_create_course(body: AdminCourseIn):
    doc = body.dict()
    doc["id"] = f"crs-{uuid.uuid4().hex[:10]}"
    doc["rating"] = 0
    doc["students"] = 0
    doc["created_at"] = now_utc().isoformat()
    await db.courses.insert_one(doc.copy())
    doc.pop("_id", None)
    return doc


@admin_api.put("/courses/{course_id}")
async def admin_update_course(course_id: str, body: AdminCourseIn):
    updates = {k: v for k, v in body.dict().items() if v is not None}
    await db.courses.update_one({"id": course_id}, {"$set": updates})
    return await db.courses.find_one({"id": course_id}, {"_id": 0})


@admin_api.delete("/courses/{course_id}")
async def admin_delete_course(course_id: str):
    await db.courses.delete_one({"id": course_id})
    await db.lectures.delete_many({"course_id": course_id})
    await db.quizzes.delete_many({"course_id": course_id})
    await db.live_classes.delete_many({"course_id": course_id})
    await db.enrollments.delete_many({"course_id": course_id})
    return {"message": "deleted"}


@admin_api.get("/courses")
async def admin_list_courses():
    return await db.courses.find({}, {"_id": 0}).sort("created_at", -1).to_list(500)


# Lectures
@admin_api.get("/lectures")
async def admin_list_lectures(course_id: Optional[str] = None):
    q = {}
    if course_id:
        q["course_id"] = course_id
    return await db.lectures.find(q, {"_id": 0}).sort("order", 1).to_list(500)


@admin_api.post("/lectures")
async def admin_create_lecture(body: AdminLectureIn):
    doc = body.dict()
    doc["id"] = f"lec-{uuid.uuid4().hex[:10]}"
    await db.lectures.insert_one(doc.copy())
    doc.pop("_id", None)
    return doc


@admin_api.put("/lectures/{lecture_id}")
async def admin_update_lecture(lecture_id: str, body: AdminLectureIn):
    await db.lectures.update_one({"id": lecture_id}, {"$set": body.dict()})
    return await db.lectures.find_one({"id": lecture_id}, {"_id": 0})


@admin_api.delete("/lectures/{lecture_id}")
async def admin_delete_lecture(lecture_id: str):
    await db.lectures.delete_one({"id": lecture_id})
    return {"message": "deleted"}


# Quizzes
@admin_api.get("/quizzes")
async def admin_list_quizzes():
    return await db.quizzes.find({}, {"_id": 0}).to_list(500)


@admin_api.post("/quizzes")
async def admin_create_quiz(body: AdminQuizIn):
    doc = body.dict()
    doc["id"] = f"quiz-{uuid.uuid4().hex[:10]}"
    for q in doc.get("questions", []):
        q.setdefault("id", f"q-{uuid.uuid4().hex[:6]}")
    await db.quizzes.insert_one(doc.copy())
    doc.pop("_id", None)
    return doc


@admin_api.put("/quizzes/{quiz_id}")
async def admin_update_quiz(quiz_id: str, body: AdminQuizIn):
    doc = body.dict()
    for q in doc.get("questions", []):
        q.setdefault("id", f"q-{uuid.uuid4().hex[:6]}")
    await db.quizzes.update_one({"id": quiz_id}, {"$set": doc})
    return await db.quizzes.find_one({"id": quiz_id}, {"_id": 0})


@admin_api.delete("/quizzes/{quiz_id}")
async def admin_delete_quiz(quiz_id: str):
    await db.quizzes.delete_one({"id": quiz_id})
    return {"message": "deleted"}


# Students
@admin_api.get("/students")
async def admin_list_students(q: Optional[str] = None):
    query = {"is_admin": {"$ne": True}}
    if q:
        query["$or"] = [
            {"email": {"$regex": q, "$options": "i"}},
            {"name": {"$regex": q, "$options": "i"}},
        ]
    users = await db.users.find(query, {"_id": 0, "password_hash": 0}).sort("created_at", -1).limit(500).to_list(500)
    for u in users:
        u["enrollments_count"] = await db.enrollments.count_documents({"user_id": u["id"]})
    return users


@admin_api.get("/students/{user_id}")
async def admin_get_student(user_id: str):
    u = await db.users.find_one({"id": user_id}, {"_id": 0, "password_hash": 0})
    if not u:
        raise HTTPException(status_code=404, detail="Student not found")
    enrolls = await db.enrollments.find({"user_id": user_id}, {"_id": 0}).to_list(500)
    for e in enrolls:
        e["course"] = await db.courses.find_one({"id": e["course_id"]}, {"_id": 0, "title": 1, "thumbnail": 1})
    u["enrollments"] = enrolls
    u["quiz_results"] = await db.quiz_results.find({"user_id": user_id}, {"_id": 0}).sort("submitted_at", -1).limit(20).to_list(20)
    return u


@admin_api.patch("/students/{user_id}")
async def admin_update_student(user_id: str, body: dict):
    allowed = {k: v for k, v in body.items() if k in {"disabled", "name", "is_admin"}}
    if allowed:
        await db.users.update_one({"id": user_id}, {"$set": allowed})
    return await db.users.find_one({"id": user_id}, {"_id": 0, "password_hash": 0})


# Live classes
@admin_api.get("/live-classes")
async def admin_list_live():
    return await db.live_classes.find({}, {"_id": 0}).sort("start_time", 1).to_list(500)


@admin_api.post("/live-classes")
async def admin_create_live(body: AdminLiveIn):
    doc = body.dict()
    doc["id"] = f"live-{uuid.uuid4().hex[:10]}"
    await db.live_classes.insert_one(doc.copy())
    doc.pop("_id", None)
    return doc


@admin_api.put("/live-classes/{class_id}")
async def admin_update_live(class_id: str, body: AdminLiveIn):
    await db.live_classes.update_one({"id": class_id}, {"$set": body.dict()})
    return await db.live_classes.find_one({"id": class_id}, {"_id": 0})


@admin_api.delete("/live-classes/{class_id}")
async def admin_delete_live(class_id: str):
    await db.live_classes.delete_one({"id": class_id})
    return {"message": "deleted"}


# Broadcasts / Notifications
@admin_api.post("/broadcast")
async def admin_broadcast(body: AdminBroadcastIn):
    doc = {
        "id": str(uuid.uuid4()),
        "title": body.title,
        "body": body.body,
        "type": body.type,
        "broadcast": body.user_id is None,
        "user_id": body.user_id,
        "created_at": now_utc().isoformat(),
    }
    await db.notifications.insert_one(doc.copy())
    doc.pop("_id", None)
    return doc


@admin_api.get("/notifications")
async def admin_list_notifications():
    return await db.notifications.find({}, {"_id": 0}).sort("created_at", -1).limit(500).to_list(500)


@admin_api.delete("/notifications/{notif_id}")
async def admin_delete_notification(notif_id: str):
    await db.notifications.delete_one({"id": notif_id})
    return {"message": "deleted"}


# Orders / Revenue
@admin_api.get("/orders")
async def admin_list_orders(status: Optional[str] = None):
    q = {}
    if status:
        q["status"] = status
    orders = await db.orders.find(q, {"_id": 0}).sort("created_at", -1).limit(500).to_list(500)
    for o in orders:
        u = await db.users.find_one({"id": o["user_id"]}, {"_id": 0, "email": 1, "name": 1})
        c = await db.courses.find_one({"id": o["course_id"]}, {"_id": 0, "title": 1, "thumbnail": 1})
        o["user"] = u
        o["course"] = c
    return orders


# Categories (read for dropdowns)
@admin_api.get("/categories")
async def admin_list_categories():
    return await db.categories.find({}, {"_id": 0}).to_list(100)


# ---------- Root ----------
@api.get("/")
async def root():
    return {"message": "LMS Student API is running", "version": "1.0"}


app.include_router(auth_api)
app.include_router(api)
app.include_router(admin_api)


# ---------- Seed Data ----------
async def seed_live_classes():
    """Idempotent live-classes seed (added in iter 2)."""
    if await db.live_classes.count_documents({}) > 0:
        return
    live = [
        {
            "id": "live-rn-1",
            "course_id": "crs-react-native",
            "title": "Live Q&A: React Native + Expo Router",
            "description": "Ask anything about Expo Router and native modules.",
            "meet_url": "https://meet.google.com/lookup/lumina-rn-live",
            "start_time": (now_utc() + timedelta(hours=6)).isoformat(),
            "end_time": (now_utc() + timedelta(hours=7)).isoformat(),
            "duration_minutes": 60,
            "instructor": "Sarah Chen",
            "recording_url": None,
        },
        {
            "id": "live-ui-1",
            "course_id": "crs-ui-design",
            "title": "Design Critique Session",
            "description": "Bring your Figma files — we'll review them live.",
            "meet_url": "https://meet.google.com/lookup/lumina-ui-live",
            "start_time": (now_utc() + timedelta(days=1, hours=2)).isoformat(),
            "end_time": (now_utc() + timedelta(days=1, hours=3)).isoformat(),
            "duration_minutes": 60,
            "instructor": "Marcus Lee",
            "recording_url": None,
        },
        {
            "id": "live-py-1",
            "course_id": "crs-python-data",
            "title": "Pandas Workshop (Recording)",
            "description": "Recorded live workshop — watch anytime.",
            "meet_url": "https://meet.google.com/lookup/lumina-py-live",
            "start_time": (now_utc() - timedelta(days=2)).isoformat(),
            "end_time": (now_utc() - timedelta(days=2) + timedelta(hours=1)).isoformat(),
            "duration_minutes": 60,
            "instructor": "Dr. Priya Rao",
            "recording_url": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
        },
    ]
    await db.live_classes.insert_many([lc.copy() for lc in live])
    logger.info("Live classes seeded.")


async def seed_data():
    if await db.courses.count_documents({}) > 0:
        await seed_live_classes()
        return
    logger.info("Seeding LMS demo data...")

    categories = [
        {"id": "cat-dev", "name": "Development", "icon": "code"},
        {"id": "cat-design", "name": "Design", "icon": "palette"},
        {"id": "cat-data", "name": "Data Science", "icon": "chart-line"},
        {"id": "cat-biz", "name": "Business", "icon": "briefcase"},
        {"id": "cat-mkt", "name": "Marketing", "icon": "megaphone"},
        {"id": "cat-lang", "name": "Languages", "icon": "translate"},
    ]
    await db.categories.insert_many([c.copy() for c in categories])

    courses_data = [
        {
            "id": "crs-react-native",
            "title": "React Native Masterclass",
            "instructor": "Sarah Chen",
            "instructor_bio": "Senior mobile engineer at a Fortune 500 company with 10+ years of experience.",
            "category_id": "cat-dev",
            "category": "Development",
            "thumbnail": "https://images.pexels.com/photos/11035380/pexels-photo-11035380.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=800",
            "banner": "https://images.pexels.com/photos/11035380/pexels-photo-11035380.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=1200",
            "description": "Build production-ready mobile apps with React Native and Expo. Learn navigation, state management, native modules, and deployment.",
            "duration_minutes": 720,
            "language": "English",
            "level": "Intermediate",
            "rating": 4.8,
            "students": 12530,
            "price": 89.99,
            "discount_price": 39.99,
            "requirements": ["Basic JavaScript", "React fundamentals"],
            "outcomes": ["Build cross-platform apps", "Publish to App Store & Play Store", "Master Expo Router"],
            "faqs": [{"q": "Do I need a Mac?", "a": "No, Expo works on Windows, Mac, and Linux."}],
            "certificate": True,
            "created_at": now_utc().isoformat(),
        },
        {
            "id": "crs-ui-design",
            "title": "UI/UX Design Fundamentals",
            "instructor": "Marcus Lee",
            "instructor_bio": "Design lead who has shipped products used by millions.",
            "category_id": "cat-design",
            "category": "Design",
            "thumbnail": "https://images.pexels.com/photos/196644/pexels-photo-196644.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=800",
            "banner": "https://images.pexels.com/photos/196644/pexels-photo-196644.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=1200",
            "description": "Master the principles of great mobile UI/UX design — typography, color, spacing, and interaction.",
            "duration_minutes": 540,
            "language": "English",
            "level": "Beginner",
            "rating": 4.7,
            "students": 8420,
            "price": 69.99,
            "discount_price": 24.99,
            "requirements": ["No prior experience needed"],
            "outcomes": ["Design beautiful mobile apps", "Understand design systems", "Prototype in Figma"],
            "faqs": [{"q": "Is Figma required?", "a": "Optional — the free tier is plenty."}],
            "certificate": True,
            "created_at": now_utc().isoformat(),
        },
        {
            "id": "crs-python-data",
            "title": "Python for Data Science",
            "instructor": "Dr. Priya Rao",
            "instructor_bio": "PhD in Statistics, teaching data science for 8 years.",
            "category_id": "cat-data",
            "category": "Data Science",
            "thumbnail": "https://images.pexels.com/photos/1181671/pexels-photo-1181671.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=800",
            "banner": "https://images.pexels.com/photos/1181671/pexels-photo-1181671.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=1200",
            "description": "From zero to data hero. Learn pandas, numpy, matplotlib and build real machine-learning projects.",
            "duration_minutes": 900,
            "language": "English",
            "level": "Beginner",
            "rating": 4.9,
            "students": 21030,
            "price": 99.99,
            "discount_price": 44.99,
            "requirements": ["Basic Python is a plus"],
            "outcomes": ["Analyze data with pandas", "Build ML models", "Visualize insights"],
            "faqs": [],
            "certificate": True,
            "created_at": now_utc().isoformat(),
        },
        {
            "id": "crs-digital-marketing",
            "title": "Digital Marketing 2026",
            "instructor": "Alex Rivera",
            "instructor_bio": "Growth marketer for high-growth SaaS companies.",
            "category_id": "cat-mkt",
            "category": "Marketing",
            "thumbnail": "https://images.pexels.com/photos/265087/pexels-photo-265087.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=800",
            "banner": "https://images.pexels.com/photos/265087/pexels-photo-265087.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=1200",
            "description": "Master SEO, paid ads, content marketing, and analytics.",
            "duration_minutes": 480,
            "language": "English",
            "level": "Intermediate",
            "rating": 4.6,
            "students": 5210,
            "price": 79.99,
            "discount_price": 29.99,
            "requirements": ["None"],
            "outcomes": ["Rank on Google", "Run profitable ads", "Grow audiences"],
            "faqs": [],
            "certificate": True,
            "created_at": now_utc().isoformat(),
        },
        {
            "id": "crs-spanish",
            "title": "Spanish for Beginners",
            "instructor": "Isabella Martínez",
            "instructor_bio": "Certified language teacher from Madrid.",
            "category_id": "cat-lang",
            "category": "Languages",
            "thumbnail": "https://images.pexels.com/photos/207658/pexels-photo-207658.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=800",
            "banner": "https://images.pexels.com/photos/207658/pexels-photo-207658.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=1200",
            "description": "Learn conversational Spanish in 30 days.",
            "duration_minutes": 360,
            "language": "English",
            "level": "Beginner",
            "rating": 4.8,
            "students": 9840,
            "price": 59.99,
            "discount_price": 19.99,
            "requirements": ["Curiosity"],
            "outcomes": ["Hold basic conversations", "Understand grammar", "Travel with confidence"],
            "faqs": [],
            "certificate": True,
            "created_at": now_utc().isoformat(),
        },
        {
            "id": "crs-startup",
            "title": "Startup Fundamentals",
            "instructor": "Jordan Kim",
            "instructor_bio": "Founder of two successful startups.",
            "category_id": "cat-biz",
            "category": "Business",
            "thumbnail": "https://images.pexels.com/photos/3184291/pexels-photo-3184291.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=800",
            "banner": "https://images.pexels.com/photos/3184291/pexels-photo-3184291.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=1200",
            "description": "From idea to funded startup — the practical playbook.",
            "duration_minutes": 600,
            "language": "English",
            "level": "Advanced",
            "rating": 4.7,
            "students": 3120,
            "price": 129.99,
            "discount_price": 49.99,
            "requirements": ["Business curiosity"],
            "outcomes": ["Validate ideas", "Pitch investors", "Build MVPs"],
            "faqs": [],
            "certificate": True,
            "created_at": now_utc().isoformat(),
        },
    ]
    await db.courses.insert_many([c.copy() for c in courses_data])

    # Sample video (Big Buck Bunny), sample PDF (W3C)
    sample_video = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    sample_pdf = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"

    for course in courses_data:
        for i in range(1, 6):
            lec = {
                "id": f"lec-{course['id']}-{i}",
                "course_id": course["id"],
                "title": f"Lesson {i}: {course['title'].split()[0]} - Part {i}",
                "type": "video" if i % 2 == 1 else "pdf",
                "url": sample_video if i % 2 == 1 else sample_pdf,
                "duration_seconds": 596,
                "order": i,
                "description": f"In this lesson we cover key concepts of {course['title']}.",
                "notes": "Take notes and try the exercises at the end.",
            }
            await db.lectures.insert_one(lec)

    # Quizzes for a couple of courses
    quiz_rn = {
        "id": "quiz-react-native-1",
        "course_id": "crs-react-native",
        "title": "React Native Basics",
        "duration_minutes": 10,
        "questions": [
            {
                "id": "q1", "type": "mcq",
                "question": "Which company created React Native?",
                "options": ["Google", "Meta", "Microsoft", "Apple"],
                "correct": ["Meta"],
            },
            {
                "id": "q2", "type": "true_false",
                "question": "Expo Router uses file-based routing.",
                "options": ["True", "False"],
                "correct": ["True"],
            },
            {
                "id": "q3", "type": "multi",
                "question": "Which are core React Native components? (select all)",
                "options": ["View", "div", "Text", "span"],
                "correct": ["View", "Text"],
            },
            {
                "id": "q4", "type": "mcq",
                "question": "What is the entry file with Expo Router?",
                "options": ["index.js", "App.tsx", "app/_layout.tsx", "expo-router/entry"],
                "correct": ["expo-router/entry"],
            },
            {
                "id": "q5", "type": "fill",
                "question": "Which hook navigates imperatively? use___()",
                "options": [],
                "correct": ["router"],
            },
        ],
    }
    quiz_design = {
        "id": "quiz-ui-design-1",
        "course_id": "crs-ui-design",
        "title": "Design Foundations",
        "duration_minutes": 8,
        "questions": [
            {"id": "q1", "type": "mcq", "question": "Which is a design system?",
             "options": ["Material Design", "MP3", "TCP/IP", "HTML"],
             "correct": ["Material Design"]},
            {"id": "q2", "type": "true_false", "question": "8pt grid is a common spacing system.",
             "options": ["True", "False"], "correct": ["True"]},
            {"id": "q3", "type": "mcq", "question": "Minimum touch target on iOS?",
             "options": ["24px", "32px", "44px", "60px"], "correct": ["44px"]},
        ],
    }
    await db.quizzes.insert_many([quiz_rn.copy(), quiz_design.copy()])

    # Broadcast notifications
    notifs = [
        {"id": str(uuid.uuid4()), "title": "Welcome to Lumina Learn!",
         "body": "Discover thousands of courses to level up your skills.",
         "type": "announcement", "broadcast": True,
         "created_at": now_utc().isoformat()},
        {"id": str(uuid.uuid4()), "title": "New Course: React Native Masterclass",
         "body": "Sarah Chen just dropped a brand new course. Check it out!",
         "type": "course_update", "broadcast": True,
         "created_at": (now_utc() - timedelta(days=1)).isoformat()},
        {"id": str(uuid.uuid4()), "title": "Weekend Sale: 60% Off",
         "body": "Grab your favorite courses at up to 60% off this weekend only.",
         "type": "announcement", "broadcast": True,
         "created_at": (now_utc() - timedelta(days=2)).isoformat()},
    ]
    await db.notifications.insert_many([n.copy() for n in notifs])

    # Live classes (Google Meet URLs; recordings are optional)
    live = [
        {
            "id": "live-rn-1",
            "course_id": "crs-react-native",
            "title": "Live Q&A: React Native + Expo Router",
            "description": "Ask anything about Expo Router and native modules.",
            "meet_url": "https://meet.google.com/lookup/lumina-rn-live",
            "start_time": (now_utc() + timedelta(hours=6)).isoformat(),
            "end_time": (now_utc() + timedelta(hours=7)).isoformat(),
            "duration_minutes": 60,
            "instructor": "Sarah Chen",
            "recording_url": None,
        },
        {
            "id": "live-ui-1",
            "course_id": "crs-ui-design",
            "title": "Design Critique Session",
            "description": "Bring your Figma files — we'll review them live.",
            "meet_url": "https://meet.google.com/lookup/lumina-ui-live",
            "start_time": (now_utc() + timedelta(days=1, hours=2)).isoformat(),
            "end_time": (now_utc() + timedelta(days=1, hours=3)).isoformat(),
            "duration_minutes": 60,
            "instructor": "Marcus Lee",
            "recording_url": None,
        },
        {
            "id": "live-py-1",
            "course_id": "crs-python-data",
            "title": "Pandas Workshop (Recording)",
            "description": "Recorded live workshop — watch anytime.",
            "meet_url": "https://meet.google.com/lookup/lumina-py-live",
            "start_time": (now_utc() - timedelta(days=2)).isoformat(),
            "end_time": (now_utc() - timedelta(days=2) + timedelta(hours=1)).isoformat(),
            "duration_minutes": 60,
            "instructor": "Dr. Priya Rao",
            "recording_url": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
        },
    ]
    await db.live_classes.insert_many([lc.copy() for lc in live])
    logger.info("Seed complete.")


async def seed_admin():
    admin_email = "admin@lumina.com"
    existing = await db.users.find_one({"email": admin_email})
    if existing:
        if not existing.get("is_admin"):
            await db.users.update_one({"email": admin_email}, {"$set": {"is_admin": True}})
        return
    doc = {
        "id": str(uuid.uuid4()),
        "email": admin_email,
        "name": "Lumina Admin",
        "password_hash": pwd.hash("Admin1234"),
        "avatar": None,
        "phone": None,
        "disabled": False,
        "is_admin": True,
        "created_at": now_utc().isoformat(),
    }
    await db.users.insert_one(doc)
    logger.info("Bootstrap admin created: %s / Admin1234", admin_email)


@app.on_event("startup")
async def on_startup():
    await seed_data()
    await seed_admin()


@app.on_event("shutdown")
async def on_shutdown():
    client.close()
