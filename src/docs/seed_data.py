"""
Knowra 자동 데이터 시딩 스크립트
- Gemini API(무료)로 콘텐츠 생성
- Knowra API 호출로 회원가입 / 커뮤니티 생성 / 게시글 / 댓글 자동 등록
---------------------------------------------------------
사전 설치:
    pip install requests google-generativeai

사용법:
    1. GEMINI_API_KEY 환경변수 설정 (https://aistudio.google.com)
       export GEMINI_API_KEY=AIza...
    2. BASE_URL을 실행 중인 서버 주소로 변경
    3. python seed_data.py
"""

import os
import json
import time
import random
import requests
import google.generativeai as genai

# ── 설정 ──────────────────────────────────────────────────────────────
BASE_URL = "http://localhost:8080"
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "YOUR_API_KEY_HERE")
DELAY = 1.0   # API 호출 간격(초) — Gemini 무료 분당 15 req 제한 대응

genai.configure(api_key=GEMINI_API_KEY)
model = genai.GenerativeModel("gemini-2.0-flash")

# ── 사용자 페르소나 정의 ───────────────────────────────────────────────
USERS = [
    {"loginId": "dev_alice",    "password": "1111", "email": "alice@test.com",   "name": "앨리스",   "persona": "7년차 백엔드 개발자. Java/Spring 전문. 사이드프로젝트 덕후."},
    {"loginId": "startup_bob",  "password": "1111", "email": "bob@test.com",     "name": "밥",       "persona": "스타트업 2번 창업 경험. 1번 엑시트, 1번 폐업. 현재 3번째 도전 중."},
    {"loginId": "ai_charlie",   "password": "1111", "email": "charlie@test.com", "name": "찰리",     "persona": "AI 스타트업 CTO. LLM 파인튜닝, MLOps 전문가."},
    {"loginId": "crypto_dana",  "password": "1111", "email": "dana@test.com",    "name": "다나",     "persona": "코인 트레이더 5년차. 선물·현물 병행. 손실도 봤지만 여전히 건재."},
    {"loginId": "travel_evan",  "password": "1111", "email": "evan@test.com",    "name": "에반",     "persona": "연 2회 해외여행. 배낭여행 선호. 동남아·유럽 다수 방문."},
    {"loginId": "fit_fiona",    "password": "1111", "email": "fiona@test.com",   "name": "피오나",   "persona": "헬스 10년차. 보디빌딩 대회 출전 경험. 식단 관리 철저."},
    {"loginId": "invest_george","password": "1111", "email": "george@test.com",  "name": "조지",     "persona": "부동산+주식 병행 투자. 갭투자 경험 다수."},
    {"loginId": "cook_hana",    "password": "1111", "email": "hana@test.com",    "name": "하나",     "persona": "집밥 유튜버. 한식·이탈리안 전문. 레시피 공유 즐김."},
]

# ── 커뮤니티 정의 ─────────────────────────────────────────────────────
COMMUNITIES = [
    {
        "commNm":     "auto-dev-life",
        "commDsplNm": "개발자 라이프",
        "commDesc":   "개발자의 일상, 커리어, 사이드프로젝트 이야기",
        "ctgrSn":     "1",
        "prvcyStng":  "public",
        "owner":      "dev_alice",
        "members":    ["startup_bob", "ai_charlie"],
        "topic":      "개발자 일상, 커리어, 사이드프로젝트, 코딩 팁",
        "tags":       ["#개발", "#커리어"],
    },
    {
        "commNm":     "auto-ai-lab",
        "commDsplNm": "AI 연구소",
        "commDesc":   "ChatGPT, LLM, AI 최신 트렌드 공유",
        "ctgrSn":     "1",
        "prvcyStng":  "public",
        "owner":      "ai_charlie",
        "members":    ["dev_alice", "startup_bob"],
        "topic":      "ChatGPT, LLM, AI 활용법, 자동화",
        "tags":       ["#AI", "#ChatGPT"],
    },
    {
        "commNm":     "auto-crypto",
        "commDsplNm": "코인토크",
        "commDesc":   "비트코인·알트코인 실전 투자 경험 공유",
        "ctgrSn":     "2",
        "prvcyStng":  "public",
        "owner":      "crypto_dana",
        "members":    ["invest_george"],
        "topic":      "비트코인, 알트코인, 트레이딩, 투자 전략",
        "tags":       ["#코인", "#투자"],
    },
    {
        "commNm":     "auto-healthy",
        "commDsplNm": "헬시라이프",
        "commDesc":   "운동·식단·수면으로 몸과 마음을 챙기는 커뮤니티",
        "ctgrSn":     "4",
        "prvcyStng":  "public",
        "owner":      "fit_fiona",
        "members":    ["travel_evan", "cook_hana"],
        "topic":      "헬스, 다이어트, 식단 관리, 운동 루틴",
        "tags":       ["#헬스", "#다이어트"],
    },
]

# ── 헬퍼 함수 ─────────────────────────────────────────────────────────
def api(method, path, token=None, **kwargs):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    resp = requests.request(method, BASE_URL + path, headers=headers, **kwargs)
    try:
        return resp.json()
    except Exception:
        return {"error": resp.text}


def gemini_generate(prompt: str) -> str:
    """Gemini API 호출 (무료 tier)"""
    time.sleep(DELAY)
    response = model.generate_content(prompt)
    return response.text


def parse_json_from_text(text: str) -> dict:
    """응답에서 JSON 블록 추출"""
    start = text.find("{")
    end = text.rfind("}") + 1
    if start == -1 or end == 0:
        raise ValueError(f"JSON not found in response:\n{text}")
    return json.loads(text[start:end])


# ── 단계별 실행 ───────────────────────────────────────────────────────
def step1_join_users() -> dict[str, str]:
    """회원가입 → {loginId: token} 반환"""
    print("\n[1] 회원가입")
    tokens = {}
    for user in USERS:
        result = api("POST", "/api/auth/join", json={
            "loginId":  user["loginId"],
            "password": user["password"],
            "email":    user["email"],
            "name":     user["name"],
        })
        if result.get("resultCode") == "200":
            tokens[user["loginId"]] = result["result"]["accessToken"]
            print(f"  ✓ {user['name']} ({user['loginId']})")
        else:
            # 이미 가입된 경우 로그인
            login_result = api("POST", "/api/auth/login", json={
                "loginId":  user["loginId"],
                "password": user["password"],
            })
            if login_result.get("resultCode") == "200":
                tokens[user["loginId"]] = login_result["result"]["accessToken"]
                print(f"  ↩ {user['name']} 이미 가입됨, 로그인 완료")
            else:
                print(f"  ✗ {user['name']} 실패: {result}")
    return tokens


def step2_create_communities(tokens: dict) -> dict[str, int]:
    """커뮤니티 생성 + 멤버 가입 → {commNm: commSn} 반환"""
    print("\n[2] 커뮤니티 생성")
    comm_sns = {}
    for comm in COMMUNITIES:
        owner_token = tokens.get(comm["owner"])
        if not owner_token:
            print(f"  ✗ {comm['commDsplNm']} — 오너 토큰 없음")
            continue

        # 커뮤니티 생성 (multipart/form-data)
        headers = {"Authorization": f"Bearer {owner_token}"}
        resp = requests.post(
            BASE_URL + "/api/community/setCommunity",
            headers=headers,
            data={
                "commNm":     comm["commNm"],
                "commDsplNm": comm["commDsplNm"],
                "commDesc":   comm["commDesc"],
                "ctgrSn":     comm["ctgrSn"],
                "prvcyStng":  comm["prvcyStng"],
            },
        )
        result = resp.json()
        if result.get("resultCode") == "200":
            comm_sn = result["result"]["commSn"]
            comm_sns[comm["commNm"]] = comm_sn
            print(f"  ✓ {comm['commDsplNm']} (SN={comm_sn})")
        else:
            print(f"  ✗ {comm['commDsplNm']}: {result}")
            continue

        # 멤버 가입
        for member_id in comm["members"]:
            member_token = tokens.get(member_id)
            if not member_token:
                continue
            join_result = api("POST", "/api/community/setMember",
                              token=member_token,
                              json={"commSn": comm_sn})
            if join_result.get("resultCode") == "200":
                print(f"    + {member_id} 가입")

    return comm_sns


def step3_create_posts(tokens: dict, comm_sns: dict) -> list[dict]:
    """게시글 AI 생성 + 등록 → [{commPostSn, commNm}] 반환"""
    print("\n[3] 게시글 생성 (Gemini)")
    created_posts = []

    for comm in COMMUNITIES:
        comm_sn = comm_sns.get(comm["commNm"])
        if not comm_sn:
            continue

        # 커뮤니티 멤버(오너 포함) 목록
        writers = [comm["owner"]] + comm["members"]

        # Gemini로 게시글 일괄 생성
        prompt = f"""
당신은 "{comm['commDsplNm']}" 커뮤니티의 활발한 멤버입니다.
커뮤니티 주제: {comm['topic']}

아래 {len(writers)}명의 페르소나에 맞는 게시글을 각각 1개씩 작성해주세요.
페르소나 목록:
{json.dumps([{"id": uid, "persona": next(u["persona"] for u in USERS if u["loginId"] == uid)} for uid in writers if any(u["loginId"] == uid for u in USERS)], ensure_ascii=False)}

요구사항:
- 제목: 사람들이 클릭하고 싶을 만큼 흥미롭게
- 본문: 300~600자, 구체적인 경험/정보 포함, 한국어
- 태그: {comm['tags']} 중 1~2개 선택

JSON 형식으로만 응답:
{{
  "posts": [
    {{
      "writerId": "loginId",
      "title": "제목",
      "content": "본문",
      "tags": ["#태그1"]
    }}
  ]
}}
"""
        try:
            raw = gemini_generate(prompt)
            data = parse_json_from_text(raw)
            posts = data.get("posts", [])
        except Exception as e:
            print(f"  ✗ {comm['commDsplNm']} 게시글 생성 실패: {e}")
            continue

        for post in posts:
            writer_token = tokens.get(post["writerId"])
            if not writer_token:
                continue
            result = api("POST", "/api/community/setCommPost",
                         token=writer_token,
                         json={
                             "commSn":    comm_sn,
                             "postTtl":   post["title"],
                             "postCntnt": post["content"],
                             "postTyp":   "NORMAL",
                             "tagNms":    post.get("tags", []),
                         })
            if result.get("resultCode") == "200":
                post_sn = result["result"]["commPostSn"]
                created_posts.append({"commPostSn": post_sn, "commNm": comm["commNm"]})
                print(f"  ✓ [{comm['commDsplNm']}] {post['title'][:30]}...")
            else:
                print(f"  ✗ 게시글 등록 실패: {result}")

    return created_posts


def step4_create_comments(tokens: dict, posts: list[dict]):
    """댓글 AI 생성 + 등록"""
    print("\n[4] 댓글 생성 (Gemini)")
    if not posts:
        print("  등록된 게시글 없음")
        return

    # 게시글당 랜덤 2~3명이 댓글
    all_login_ids = [u["loginId"] for u in USERS]

    for post in posts:
        comm_post_sn = post["commPostSn"]
        commenters = random.sample(all_login_ids, k=min(3, len(all_login_ids)))

        prompt = f"""
게시글 SN={comm_post_sn}에 댓글을 작성합니다.
아래 {len(commenters)}명이 각자 자연스러운 댓글을 1개씩 작성해주세요.
댓글은 게시글에 공감하거나 질문하거나 자신의 경험을 짧게 공유하는 내용으로 50~150자.

JSON 형식으로만 응답:
{{
  "comments": [
    {{
      "writerId": "loginId",
      "content": "댓글 내용",
      "isReply": false,
      "parentIdx": null
    }}
  ]
}}
loginId 목록: {commenters}
"""
        try:
            raw = gemini_generate(prompt)
            data = parse_json_from_text(raw)
            comments = data.get("comments", [])
        except Exception as e:
            print(f"  ✗ 댓글 생성 실패 (post={comm_post_sn}): {e}")
            continue

        parent_sn = None
        for i, cmt in enumerate(comments):
            writer_token = tokens.get(cmt["writerId"])
            if not writer_token:
                continue

            parent_cmt_sn = parent_sn if (cmt.get("isReply") and parent_sn) else None

            result = api("POST", "/api/community/setCommPostCmt",
                         token=writer_token,
                         json={
                             "commPostSn": comm_post_sn,
                             "cmtCntnt":   cmt["content"],
                             "prntCmtSn":  parent_cmt_sn,
                         })
            if result.get("resultCode") == "200":
                cmt_sn = result["result"].get("commPostCmtSn")
                if i == 0:
                    parent_sn = cmt_sn  # 첫 댓글을 대댓글 부모로
                print(f"  ✓ [post={comm_post_sn}] {cmt['writerId']}: {cmt['content'][:30]}...")
            else:
                print(f"  ✗ 댓글 등록 실패: {result}")


# ── 메인 ──────────────────────────────────────────────────────────────
if __name__ == "__main__":
    print("=" * 55)
    print("  Knowra 자동 데이터 시딩 시작")
    print("=" * 55)

    tokens     = step1_join_users()
    comm_sns   = step2_create_communities(tokens)
    posts      = step3_create_posts(tokens, comm_sns)
    step4_create_comments(tokens, posts)

    print("\n✅ 완료!")
    print(f"   사용자: {len(tokens)}명")
    print(f"   커뮤니티: {len(comm_sns)}개")
    print(f"   게시글: {len(posts)}개")
