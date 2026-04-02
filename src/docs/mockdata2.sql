-- =============================================================
--  Knowra API — 목업 데이터 2
--  대상: TBL_USER(31~45) / TBL_USER_STNG / TBL_USER_TAG / TBL_USER_LGN_HSTRY
--        TBL_POST / TBL_POST_CMT / TBL_POST_LIKE / TBL_POST_SAVE / TBL_POST_TAG
--        TBL_POST_CMT_REACT / TBL_COMM_POST_CMT_REACT
--        TBL_BNR / TBL_ACS_IP / TBL_MENU
--  전제: TBL_USER SN 1~30, TBL_TAG SN 1~25,
--        TBL_COMM SN 1~15, TBL_COMM_POST SN 1~90,
--        TBL_COMM_POST_CMT SN 1~43 이미 존재
--  USER_SN=1 데이터는 신규 추가, USER_SN 2~30 미참조
-- =============================================================


-- ============================================================
-- 1. KNOWRA_USER.TBL_USER (신규 유저 SN 31~45)
--    비밀번호: 1111 → BCrypt 해시
-- ============================================================
USE KNOWRA_USER;

INSERT INTO TBL_USER (EMAIL, LOGIN_ID, PASSWORD, NAME, INTEREST, BIO, CREATR_SN) VALUES
('jungfullstack@gmail.com', 'jungfullstack', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '정풀스택',  '개발',     '프론트부터 백엔드까지 혼자 다 합니다. 사이드프로젝트 중독자.', 1), -- SN 31
('kimdata@naver.com',       'kimdata',       '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '김데이터',  'AI',       '데이터 사이언티스트. 숫자로 세상을 읽습니다.', 1),               -- SN 32
('leedesign@kakao.com',     'leedesign',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '이디자인',  '디자인',   'UX/UI 디자이너. 좋은 경험을 만드는 것이 목표입니다.', 1),        -- SN 33
('parkmarketer@gmail.com',  'parkmarketer',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '박마케터',  '창업',     '그로스 해커 출신 마케터. 데이터 기반 마케팅이 전문입니다.', 1), -- SN 34
('choifreelance@naver.com', 'choifreelance', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '최프리',    '개발',     '프리랜서 개발자 5년차. 재택근무와 자유가 최우선입니다.', 1),     -- SN 35
('hanblock@kakao.com',      'hanblock',      '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '한블록',    'AI',       '블록체인 개발자. Web3와 DeFi에 올인 중입니다.', 1),              -- SN 36
('yoonenglish@gmail.com',   'yoonenglish',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '윤영어',    '자기계발', '영어 강사 출신 콘텐츠 크리에이터. 영어로 기회를 만듭니다.', 1), -- SN 37
('ohpet@naver.com',         'ohpet',         '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '오펫',      '여행',     '반려동물과 함께하는 삶. 펫 여행 전문 블로거입니다.', 1),          -- SN 38
('shinmedical@kakao.com',   'shinmedical',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '신의사',    'AI',       '의료 AI 연구자. 기술로 의료의 미래를 바꾸고 싶습니다.', 1),      -- SN 39
('limcontent@gmail.com',    'limcontent',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '임콘텐츠',  '창업',     '유튜브 구독자 10만 크리에이터. 영상으로 사업합니다.', 1),        -- SN 40
('kangenv@naver.com',       'kangenv',       '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '강환경',    '자기계발', '환경 스타트업 대표. 지속가능한 비즈니스를 만듭니다.', 1),          -- SN 41
('baekstudent@kakao.com',   'baekstudent',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '백학생',    '개발',     'CS 전공 대학생. 취업 준비 중이고 공부 기록 남깁니다.', 1),        -- SN 42
('soncfo@gmail.com',        'soncfo',        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '손CFO',     '재테크',   '스타트업 CFO. 재무·회계·투자 전략이 전문입니다.', 1),             -- SN 43
('anhomelab@naver.com',     'anhomelab',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '안홈랩',    '개발',     '홈서버·자동화·IoT 덕후. 집을 스마트홈으로 만드는 중입니다.', 1), -- SN 44
('hwangvegan@kakao.com',    'hwangvegan',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '황비건',    '요리',     '비건 요리 연구가. 맛있고 건강한 식물성 식단을 연구합니다.', 1);  -- SN 45


-- ============================================================
-- 2. KNOWRA_USER.TBL_USER_STNG (USER_SN=1 및 31~45)
-- ============================================================

INSERT INTO TBL_USER_STNG (USER_SN, THEME_TYP, CMT_NTFCTN_YN, FLWR_NTFCTN_YN, LIKE_NTFCTN_YN, SYS_NTFCTN_YN, ACTVTN_YN, CREATR_SN) VALUES
(1,  'dark',   'Y','Y','Y','Y','Y', 1),
(31, 'dark',   'Y','Y','N','Y','Y', 31),
(32, 'light',  'Y','Y','Y','Y','Y', 32),
(33, 'system', 'Y','N','N','Y','Y', 33),
(34, 'light',  'Y','Y','Y','Y','Y', 34),
(35, 'dark',   'N','Y','N','Y','Y', 35),
(36, 'system', 'Y','Y','Y','N','Y', 36),
(37, 'light',  'Y','Y','N','Y','Y', 37),
(38, 'dark',   'Y','Y','Y','Y','Y', 38),
(39, 'system', 'Y','N','N','Y','Y', 39),
(40, 'light',  'Y','Y','Y','Y','Y', 40),
(41, 'dark',   'Y','Y','N','N','Y', 41),
(42, 'system', 'Y','Y','Y','Y','Y', 42),
(43, 'light',  'N','Y','N','Y','Y', 43),
(44, 'dark',   'Y','Y','Y','Y','Y', 44),
(45, 'system', 'Y','Y','N','Y','Y', 45);


-- ============================================================
-- 3. KNOWRA_USER.TBL_USER_TAG (USER_SN=1 및 31~45)
-- ============================================================

INSERT INTO TBL_USER_TAG (USER_SN, TAG_SN, USE_COUNT, CREATR_SN) VALUES
-- USER 1 (개발·AI·커리어·재테크)
(1, 1, 12, 1),(1, 2, 8, 1),(1, 17, 6, 1),(1, 22, 5, 1),
-- USER 31 (개발·사이드프로젝트·자동화)
(31, 1, 14, 31),(31, 6, 11, 31),(31, 20, 8, 31),
-- USER 32 (AI·개발·ChatGPT·자동화)
(32, 2, 17, 32),(32, 1, 12, 32),(32, 19, 14, 32),(32, 20, 9, 32),
-- USER 33 (사이드프로젝트)
(33, 6, 9, 33),
-- USER 34 (창업·사이드프로젝트·커리어)
(34, 5, 13, 34),(34, 6, 10, 34),(34, 17, 7, 34),
-- USER 35 (개발·커리어·이직)
(35, 1, 11, 35),(35, 17, 9, 35),(35, 18, 12, 35),
-- USER 36 (코인·AI·자동화)
(36, 3, 10, 36),(36, 2, 8, 36),(36, 20, 7, 36),
-- USER 37 (자기계발·독서·커리어)
(37, 23, 15, 37),(37, 16, 12, 37),(37, 17, 8, 37),
-- USER 38 (여행·사진)
(38, 7, 13, 38),(38, 13, 9, 38),
-- USER 39 (AI·ChatGPT·의료)
(39, 2, 16, 39),(39, 19, 13, 39),
-- USER 40 (창업·사이드프로젝트)
(40, 5, 14, 40),(40, 6, 11, 40),
-- USER 41 (개발·커리어·자기계발)
(41, 1, 8, 41),(41, 17, 6, 41),(41, 23, 5, 41),
-- USER 42 (재테크·투자·창업)
(42, 22, 14, 42),(42, 11, 11, 42),(42, 5, 8, 42),
-- USER 43 (홈서버·개발·자동화)
(43, 1, 7, 43),(43, 20, 9, 43),
-- USER 44 (요리·맛집)
(44, 21, 15, 44),(44, 8, 11, 44),
-- USER 45 (요리)
(45, 21, 18, 45);


-- ============================================================
-- 4. KNOWRA_USER.TBL_USER_LGN_HSTRY
-- ============================================================

INSERT INTO TBL_USER_LGN_HSTRY (USER_SN, LGN_DT, LGN_IP, CREATR_SN) VALUES
(1,  '2026-03-31 08:05:00', '112.188.0.1',  1),
(1,  '2026-03-30 09:22:00', '112.188.0.1',  1),
(1,  '2026-03-29 21:44:00', '210.94.0.50',  1),
(31, '2026-03-31 10:15:00', '59.11.0.33',   31),
(31, '2026-03-30 11:00:00', '59.11.0.33',   31),
(32, '2026-03-31 09:30:00', '1.233.0.7',    32),
(33, '2026-03-30 14:00:00', '125.180.0.12', 33),
(34, '2026-03-31 08:45:00', '211.207.0.4',  34),
(35, '2026-03-29 17:20:00', '58.140.0.21',  35),
(36, '2026-03-31 11:55:00', '175.200.0.9',  36),
(37, '2026-03-30 07:30:00', '61.78.0.5',    37),
(39, '2026-03-31 13:10:00', '203.229.0.16', 39),
(40, '2026-03-30 18:00:00', '180.92.0.3',   40),
(42, '2026-03-31 09:00:00', '121.130.0.22', 42),
(45, '2026-03-29 20:30:00', '110.8.0.14',   45);


-- ============================================================
-- 5. KNOWRA_POST.TBL_POST (일반 게시글 20개)
--    USER_SN=1 게시글 5건 포함 / 나머지 31~45 유저
--    SN 1~20 자동 부여
-- ============================================================
USE KNOWRA_POST;

INSERT INTO TBL_POST (POST_TYP, USER_SN, POST_TTL, POST_CNTNT, VIEW_CNT, LIKE_CNT, CMT_CNT, STAT, CREATR_SN) VALUES
-- SN 1 공지
('NOTICE', 1, '[공지] Knowra 일반 게시판 이용 안내',
 '광고·비방·무분별한 홍보는 삭제됩니다. 지식과 경험을 자유롭게 나눠요.',
 890, 12, 3, 'ACTIVE', 1),

-- SN 2~6 : USER 1 게시글
('NORMAL', 1, '개발자로 살면서 가장 잘한 결정 3가지',
 '커리어·학습·워라밸. 10년 차 개발자로서 돌아보면 이 세 가지 결정이 가장 큰 차이를 만들었습니다.',
 4230, 312, 47, 'ACTIVE', 1),
('NORMAL', 1, '매일 30분 글쓰기가 개발자 커리어에 미친 영향',
 'TIL 블로그를 1년간 운영하면서 면접에서 받은 혜택, 채용 담당자한테 연락 온 횟수, 실제 이직 성공까지 공유합니다.',
 5670, 445, 89, 'ACTIVE', 1),
('NORMAL', 1, '오픈소스 기여 처음 시작하는 방법 (완전 초보 가이드)',
 'Good First Issue 찾는 법, PR 올리는 절차, 리뷰 받는 요령. 첫 머지까지 걸린 시간과 배운 점 공유합니다.',
 3450, 267, 54, 'ACTIVE', 1),
('NORMAL', 1, '기술 면접 200번 보고 나서 느낀 것들',
 '합격·불합격 다 경험한 기술 면접 200번. 어떤 회사가 좋은 면접을 하는지, 어떤 준비가 실제로 통했는지 솔직하게.',
 6780, 534, 112, 'ACTIVE', 1),
('NORMAL', 1, '내가 쓰는 개발 도구 스택 2026 버전',
 'IDE·터미널·노트·브라우저·AI 보조 도구까지. 매년 바뀌는 스택 중 진짜 생산성에 도움된 것들만 골랐습니다.',
 2890, 198, 38, 'ACTIVE', 1),

-- SN 7~20 : 유저 31~45
('NORMAL', 31, '풀스택 혼자 개발해서 월 200 버는 방법',
 'Next.js + Spring Boot + AWS. 사이드프로젝트 3개 운영하면서 쌓은 아키텍처 결정들을 공유합니다.',
 3120, 243, 56, 'ACTIVE', 31),
('NORMAL', 32, 'ChatGPT API로 나만의 AI 도구 만드는 법 (비용 월 3천원)',
 'OpenAI API 호출 최적화, 프롬프트 캐싱, 스트리밍 응답. 토큰 절약하면서 쓸만한 AI 앱 만드는 법.',
 4560, 367, 78, 'ACTIVE', 32),
('NORMAL', 34, '그로스 해킹으로 앱 MAU 0→10만 만든 방법',
 'SEO·바이럴 루프·리텐션 개선. 광고비 0원으로 10만 MAU까지 간 실제 전략을 공개합니다.',
 5340, 423, 94, 'ACTIVE', 34),
('NORMAL', 35, '프리랜서 5년차가 말하는 장단점 총정리',
 '수입·자유·외로움·세금·건강보험. 회사 다니다 프리랜서 된 사람이 경험한 현실을 솔직하게.',
 4120, 334, 72, 'ACTIVE', 35),
('NORMAL', 36, '이더리움 스마트 컨트랙트 개발 입문 가이드',
 'Solidity 기초부터 Hardhat 테스트까지. 백엔드 개발자가 Web3 입문하면서 헤맸던 것들 정리.',
 2780, 189, 43, 'ACTIVE', 36),
('NORMAL', 37, '영어 공부 3년, 말하기 두렵지 않게 된 방법',
 '문법보다 입 훈련이 먼저였어요. 매일 15분 쉐도잉으로 어떻게 달라졌는지 데이터와 함께 공유합니다.',
 3890, 312, 67, 'ACTIVE', 37),
('NORMAL', 39, '의료 AI가 실제 병원에서 사용되는 방법',
 '영상 판독·처방 보조·환자 분류. 현직 의료 AI 연구자가 말하는 현실과 한계.',
 5120, 421, 94, 'ACTIVE', 39),
('NORMAL', 40, '유튜브 구독자 10만 가기까지 실제로 한 것들',
 '콘텐츠 기획·썸네일·알고리즘·협업. 2년간 올린 영상 300개에서 배운 것들을 압축합니다.',
 4890, 389, 83, 'ACTIVE', 40),
('NORMAL', 42, '스타트업 CFO가 말하는 런웨이 관리 실전',
 '캐시플로우 예측, 번 레이트 관리, 투자 라운드 타이밍. 실제 숫자로 보는 스타트업 재무 생존법.',
 3560, 278, 61, 'ACTIVE', 42),
('NORMAL', 43, '홈서버 구축으로 월 클라우드 비용 10만원 절약한 방법',
 'Proxmox + Docker + Nginx. 중고 미니PC 하나로 NAS·CI/CD·개인 서비스 돌리는 셋업 공유.',
 4120, 334, 72, 'ACTIVE', 43),
('NORMAL', 44, '집에서 식당 수준의 파스타 만드는 3가지 비밀',
 '면수·버터 마운팅·치즈의 비율. 레스토랑 셰프한테 직접 배운 것들을 가정 주방에 맞게 정리했습니다.',
 5670, 456, 98, 'ACTIVE', 44),
('NORMAL', 45, '비건 도시락 7일 챌린지 실제 비용과 포만감 후기',
 '고기 없이 하루 세 끼. 영양소 충족·맛·비용 모두 잡은 일주일 메뉴 전체 공개합니다.',
 2560, 178, 38, 'ACTIVE', 45),
('NORMAL', 41, '컴공 학부생이 인턴 합격하기까지 한 것들',
 '코딩 테스트·포트폴리오·자소서. 대기업 인턴 붙기 전에 실패한 것들도 솔직하게 씁니다.',
 3450, 267, 55, 'ACTIVE', 41),
('NORMAL', 38, '반려동물과 함께하는 국내 여행지 TOP 10',
 '펫 동반 가능한 숙소·카페·트레킹 코스. 직접 다 가봤어요. 강아지와 2년간 여행한 기록 공유합니다.',
 3120, 245, 52, 'ACTIVE', 38);


-- ============================================================
-- 6. KNOWRA_POST.TBL_POST_CMT (일반 게시글 댓글)
--    POST SN 2,3,4,5,7,8,12,13 에 달린 댓글
--    CMT SN 1~35 자동 부여
-- ============================================================

-- 게시글 2 (개발자 잘한 결정) → CMT 1~4
INSERT INTO TBL_POST_CMT (POST_SN, USER_SN, PRNT_CMT_SN, CMT_CNTNT, LIKE_CNT, CREATR_SN) VALUES
(2, 31, NULL, '워라밸 고르는 결정이 생산성에도 결국 플러스더라고요. 야근 많은 팀은 번아웃 이탈률이 높아요.', 34, 31),
(2, 32, 1,   '맞아요. 번아웃 후 회복 시간까지 합치면 장기적으로 무조건 손해예요.', 21, 32),
(2, 41, NULL, '10년차 관점에서 학습 방식에 대해 더 자세히 알 수 있을까요? 어떻게 공부하셨는지 궁금합니다.', 28, 41),
(2, 1,  3,   '책보다는 실제 코드 읽기, 공식 문서 우선, 그리고 작은 것이라도 직접 만들기를 반복했어요.', 45, 1);

-- 게시글 3 (글쓰기 커리어) → CMT 5~9
INSERT INTO TBL_POST_CMT (POST_SN, USER_SN, PRNT_CMT_SN, CMT_CNTNT, LIKE_CNT, CREATR_SN) VALUES
(3, 31, NULL, '저도 TIL 블로그 6개월째 하는데 링크드인 채용 DM이 실제로 늘었어요. 효과 확실합니다.', 45, 31),
(3, 37, 5,   '어떤 주제 위주로 쓰세요? 기술인지 커리어인지 둘 다인지 궁금해요.', 22, 37),
(3, 1, 6,    '주로 그날 배운 기술 TIL이랑 월간 회고 글이요. 꾸준함이 제일 중요한 것 같아요.', 18, 1),
(3, 34, NULL, '블로그 플랫폼은 어디 쓰세요? 벨로그 vs 티스토리 고민 중입니다.', 31, 34),
(3, 1,  8,   '벨로그 추천해요. 개발자 커뮤니티 있어서 초기 유입이 훨씬 잘 돼요.', 27, 1);

-- 게시글 4 (오픈소스 기여) → CMT 10~13
INSERT INTO TBL_POST_CMT (POST_SN, USER_SN, PRNT_CMT_SN, CMT_CNTNT, LIKE_CNT, CREATR_SN) VALUES
(4, 41, NULL, 'Good First Issue 이 키워드 처음 알았어요. 어떻게 찾으면 되나요?', 38, 41),
(4, 1,  10,  'github.com 에서 label:good-first-issue 검색하거나 goodfirstissue.dev 쓰면 돼요.', 45, 1),
(4, 31, NULL, 'PR 올리고 리뷰 받는 경험이 실제 협업 문화 적응에 정말 도움됐어요.', 29, 31),
(4, 32, NULL, '어떤 프로젝트부터 시작하면 좋을까요? 규모 작은 게 첫 머지 받기 쉽나요?', 23, 32);

-- 게시글 5 (기술 면접 200번) → CMT 14~18
INSERT INTO TBL_POST_CMT (POST_SN, USER_SN, PRNT_CMT_SN, CMT_CNTNT, LIKE_CNT, CREATR_SN) VALUES
(5, 35, NULL, '어떤 회사 면접 문화가 제일 인상 깊었나요?', 67, 35),
(5, 1,  14,  '당락 여부와 관계없이 상세한 피드백을 주는 회사들이요. 문화도 좋더라고요.', 52, 1),
(5, 42, NULL, '200번 중 합격률이 어느 정도인지 궁금해요.', 34, 42),
(5, 1,  16,  '약 30% 정도였어요. 준비 덜 된 상태에서 지원한 게 많아서요.', 28, 1),
(5, 41, NULL, '코딩 테스트랑 시스템 설계 중 어떤 게 더 준비하기 어려웠나요?', 41, 41);

-- 게시글 7 (풀스택 월 200) → CMT 19~22
INSERT INTO TBL_POST_CMT (POST_SN, USER_SN, PRNT_CMT_SN, CMT_CNTNT, LIKE_CNT, CREATR_SN) VALUES
(7, 1,  NULL, 'Next.js + Spring 조합이 사이드프로젝트에 딱이더라고요. AWS 비용 어떻게 관리하시나요?', 56, 1),
(7, 31, 19,  '초반엔 EC2 프리 티어, 트래픽 붙으면 Lambda + CloudFront로 바꿨어요. 고정비 거의 없어요.', 34, 31),
(7, 35, NULL, '사이드프로젝트 3개를 혼자 운영하면 유지보수가 벅차지 않나요?', 28, 35),
(7, 31, 21,  '자동화를 많이 해뒀어요. 모니터링·배포·정기 작업 전부 자동화하니까 주 2~3시간으로 돌아가요.', 23, 31);

-- 게시글 8 (ChatGPT API) → CMT 23~26
INSERT INTO TBL_POST_CMT (POST_SN, USER_SN, PRNT_CMT_SN, CMT_CNTNT, LIKE_CNT, CREATR_SN) VALUES
(8, 1,  NULL, '프롬프트 캐싱 쓰면 비용이 실제로 얼마나 줄어요? 비율이 궁금합니다.', 45, 1),
(8, 32, 23,  '동일 프롬프트 재사용 기준으로 최대 90% 가까이 줄더라고요. 캐시 히트율이 핵심이에요.', 38, 32),
(8, 39, NULL, '의료 쪽에서 GPT API 쓸 때 개인정보 이슈는 어떻게 처리하셨나요?', 31, 39),
(8, 32, 25,  '온프레미스 LLM 쓰거나 Azure OpenAI 계약하면 데이터 격리 보장돼요.', 27, 32);

-- 게시글 12 (의료 AI) → CMT 27~31
INSERT INTO TBL_POST_CMT (POST_SN, USER_SN, PRNT_CMT_SN, CMT_CNTNT, LIKE_CNT, CREATR_SN) VALUES
(12, 1,  NULL, '영상 판독 AI의 실제 정확도가 의사 수준에 도달했나요? 오진율이 궁금합니다.', 52, 1),
(12, 39, 27,  '특정 질환(폐암 CT 등)에서는 이미 전문의 수준이에요. 단, 일반화는 아직 갈 길이 멀어요.', 45, 39),
(12, 32, NULL, '의료 AI 데이터셋 구하는 게 제일 어렵다고 들었는데, 실제로 어떤가요?', 38, 32),
(12, 39, 29,  '맞아요. 라벨링 된 의료 데이터가 절대적으로 부족해요. 병원 협력이 없으면 거의 불가능합니다.', 34, 39),
(12, 44, NULL, '비의료인 입장에서 AI 진단 보조가 실제로 도움이 될 것 같아서 기대가 커요.', 22, 44);

-- 게시글 13 (유튜브 10만) → CMT 32~35
INSERT INTO TBL_POST_CMT (POST_SN, USER_SN, PRNT_CMT_SN, CMT_CNTNT, LIKE_CNT, CREATR_SN) VALUES
(13, 1,  NULL, '썸네일 A/B 테스트는 어떻게 하셨나요? 유튜브 스튜디오에서 가능한가요?', 48, 1),
(13, 40, 32,  '유튜브 스튜디오 실험 기능 쓰거나, 커뮤니티 탭 투표로 먼저 반응 봤어요.', 36, 40),
(13, 34, NULL, '알고리즘보다 리텐션이 결국 핵심이라는 걸 어느 순간 깨달았어요. 비슷한 경험이에요.', 41, 34),
(13, 41, NULL, '크리에이터 수익 구조가 어떻게 되는지 궁금해요. 유튜브 광고만으로는 힘들다고 하던데.', 29, 41);


-- ============================================================
-- 7. KNOWRA_POST.TBL_POST_LIKE (일반 게시글 좋아요)
-- ============================================================

-- 게시글 2
INSERT INTO TBL_POST_LIKE (POST_SN, USER_SN, LIKE_TYP, CREATR_SN) VALUES
(2, 31,'UP',31),(2, 32,'UP',32),(2, 37,'UP',37),(2, 41,'UP',41),(2, 35,'UP',35);

-- 게시글 3
INSERT INTO TBL_POST_LIKE (POST_SN, USER_SN, LIKE_TYP, CREATR_SN) VALUES
(3, 31,'UP',31),(3, 32,'UP',32),(3, 34,'UP',34),(3, 37,'UP',37),(3, 41,'UP',41),(3, 42,'UP',42);

-- 게시글 4
INSERT INTO TBL_POST_LIKE (POST_SN, USER_SN, LIKE_TYP, CREATR_SN) VALUES
(4, 31,'UP',31),(4, 32,'UP',32),(4, 35,'UP',35),(4, 41,'UP',41);

-- 게시글 5
INSERT INTO TBL_POST_LIKE (POST_SN, USER_SN, LIKE_TYP, CREATR_SN) VALUES
(5, 31,'UP',31),(5, 35,'UP',35),(5, 41,'UP',41),(5, 42,'UP',42),(5, 34,'UP',34),(5, 37,'UP',37);

-- 게시글 7
INSERT INTO TBL_POST_LIKE (POST_SN, USER_SN, LIKE_TYP, CREATR_SN) VALUES
(7, 1,'UP',1),(7, 32,'UP',32),(7, 34,'UP',34),(7, 35,'UP',35),(7, 43,'UP',43);

-- 게시글 8
INSERT INTO TBL_POST_LIKE (POST_SN, USER_SN, LIKE_TYP, CREATR_SN) VALUES
(8, 1,'UP',1),(8, 31,'UP',31),(8, 36,'UP',36),(8, 39,'UP',39),(8, 43,'UP',43);

-- 게시글 9 (그로스 해킹)
INSERT INTO TBL_POST_LIKE (POST_SN, USER_SN, LIKE_TYP, CREATR_SN) VALUES
(9, 1,'UP',1),(9, 31,'UP',31),(9, 40,'UP',40),(9, 42,'UP',42);

-- 게시글 10 (프리랜서)
INSERT INTO TBL_POST_LIKE (POST_SN, USER_SN, LIKE_TYP, CREATR_SN) VALUES
(10, 1,'UP',1),(10, 31,'UP',31),(10, 41,'UP',41),(10, 34,'UP',34);

-- 게시글 12 (의료 AI)
INSERT INTO TBL_POST_LIKE (POST_SN, USER_SN, LIKE_TYP, CREATR_SN) VALUES
(12, 1,'UP',1),(12, 32,'UP',32),(12, 36,'UP',36),(12, 44,'UP',44),(12, 45,'UP',45);

-- 게시글 13 (유튜브 10만)
INSERT INTO TBL_POST_LIKE (POST_SN, USER_SN, LIKE_TYP, CREATR_SN) VALUES
(13, 1,'UP',1),(13, 34,'UP',34),(13, 37,'UP',37),(13, 41,'UP',41),(13, 42,'UP',42);

-- 게시글 15 (홈서버)
INSERT INTO TBL_POST_LIKE (POST_SN, USER_SN, LIKE_TYP, CREATR_SN) VALUES
(15, 1,'UP',1),(15, 31,'UP',31),(15, 32,'UP',32),(15, 35,'UP',35),(15, 43,'UP',43);

-- 게시글 16 (파스타)
INSERT INTO TBL_POST_LIKE (POST_SN, USER_SN, LIKE_TYP, CREATR_SN) VALUES
(16, 1,'UP',1),(16, 38,'UP',38),(16, 45,'UP',45),(16, 44,'UP',44);


-- ============================================================
-- 8. KNOWRA_POST.TBL_POST_SAVE (게시글 저장)
-- ============================================================

INSERT INTO TBL_POST_SAVE (USER_SN, POST_SN, POST_KIND, CREATR_SN) VALUES
-- USER 1이 저장한 일반 게시글
(1, 7,  'POST', 1),
(1, 8,  'POST', 1),
(1, 12, 'POST', 1),
(1, 15, 'POST', 1),
-- USER 1이 저장한 커뮤니티 게시글 (SN 1~90 기존 데이터)
(1, 3,  'COMM', 1),
(1, 15, 'COMM', 1),
(1, 34, 'COMM', 1),
(1, 80, 'COMM', 1),
-- 신규 유저들이 저장한 일반 게시글
(31, 3,  'POST', 31),
(31, 5,  'POST', 31),
(32, 8,  'POST', 32),
(32, 12, 'POST', 32),
(34, 9,  'POST', 34),
(35, 10, 'POST', 35),
(35, 5,  'POST', 35),
(37, 3,  'POST', 37),
(41, 4,  'POST', 41),
(41, 5,  'POST', 41),
(42, 14, 'POST', 42),
-- 신규 유저들이 저장한 커뮤니티 게시글
(31, 5,  'COMM', 31),
(32, 11, 'COMM', 32),
(36, 15, 'COMM', 36),
(42, 80, 'COMM', 42),
(43, 10, 'COMM', 43);


-- ============================================================
-- 9. KNOWRA_POST.TBL_POST_TAG (일반 게시글 태그 매핑)
-- ============================================================

INSERT INTO TBL_POST_TAG (POST_SN, TAG_SN, CREATR_SN) VALUES
-- 게시글 2 (잘한 결정): #개발 #커리어
(2, 1, 1),(2, 17, 1),
-- 게시글 3 (글쓰기): #개발 #커리어 #사이드프로젝트
(3, 1, 1),(3, 17, 1),(3, 6, 1),
-- 게시글 4 (오픈소스): #개발
(4, 1, 1),
-- 게시글 5 (면접 200번): #개발 #커리어 #이직
(5, 1, 1),(5, 17, 1),(5, 18, 1),
-- 게시글 6 (개발 도구): #개발 #AI
(6, 1, 1),(6, 2, 1),
-- 게시글 7 (풀스택 월 200): #개발 #사이드프로젝트 #자동화
(7, 1, 31),(7, 6, 31),(7, 20, 31),
-- 게시글 8 (ChatGPT API): #AI #ChatGPT #자동화
(8, 2, 32),(8, 19, 32),(8, 20, 32),
-- 게시글 9 (그로스 해킹): #창업 #사이드프로젝트
(9, 5, 34),(9, 6, 34),
-- 게시글 10 (프리랜서): #개발 #커리어
(10, 1, 35),(10, 17, 35),
-- 게시글 11 (스마트 컨트랙트): #개발 #코인
(11, 1, 36),(11, 3, 36),
-- 게시글 12 (영어 공부): #자기계발
(12, 23, 37),
-- 게시글 13 (유튜브 10만): #창업 #사이드프로젝트
(13, 5, 40),(13, 6, 40),
-- 게시글 14 (스타트업 CFO): #창업 #재테크 #투자
(14, 5, 42),(14, 22, 42),(14, 11, 42),
-- 게시글 15 (홈서버): #개발 #자동화
(15, 1, 43),(15, 20, 43),
-- 게시글 16 (파스타): #요리
(16, 21, 44),
-- 게시글 17 (비건 도시락): #요리
(17, 21, 45),
-- 게시글 18 (컴공 인턴): #개발 #커리어
(18, 1, 41),(18, 17, 41),
-- 게시글 19 (반려동물 여행): #여행
(19, 7, 38),
-- 게시글 20 (의료 AI): #AI #ChatGPT
(20, 2, 39),(20, 19, 39);

-- ※ TBL_POST_TAG SN 오류 방지: 위 INSERT는 POST SN 기준으로
--   게시글 12(의료AI)가 실제로는 SN 12임에 주의
--   게시글 번호 재확인: SN 1=공지, 2~6=USER1, 7=31, 8=32, 9=34, 10=35,
--   11=36, 12=37, 13=39, 14=40, 15=42, 16=43, 17=44, 18=45, 19=41, 20=38


-- ============================================================
-- 10. KNOWRA_COMMUNITY.TBL_POST_CMT_REACT
--     일반 게시글 댓글(TBL_POST_CMT SN 1~35) 반응
--     USER_SN=1 및 31~45 유저
-- ============================================================
USE KNOWRA_COMMUNITY;

-- CMT 4 (게시글2 댓글: 직접 만들기 반복)
INSERT INTO TBL_POST_CMT_REACT (POST_CMT_SN, USER_SN, REACT_TYP, CREATR_SN) VALUES
(4, 31,'LIKE',31),(4, 32,'LIKE',32),(4, 41,'LIKE',41);

-- CMT 5 (게시글3 댓글: 링크드인 DM 늘었어요)
INSERT INTO TBL_POST_CMT_REACT (POST_CMT_SN, USER_SN, REACT_TYP, CREATR_SN) VALUES
(5, 1,'LIKE',1),(5, 34,'LIKE',34),(5, 37,'LIKE',37),(5, 42,'LIKE',42);

-- CMT 9 (게시글3 댓글: 벨로그 추천)
INSERT INTO TBL_POST_CMT_REACT (POST_CMT_SN, USER_SN, REACT_TYP, CREATR_SN) VALUES
(9, 31,'LIKE',31),(9, 34,'LIKE',34),(9, 41,'LIKE',41);

-- CMT 11 (게시글4 댓글: goodfirstissue 방법)
INSERT INTO TBL_POST_CMT_REACT (POST_CMT_SN, USER_SN, REACT_TYP, CREATR_SN) VALUES
(11, 31,'LIKE',31),(11, 32,'LIKE',32),(11, 34,'LIKE',34),(11, 41,'LIKE',41);

-- CMT 15 (게시글5 댓글: 피드백 주는 회사)
INSERT INTO TBL_POST_CMT_REACT (POST_CMT_SN, USER_SN, REACT_TYP, CREATR_SN) VALUES
(15, 35,'LIKE',35),(15, 42,'LIKE',42),(15, 41,'LIKE',41);

-- CMT 20 (게시글7 댓글: Lambda 비용 절약)
INSERT INTO TBL_POST_CMT_REACT (POST_CMT_SN, USER_SN, REACT_TYP, CREATR_SN) VALUES
(20, 1,'LIKE',1),(20, 32,'LIKE',32),(20, 35,'LIKE',35),(20, 43,'LIKE',43);

-- CMT 24 (게시글8 댓글: 캐시 90% 절감)
INSERT INTO TBL_POST_CMT_REACT (POST_CMT_SN, USER_SN, REACT_TYP, CREATR_SN) VALUES
(24, 1,'LIKE',1),(24, 31,'LIKE',31),(24, 39,'LIKE',39),(24, 43,'LIKE',43);

-- CMT 28 (게시글12 댓글: 전문의 수준 AI)
INSERT INTO TBL_POST_CMT_REACT (POST_CMT_SN, USER_SN, REACT_TYP, CREATR_SN) VALUES
(28, 1,'LIKE',1),(28, 32,'LIKE',32),(28, 44,'LIKE',44),(28, 45,'LIKE',45);


-- ============================================================
-- 11. KNOWRA_COMMUNITY.TBL_COMM_POST_CMT_REACT
--     기존 커뮤니티 게시글 댓글(SN 1~43)에
--     신규 유저 31~45가 반응
-- ============================================================

-- CMT 3 (사이드프로젝트 댓글 — 못 나가겠어요)
INSERT INTO TBL_COMM_POST_CMT_REACT (COMM_POST_CMT_SN, USER_SN, REACT_TYP, CREATR_SN) VALUES
(3, 31,'LIKE',31),(3, 34,'LIKE',34),(3, 40,'LIKE',40);

-- CMT 10 (GPT-5 댓글 — AI 두려워하는 사람)
INSERT INTO TBL_COMM_POST_CMT_REACT (COMM_POST_CMT_SN, USER_SN, REACT_TYP, CREATR_SN) VALUES
(10, 32,'LIKE',32),(10, 39,'LIKE',39),(10, 36,'LIKE',36),(10, 1,'LIKE',1);

-- CMT 16 (비트코인 댓글 — 2020년도 이 말)
INSERT INTO TBL_COMM_POST_CMT_REACT (COMM_POST_CMT_SN, USER_SN, REACT_TYP, CREATR_SN) VALUES
(16, 36,'HAHA',36),(16, 42,'HAHA',42),(16, 1,'LIKE',1);

-- CMT 25 (폐업 댓글 — 자산이에요)
INSERT INTO TBL_COMM_POST_CMT_REACT (COMM_POST_CMT_SN, USER_SN, REACT_TYP, CREATR_SN) VALUES
(25, 31,'LOVE',31),(25, 34,'LIKE',34),(25, 40,'LOVE',40),(25, 42,'LIKE',42);

-- CMT 35 (연봉협상 댓글 — 스크립트 감사)
INSERT INTO TBL_COMM_POST_CMT_REACT (COMM_POST_CMT_SN, USER_SN, REACT_TYP, CREATR_SN) VALUES
(35, 31,'LIKE',31),(35, 35,'LIKE',35),(35, 37,'LIKE',37),(35, 1,'LIKE',1);

-- CMT 38 (연봉협상 댓글 — HR 관점)
INSERT INTO TBL_COMM_POST_CMT_REACT (COMM_POST_CMT_SN, USER_SN, REACT_TYP, CREATR_SN) VALUES
(38, 31,'LIKE',31),(38, 34,'LIKE',34),(38, 35,'LIKE',35),(38, 42,'LIKE',42),(38, 1,'LIKE',1);

-- CMT 40 (순자산 10억 댓글 — 저축률 핵심)
INSERT INTO TBL_COMM_POST_CMT_REACT (COMM_POST_CMT_SN, USER_SN, REACT_TYP, CREATR_SN) VALUES
(40, 36,'LIKE',36),(40, 42,'LIKE',42),(40, 43,'LIKE',43),(40, 1,'LIKE',1);


-- ============================================================
-- 12. KNOWRA_CMS.TBL_BNR (배너)
-- ============================================================
USE KNOWRA_CMS;

INSERT INTO TBL_BNR (BNR_KND, BNR_TTL, BNR_URL_ADDR, START_DT, END_DT, ACTVTN_YN, CREATR_SN) VALUES
('MAIN_TOP',   'AI 시대, 개발자 커리어를 바꾸는 Knowra',         '/community/ai-lab',     '20260101', '20261231', 'Y', 1),
('MAIN_TOP',   '스타트업 창업자들의 생생한 이야기',               '/community/startup-hub', '20260301', '20261231', 'Y', 1),
('MAIN_MID',   '커리어 업! 연봉 협상부터 이직까지',              '/community/career-up',   '20260301', '20260630', 'Y', 1),
('MAIN_MID',   '투자 고수들의 재테크 전략 공개',                  '/community/rich-road',   '20260401', '20260930', 'Y', 1),
('SIDE_RIGHT', '신규 가입 이벤트 — 첫 글 작성 시 배지 지급',    '/event/newbie',           '20260101', '20260630', 'Y', 1),
('SIDE_RIGHT', '4월의 추천 커뮤니티: 그로스 랩',                 '/community/growth-lab',  '20260401', '20260430', 'Y', 1),
('POPUP',      '서비스 점검 안내 (4/5 새벽 2~4시)',               '/notice/maintenance',    '20260404', '20260405', 'Y', 1);


-- ============================================================
-- 13. KNOWRA_CMS.TBL_ACS_IP (관리자 접근 허용 IP)
-- ============================================================

INSERT INTO TBL_ACS_IP (IP_ADDR, PLCUS_NM, CREATR_SN) VALUES
('127.0.0.1',     '로컬 개발 환경',   1),
('10.0.0.0/8',    '사내 VPN',         1),
('192.168.1.100', '운영 서버 1',      1),
('192.168.1.101', '운영 서버 2',      1),
('203.0.113.10',  '관리자 사무실 IP', 1);


-- ============================================================
-- 14. SCHM_CMS.TBL_MENU (메뉴 구조)
-- ============================================================
USE SCHM_CMS;

INSERT INTO TBL_MENU (MENU_TYPE, BBS_SN, MENU_NAME, MENU_SORT_ORDER, MENU_PATH, APLCN_NTN_LTR, MENU_NM_PATH, MENU_WHOL_PATH, ACTVTN_YN, CREATR_SN) VALUES
('LINK',  NULL, '홈',            1, '/',                'KO', '홈',                    '/',                'Y', 1),
('LINK',  NULL, '피드',          2, '/feed',            'KO', '피드',                  '/feed',            'Y', 1),
('GROUP', NULL, '커뮤니티',      3, '/community',       'KO', '커뮤니티',              '/community',       'Y', 1),
('LINK',  NULL, '전체 커뮤니티', 1, '/community/list',  'KO', '커뮤니티 > 전체',       '/community/list',  'Y', 1),
('LINK',  NULL, '인기 커뮤니티', 2, '/community/hot',   'KO', '커뮤니티 > 인기',       '/community/hot',   'Y', 1),
('LINK',  NULL, '내 커뮤니티',   3, '/community/my',    'KO', '커뮤니티 > 내 것',      '/community/my',    'Y', 1),
('GROUP', NULL, '게시판',        4, '/post',            'KO', '게시판',                '/post',            'Y', 1),
('LINK',  NULL, '전체 글',       1, '/post/list',       'KO', '게시판 > 전체',          '/post/list',       'Y', 1),
('LINK',  NULL, '인기 글',       2, '/post/popular',    'KO', '게시판 > 인기',          '/post/popular',    'Y', 1),
('LINK',  NULL, '태그',          5, '/tag',             'KO', '태그',                  '/tag',             'Y', 1),
('GROUP', NULL, '관리자',       99, '/admin',           'KO', '관리자',                '/admin',           'Y', 1),
('LINK',  NULL, '유저 관리',     1, '/admin/user',      'KO', '관리자 > 유저',          '/admin/user',      'Y', 1),
('LINK',  NULL, '커뮤니티 관리', 2, '/admin/community', 'KO', '관리자 > 커뮤니티',     '/admin/community', 'Y', 1),
('LINK',  NULL, '게시글 관리',   3, '/admin/post',      'KO', '관리자 > 게시글',        '/admin/post',      'Y', 1),
('LINK',  NULL, '배너 관리',     4, '/admin/banner',    'KO', '관리자 > 배너',          '/admin/banner',    'Y', 1),
('LINK',  NULL, 'IP 관리',       5, '/admin/access-ip', 'KO', '관리자 > IP관리',        '/admin/access-ip', 'Y', 1),
('LINK',  NULL, '메뉴 관리',     6, '/admin/menu',      'KO', '관리자 > 메뉴',          '/admin/menu',      'Y', 1);