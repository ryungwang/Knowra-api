-- ============================================================
--  KNOWRA 목업 데이터 (확장판)
--  비밀번호: 1111 → BCrypt 해시
--  USER_SN=1 은 이미 존재하므로 제외 → SN 2~30 삽입
-- ============================================================

-- ============================================================
--  1. KNOWRA_USER.TBL_USER (29명 추가, SN 2~30)
-- ============================================================
USE KNOWRA_USER;

INSERT INTO TBL_USER (EMAIL, LOGIN_ID, PASSWORD, NAME, CREATR_SN) VALUES
('kimdev@gmail.com',       'kimdev',       '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '김개발',   1),  -- SN 2
('leestartup@naver.com',   'leestartup',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '이창업',   1),  -- SN 3
('parkai@kakao.com',       'parkai',       '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '박인공',   1),  -- SN 4
('choicrypto@gmail.com',   'choicrypto',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '최코인',   1),  -- SN 5
('jungphoto@naver.com',    'jungphoto',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '정사진',   1),  -- SN 6
('hantravel@kakao.com',    'hantravel',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '한여행',   1),  -- SN 7
('yoonfitness@gmail.com',  'yoonfitness',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '윤헬스',   1),  -- SN 8
('ohinvest@naver.com',     'ohinvest',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '오투자',   1),  -- SN 9
('shincook@kakao.com',     'shincook',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '신요리',   1),  -- SN 10
('limmusic@gmail.com',     'limmusic',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '임음악',   1),  -- SN 11
('kangsenior@naver.com',   'kangsenior',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '강시니어', 1),  -- SN 12
('baekpm@kakao.com',       'baekpm',       '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '백피엠',   1),  -- SN 13
('sondesign@gmail.com',    'sondesign',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '손디자인', 1),  -- SN 14
('anmarket@naver.com',     'anmarket',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '안마케터', 1),  -- SN 15
('hwangdata@kakao.com',    'hwangdata',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '황데이터', 1),  -- SN 16
('namwriter@gmail.com',    'namwriter',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '남작가',   1),  -- SN 17
('ohreader@naver.com',     'ohreader',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '오독서',   1),  -- SN 18
('junggame@kakao.com',     'junggame',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '정게임',   1),  -- SN 19
('kimrealty@gmail.com',    'kimrealty',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '김부동산', 1),  -- SN 20
('parkfinance@naver.com',  'parkfinance',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '박재테크', 1),  -- SN 21
('leeyoga@kakao.com',      'leeyoga',      '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '이요가',   1),  -- SN 22
('choivideo@gmail.com',    'choivideo',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '최유튜버', 1),  -- SN 23
('jeonrunner@naver.com',   'jeonrunner',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '전러너',   1),  -- SN 24
('limhiker@kakao.com',     'limhiker',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '임등산',   1),  -- SN 25
('kangchef@gmail.com',     'kangchef',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '강셰프',   1),  -- SN 26
('baekguitar@naver.com',   'baekguitar',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '백기타',   1),  -- SN 27
('sonphotog@kakao.com',    'sonphotog',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '손포토',   1),  -- SN 28
('anstartup2@gmail.com',   'anstartup2',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '안스타트업',1), -- SN 29
('hwangtrader@naver.com',  'hwangtrader',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHHa', '황트레이더',1); -- SN 30


-- ============================================================
--  2. KNOWRA_COM.TBL_TAG (25개)
-- ============================================================
USE KNOWRA_COM;

INSERT INTO TBL_TAG (TAG_NM, USE_COUNT, CREATR_SN) VALUES
('#개발',        25, 1),  -- SN 1
('#AI',          22, 1),  -- SN 2
('#코인',        18, 1),  -- SN 3
('#주식',        17, 1),  -- SN 4
('#창업',        21, 1),  -- SN 5
('#사이드프로젝트',14, 1), -- SN 6
('#여행',        19, 1),  -- SN 7
('#맛집',        12, 1),  -- SN 8
('#헬스',        16, 1),  -- SN 9
('#다이어트',    13, 1),  -- SN 10
('#투자',        20, 1),  -- SN 11
('#부동산',      15, 1),  -- SN 12
('#사진',        11, 1),  -- SN 13
('#음악',        10, 1),  -- SN 14
('#게임',        14, 1),  -- SN 15
('#독서',         9, 1),  -- SN 16
('#커리어',      18, 1),  -- SN 17
('#이직',        16, 1),  -- SN 18
('#ChatGPT',     20, 1),  -- SN 19
('#자동화',      12, 1),  -- SN 20
('#요리',        11, 1),  -- SN 21
('#재테크',      17, 1),  -- SN 22
('#자기계발',    13, 1),  -- SN 23
('#러닝',         8, 1),  -- SN 24
('#등산',         7, 1);  -- SN 25


-- ============================================================
--  3. KNOWRA_COMMUNITY.TBL_COMM (15개 커뮤니티, SN 1~15)
-- ============================================================
USE KNOWRA_COMMUNITY;

INSERT INTO TBL_COMM (COMM_NM, COMM_DSPL_NM, COMM_DESC, CTGR_SN, PRVCY_STNG, MEMBER_CNT, CREATR_SN) VALUES
('dev-life',      '개발자 라이프',   '개발자들의 일상, 커리어, 사이드프로젝트 이야기. 코딩 말고 삶도 나눠요.',        1, 'public', 6, 2),   -- COMM 1
('ai-lab',        'AI 연구소',       'ChatGPT·LLM·이미지 AI 최신 트렌드를 가장 빠르게 공유하는 곳.',                  1, 'public', 5, 4),   -- COMM 2
('crypto-talk',   '코인토크',        '비트코인·이더리움·알트코인 분석과 실전 투자 경험 공유.',                         2, 'public', 5, 5),   -- COMM 3
('startup-hub',   '스타트업 허브',   '아이디어 검증부터 투자 유치까지. 창업가들의 생생한 경험담.',                     3, 'public', 5, 3),   -- COMM 4
('healthy-life',  '헬시라이프',      '운동·식단·수면. 몸과 마음을 챙기는 커뮤니티.',                                  4, 'public', 5, 8),   -- COMM 5
('realty-talk',   '부동산 이야기',   '갭투자·전세·청약·재개발. 내 집 마련 실전 정보 공유.',                           2, 'public', 5, 20),  -- COMM 6
('gamers-gg',     '게이머스 GG',     '게임 공략, 티어, 신작 리뷰. 유일하게 랭크 올라가는 커뮤니티.',                  5, 'public', 5, 19), -- COMM 7
('book-club',     '독서 클럽',       '한 달에 책 한 권. 함께 읽고 삶이 달라진 사람들의 이야기.',                      5, 'public', 4, 18),  -- COMM 8
('travel-log',    '트래블 로그',     '배낭여행·호캉스·항공권 꿀팁. 가본 사람만 아는 여행 정보.',                      5, 'public', 5, 7),   -- COMM 9
('music-band',    '뮤직 & 밴드',     '악기, 작곡, 공연 후기. 음악 하는 사람들의 아지트.',                             5, 'public', 4, 11),  -- COMM 10
('photo-lens',    '포토 렌즈',       '카메라·구도·보정. 일상을 작품으로 만드는 사진가들의 모임.',                      5, 'public', 4, 6),   -- COMM 11
('cookers',       '쿡방 클럽',       '오늘 뭐 먹지? 레시피·맛집·요리 팁 다 여기 있어요.',                             5, 'public', 4, 10),  -- COMM 12
('career-up',     '커리어 업',       '이직·면접·연봉협상·퇴사 고민. 직장인이라면 한 번쯤 겪는 모든 것.',              1, 'public', 6, 12),  -- COMM 13
('rich-road',     '부자 되는 길',    '주식·ETF·배당·절세. 월급쟁이 재테크 실전 전략.',                                2, 'public', 5, 21),  -- COMM 14
('growth-lab',    '그로스 랩',       '독서·명상·습관·루틴. 어제보다 1% 나아지고 싶은 사람들.',                        5, 'public', 4, 1);   -- COMM 15


-- ============================================================
--  4. KNOWRA_COMMUNITY.TBL_COMM_MBR
-- ============================================================

-- COMM 1: 개발자 라이프 (6명)
INSERT INTO TBL_COMM_MBR (COMM_SN, USER_SN, ROLE, JOIN_TYP, STAT, CREATR_SN) VALUES
(1,2,'OWNER','AUTO','ACTIVE',2),(1,3,'MEMBER','AUTO','ACTIVE',3),
(1,4,'MEMBER','AUTO','ACTIVE',4),(1,12,'MEMBER','AUTO','ACTIVE',12),
(1,13,'MEMBER','AUTO','ACTIVE',13),(1,16,'MEMBER','AUTO','ACTIVE',16);

-- COMM 2: AI 연구소 (5명)
INSERT INTO TBL_COMM_MBR (COMM_SN, USER_SN, ROLE, JOIN_TYP, STAT, CREATR_SN) VALUES
(2,4,'OWNER','AUTO','ACTIVE',4),(2,2,'MEMBER','AUTO','ACTIVE',2),
(2,16,'MEMBER','AUTO','ACTIVE',16),(2,15,'MEMBER','AUTO','ACTIVE',15),
(2,23,'MEMBER','AUTO','ACTIVE',23);

-- COMM 3: 코인토크 (5명)
INSERT INTO TBL_COMM_MBR (COMM_SN, USER_SN, ROLE, JOIN_TYP, STAT, CREATR_SN) VALUES
(3,5,'OWNER','AUTO','ACTIVE',5),(3,9,'MEMBER','AUTO','ACTIVE',9),
(3,30,'MEMBER','AUTO','ACTIVE',30),(3,21,'MEMBER','AUTO','ACTIVE',21),
(3,3,'MEMBER','AUTO','ACTIVE',3);

-- COMM 4: 스타트업 허브 (5명)
INSERT INTO TBL_COMM_MBR (COMM_SN, USER_SN, ROLE, JOIN_TYP, STAT, CREATR_SN) VALUES
(4,3,'OWNER','AUTO','ACTIVE',3),(4,2,'MEMBER','AUTO','ACTIVE',2),
(4,29,'MEMBER','AUTO','ACTIVE',29),(4,13,'MEMBER','AUTO','ACTIVE',13),
(4,15,'MEMBER','AUTO','ACTIVE',15);

-- COMM 5: 헬시라이프 (5명)
INSERT INTO TBL_COMM_MBR (COMM_SN, USER_SN, ROLE, JOIN_TYP, STAT, CREATR_SN) VALUES
(5,8,'OWNER','AUTO','ACTIVE',8),(5,22,'MEMBER','AUTO','ACTIVE',22),
(5,24,'MEMBER','AUTO','ACTIVE',24),(5,7,'MEMBER','AUTO','ACTIVE',7),
(5,25,'MEMBER','AUTO','ACTIVE',25);

-- COMM 6: 부동산 이야기 (5명)
INSERT INTO TBL_COMM_MBR (COMM_SN, USER_SN, ROLE, JOIN_TYP, STAT, CREATR_SN) VALUES
(6,20,'OWNER','AUTO','ACTIVE',20),(6,9,'MEMBER','AUTO','ACTIVE',9),
(6,21,'MEMBER','AUTO','ACTIVE',21),(6,3,'MEMBER','AUTO','ACTIVE',3),
(6,1,'MEMBER','AUTO','ACTIVE',1);

-- COMM 7: 게이머스 GG (5명)
INSERT INTO TBL_COMM_MBR (COMM_SN, USER_SN, ROLE, JOIN_TYP, STAT, CREATR_SN) VALUES
(7,19,'OWNER','AUTO','ACTIVE',19),(7,2,'MEMBER','AUTO','ACTIVE',2),
(7,14,'MEMBER','AUTO','ACTIVE',14),(7,23,'MEMBER','AUTO','ACTIVE',23),
(7,16,'MEMBER','AUTO','ACTIVE',16);

-- COMM 8: 독서 클럽 (4명)
INSERT INTO TBL_COMM_MBR (COMM_SN, USER_SN, ROLE, JOIN_TYP, STAT, CREATR_SN) VALUES
(8,18,'OWNER','AUTO','ACTIVE',18),(8,17,'MEMBER','AUTO','ACTIVE',17),
(8,1,'MEMBER','AUTO','ACTIVE',1),(8,22,'MEMBER','AUTO','ACTIVE',22);

-- COMM 9: 트래블 로그 (5명)
INSERT INTO TBL_COMM_MBR (COMM_SN, USER_SN, ROLE, JOIN_TYP, STAT, CREATR_SN) VALUES
(9,7,'OWNER','AUTO','ACTIVE',7),(9,25,'MEMBER','AUTO','ACTIVE',25),
(9,6,'MEMBER','AUTO','ACTIVE',6),(9,23,'MEMBER','AUTO','ACTIVE',23),
(9,24,'MEMBER','AUTO','ACTIVE',24);

-- COMM 10: 뮤직 & 밴드 (4명)
INSERT INTO TBL_COMM_MBR (COMM_SN, USER_SN, ROLE, JOIN_TYP, STAT, CREATR_SN) VALUES
(10,11,'OWNER','AUTO','ACTIVE',11),(10,27,'MEMBER','AUTO','ACTIVE',27),
(10,17,'MEMBER','AUTO','ACTIVE',17),(10,14,'MEMBER','AUTO','ACTIVE',14);

-- COMM 11: 포토 렌즈 (4명)
INSERT INTO TBL_COMM_MBR (COMM_SN, USER_SN, ROLE, JOIN_TYP, STAT, CREATR_SN) VALUES
(11,6,'OWNER','AUTO','ACTIVE',6),(11,28,'MEMBER','AUTO','ACTIVE',28),
(11,23,'MEMBER','AUTO','ACTIVE',23),(11,7,'MEMBER','AUTO','ACTIVE',7);

-- COMM 12: 쿡방 클럽 (4명)
INSERT INTO TBL_COMM_MBR (COMM_SN, USER_SN, ROLE, JOIN_TYP, STAT, CREATR_SN) VALUES
(12,10,'OWNER','AUTO','ACTIVE',10),(12,26,'MEMBER','AUTO','ACTIVE',26),
(12,9,'MEMBER','AUTO','ACTIVE',9),(12,22,'MEMBER','AUTO','ACTIVE',22);

-- COMM 13: 커리어 업 (6명)
INSERT INTO TBL_COMM_MBR (COMM_SN, USER_SN, ROLE, JOIN_TYP, STAT, CREATR_SN) VALUES
(13,12,'OWNER','AUTO','ACTIVE',12),(13,2,'MEMBER','AUTO','ACTIVE',2),
(13,13,'MEMBER','AUTO','ACTIVE',13),(13,15,'MEMBER','AUTO','ACTIVE',15),
(13,3,'MEMBER','AUTO','ACTIVE',3),(13,14,'MEMBER','AUTO','ACTIVE',14);

-- COMM 14: 부자 되는 길 (5명)
INSERT INTO TBL_COMM_MBR (COMM_SN, USER_SN, ROLE, JOIN_TYP, STAT, CREATR_SN) VALUES
(14,21,'OWNER','AUTO','ACTIVE',21),(14,9,'MEMBER','AUTO','ACTIVE',9),
(14,5,'MEMBER','AUTO','ACTIVE',5),(14,20,'MEMBER','AUTO','ACTIVE',20),
(14,1,'MEMBER','AUTO','ACTIVE',1);

-- COMM 15: 그로스 랩 (4명)
INSERT INTO TBL_COMM_MBR (COMM_SN, USER_SN, ROLE, JOIN_TYP, STAT, CREATR_SN) VALUES
(15,1,'OWNER','AUTO','ACTIVE',1),(15,18,'MEMBER','AUTO','ACTIVE',18),
(15,22,'MEMBER','AUTO','ACTIVE',22),(15,17,'MEMBER','AUTO','ACTIVE',17);


-- ============================================================
--  5. KNOWRA_COMMUNITY.TBL_COMM_POST
--     커뮤니티당 6개 (NOTICE 1 + NORMAL 5), 총 90개
--     SN 흐름: COMM1=1~6, COMM2=7~12, COMM3=13~18, COMM4=19~24
--              COMM5=25~30, COMM6=31~36, COMM7=37~42, COMM8=43~48
--              COMM9=49~54, COMM10=55~60, COMM11=61~66, COMM12=67~72
--              COMM13=73~78, COMM14=79~84, COMM15=85~90
-- ============================================================

-- ── COMM 1: 개발자 라이프 (SN 1~6) ─────────────────────────
INSERT INTO TBL_COMM_POST (COMM_SN,POST_TYP,USER_SN,POST_TTL,POST_CNTNT,VIEW_CNT,LIKE_CNT,CMT_CNT,CREATR_SN) VALUES
(1,'NOTICE',2,'[공지] 커뮤니티 이용 규칙','비방·욕설·광고 금지. 즐거운 개발 이야기 나눠요!',310,4,2,2),
(1,'NORMAL',2,'연봉 1억 개발자, 진짜 가능한 이야기인가요?','주변에 신입 2년 만에 연봉 1억 찍었다는 사람 나왔습니다. 스톡옵션 포함이긴 한데... 5년차인 저는 아직 멀었거든요 ㅠ',1840,127,34,2),
(1,'NORMAL',3,'사이드 프로젝트로 월 300 버는 중인데 회사 때려칠까요?','퇴근 후 틈틈이 만든 크롬 익스텐션이 갑자기 ProductHunt 1위 먹었어요. 지금 월 300만원 정도 들어오는데 나와도 될까요?',3210,256,89,3),
(1,'NORMAL',4,'ChatGPT로 코드 짜면 실력이 늘까요 줄까요?','요즘 ChatGPT 없으면 코딩 못 하겠다는 분들 많은데, 저는 오히려 문제 해결 능력이 줄어드는 느낌이에요.',2560,198,67,4),
(1,'NORMAL',12,'비전공 6개월 독학으로 네카라쿠배 합격 후기','문과 출신 29살에 무작정 개발 공부 시작해서 6개월 만에 카카오 합격했습니다. 커리큘럼 다 공유할게요.',5670,489,124,12),
(1,'NORMAL',2,'스타트업 vs 대기업, 5년 다 다녀본 솔직 비교','스타트업 3년, 대기업 2년. 연봉·성장·워라밸·안정성 모두 다른 두 세계. 솔직하게 공유합니다.',2890,211,78,2);

-- ── COMM 2: AI 연구소 (SN 7~12) ────────────────────────────
INSERT INTO TBL_COMM_POST (COMM_SN,POST_TYP,USER_SN,POST_TTL,POST_CNTNT,VIEW_CNT,LIKE_CNT,CMT_CNT,CREATR_SN) VALUES
(2,'NOTICE',4,'[공지] AI 연구소 운영 방침','출처 없는 허위 정보는 삭제됩니다. 논문·실험 결과·프롬프트 팁 모두 환영.',280,8,2,4),
(2,'NORMAL',4,'GPT-5 나오면 개발자 직업 진짜 없어질까? 현직자 시각','GPT-4o 나왔을 때도 같은 말 나왔는데 설계·아키텍처 결정은 여전히 사람이 해요. 근데 GPT-5 스펙 보면... 솔직히 무서운 부분도 있습니다.',4120,312,98,4),
(2,'NORMAL',16,'Sora 영상 생성 AI 직접 써봤는데 충격 받은 이유','유튜브 썸네일, 광고 영상, 쇼츠 콘텐츠를 텍스트 몇 줄로 만들었어요. 크리에이터들한테 미칠 영향이 상상 이상입니다.',3780,287,76,16),
(2,'NORMAL',2,'프롬프트 엔지니어링 6개월 독학 핵심 정리 (무료 공유)','Chain of thought·Few-shot·RAG까지 실무에서 바로 쓸 수 있게 정리했어요. 노션 링크 공유합니다.',6230,521,143,2),
(2,'NORMAL',4,'AI가 내 일자리 빼앗기 전에 내가 AI를 활용하는 법','같은 직군에서도 AI 잘 쓰는 사람이 못 쓰는 사람 몫까지 가져가는 게 현실이에요.',5110,398,117,4),
(2,'NORMAL',23,'Claude vs ChatGPT vs Gemini 실무 사용 1년 비교','코딩·글쓰기·분석·창작 분야별로 어떤 AI가 진짜 잘하는지 솔직하게 비교합니다.',3450,264,88,23);

-- ── COMM 3: 코인토크 (SN 13~18) ────────────────────────────
INSERT INTO TBL_COMM_POST (COMM_SN,POST_TYP,USER_SN,POST_TTL,POST_CNTNT,VIEW_CNT,LIKE_CNT,CMT_CNT,CREATR_SN) VALUES
(3,'NOTICE',5,'[필독] 투자는 본인 판단으로, 이 글은 참고만','모든 투자 결정은 본인 책임입니다.',190,4,1,5),
(3,'NORMAL',5,'비트코인 10만 달러 돌파 직전, 지금 사도 늦지 않은 이유','온체인 데이터·기관 유입·반감기 사이클 분석해보면 지금도 초입입니다. 근거 데이터 공유할게요.',4560,234,89,5),
(3,'NORMAL',30,'코인으로 1억 잃고 배운 것들 (쓴소리 주의)','2021년 불장 때 영끌해서 다 날렸어요. FOMO·레버리지·리딩방... 제가 한 모든 실수 솔직하게 씁니다.',7820,612,201,30),
(3,'NORMAL',9,'이더리움 스테이킹 연 5% vs S&P500, 뭐가 나을까','리스크 대비 수익률. 변동성·세금·유동성까지 다 따져봤어요.',2340,178,54,9),
(3,'NORMAL',5,'알트코인 99% 망하는 이유 (생존하는 1% 조건)','지난 5년간 상장된 코인 추적해봤어요. 살아남는 프로젝트는 공통점이 있습니다.',3120,267,73,5),
(3,'NORMAL',30,'월급쟁이가 코인 투자하는 현실적인 방법 (DCA 전략)','월 50만원씩 2년 DCA 결과 공개합니다. 꾸준함이 답이에요.',2780,198,61,30);

-- ── COMM 4: 스타트업 허브 (SN 19~24) ───────────────────────
INSERT INTO TBL_COMM_POST (COMM_SN,POST_TYP,USER_SN,POST_TTL,POST_CNTNT,VIEW_CNT,LIKE_CNT,CMT_CNT,CREATR_SN) VALUES
(4,'NOTICE',3,'[공지] 스타트업 허브 소개 및 이용 안내','창업 경험·투자 유치·팀 빌딩 이야기를 나눠요.',210,6,2,3),
(4,'NORMAL',3,'아이디어 하나로 시드 투자 3억 받은 덱 공유합니다','PMF도 없고 팀도 2명이었는데 어떻게 투자 받았냐고요? 문제 정의가 전부였습니다.',5890,423,134,3),
(4,'NORMAL',29,'창업 3년, 폐업하면서 배운 것들','MAU 10만 찍고도 망할 수 있다는 거 아세요? 수익화 실패·번아웃·공동창업자 갈등 모두 공유합니다.',6340,512,178,29),
(4,'NORMAL',2,'VC가 절대 투자 안 하는 스타트업 특징 (현직 VC 내부 이야기)','아이디어보다 팀, 시장보다 타이밍. 왜 좋은 아이디어도 퇴짜 맞는지 공유합니다.',4780,367,112,2),
(4,'NORMAL',3,'직장 다니면서 창업 준비하는 현실적인 방법','언제 퇴사할지, 법인 설립 타이밍, 첫 고객 만들기까지 단계별로 정리했습니다.',3210,248,87,3),
(4,'NORMAL',29,'MVP 2주 만에 만들고 초기 고객 100명 모은 방법','노코드 툴만 썼습니다. Notion·Typeform·Zapier로 만든 MVP가 실제로 돈을 벌었어요.',2980,223,69,29);

-- ── COMM 5: 헬시라이프 (SN 25~30) ──────────────────────────
INSERT INTO TBL_COMM_POST (COMM_SN,POST_TYP,USER_SN,POST_TTL,POST_CNTNT,VIEW_CNT,LIKE_CNT,CMT_CNT,CREATR_SN) VALUES
(5,'NOTICE',8,'[공지] 헬시라이프 커뮤니티 운영 원칙','근거 없는 다이어트 정보·특정 제품 광고는 삭제합니다.',170,5,1,8),
(5,'NORMAL',8,'3개월 만에 체지방 15kg 뺀 방법 (의사한테 혼났지만)','극단적인 방법이라 추천은 못 하지만 실제로 한 것들 공유합니다.',4230,334,97,8),
(5,'NORMAL',22,'헬스 3년 했는데 근육이 안 느는 이유 찾았습니다','결국 문제는 단백질도 볼륨도 아니었어요. 수면이었습니다.',3560,278,81,22),
(5,'NORMAL',24,'직장인이 퇴근 후 30분으로 1년간 변한 몸 공개','맨몸 운동 30분, 1년 꾸준히 한 전후 사진과 루틴 공유합니다.',5120,421,138,24),
(5,'NORMAL',8,'하루 커피 3잔 마시면서 수면의 질 잡은 방법','마시는 타이밍만 바꿨는데 수면이 달라졌어요. 수면 트래커 데이터도 공유합니다.',2890,213,62,8),
(5,'NORMAL',22,'간헐적 단식 1년 결과, 진짜 효과 있을까?','16:8 방식 1년. 체중·혈당·집중력·소화 모두 추적했어요.',3140,242,73,22);

-- ── COMM 6: 부동산 이야기 (SN 31~36) ───────────────────────
INSERT INTO TBL_COMM_POST (COMM_SN,POST_TYP,USER_SN,POST_TTL,POST_CNTNT,VIEW_CNT,LIKE_CNT,CMT_CNT,CREATR_SN) VALUES
(6,'NOTICE',20,'[공지] 부동산 커뮤니티 이용 안내','허위 정보·투자 권유 목적 글은 삭제됩니다.',160,3,1,20),
(6,'NORMAL',20,'갭투자 3년 후 솔직 후기 (수익·손실 다 공개)','2021년에 2억 갭투자로 시작해서 지금 어떻게 됐는지 전부 공개합니다. 좋은 것만 말하지 않을게요.',5340,421,112,20),
(6,'NORMAL',9,'무주택자가 청약 당첨되기까지 10년의 기록','가점 올리는 법, 당첨 확률 높은 단지 고르는 법, 실거주 vs 투자 판단 기준까지.',4120,334,87,9),
(6,'NORMAL',20,'전세 사기 피하는 방법 (당한 사람이 알려주는 체크리스트)','저도 피해자입니다. 계약서 작성 전 반드시 확인해야 할 10가지 공유합니다.',6780,589,198,20),
(6,'NORMAL',21,'재개발 구역 투자, 진짜 돈 되는 지역 고르는 법','관리처분인가 단계별 투자 타이밍, 명도 리스크, 이주비 대출까지 정리했습니다.',3210,245,68,21),
(6,'NORMAL',1,'월세 받는 건물주 되기까지 직장인 7년의 투자 이력','월급 200 받으면서 어떻게 소형 아파트 3채까지 갔는지 연도별로 공개합니다.',4560,378,103,1);

-- ── COMM 7: 게이머스 GG (SN 37~42) ─────────────────────────
INSERT INTO TBL_COMM_POST (COMM_SN,POST_TYP,USER_SN,POST_TTL,POST_CNTNT,VIEW_CNT,LIKE_CNT,CMT_CNT,CREATR_SN) VALUES
(7,'NOTICE',19,'[공지] 게이머스 GG 이용 규칙','핵·버그 악용 정보는 금지. 건전한 게임 이야기 나눠요.',140,3,1,19),
(7,'NORMAL',19,'롤 챌린저 달성하고 느낀 것 (게임이 인생이었다)','다이아에서 챌린저까지 1년. 3000판 넘게 돌리면서 깨달은 멘탈·루틴·메타 분석.',4890,378,112,19),
(7,'NORMAL',2,'하루 2시간으로 랭크 올리는 방법 (직장인 게이머 가이드)','야근 없는 날 2시간. 효율적으로 실력 올리는 챔프 선택·복기 방법 공유합니다.',3120,234,67,2),
(7,'NORMAL',16,'게임 중독이라고 생각했는데 알고 보니 번아웃이었습니다','하루 10시간 게임하면서 현실 도피하던 시절 이야기. 솔직하게 씁니다.',5670,445,134,16),
(7,'NORMAL',19,'신작 게임 사기 전 꼭 확인해야 할 것들','스팀 환불 정책, 얼리 엑세스 함정, 리뷰 조작 판별법 총정리.',2780,198,54,19),
(7,'NORMAL',23,'e스포츠 선수 지망생이 알아야 할 현실 (전직 코치)','화려해 보이는 프로 게이머의 현실. 수입·수명·이후 커리어까지 솔직하게.',3450,267,78,23);

-- ── COMM 8: 독서 클럽 (SN 43~48) ───────────────────────────
INSERT INTO TBL_COMM_POST (COMM_SN,POST_TYP,USER_SN,POST_TTL,POST_CNTNT,VIEW_CNT,LIKE_CNT,CMT_CNT,CREATR_SN) VALUES
(8,'NOTICE',18,'[공지] 독서 클럽 이달의 책','이달의 책: 「아주 작은 습관의 힘」. 27일까지 후기 남겨주세요.',120,4,3,18),
(8,'NORMAL',18,'책 1000권 읽은 사람이 추천하는 인생책 10권','독서 편식 버리고 인문·과학·철학 넘나들며 읽은 책 중 삶이 달라진 책들.',3450,267,78,18),
(8,'NORMAL',17,'독서 기록 3년째 하는 방법 (노션 템플릿 공유)','읽은 책 기억하는 방법, 핵심 문장 정리법, 행동으로 연결하는 법 공유합니다.',2340,178,54,17),
(8,'NORMAL',18,'30대에 읽어야 할 재테크·경제 책 5권 (직접 효과 본 것만)','책 읽고 실제로 투자 방식이 바뀐 5권. 이론서 말고 실전서 위주로 골랐어요.',2890,212,61,18),
(8,'NORMAL',1,'소설 한 권이 번아웃을 치료했습니다','기술서만 읽다가 처음 소설 읽었을 때의 충격. 어떤 소설이었는지 후기 공유합니다.',1890,145,43,1),
(8,'NORMAL',17,'책 안 읽히는 사람을 위한 독서 습관 만들기','하루 10분부터 시작한 독서가 하루 2시간이 되기까지. 제가 한 방법 공유합니다.',2120,167,51,17);

-- ── COMM 9: 트래블 로그 (SN 49~54) ─────────────────────────
INSERT INTO TBL_COMM_POST (COMM_SN,POST_TYP,USER_SN,POST_TTL,POST_CNTNT,VIEW_CNT,LIKE_CNT,CMT_CNT,CREATR_SN) VALUES
(9,'NOTICE',7,'[공지] 트래블 로그 이용 안내','광고성 여행사 홍보글은 삭제됩니다.',130,3,1,7),
(9,'NORMAL',7,'배낭 하나로 동남아 3개월 살아본 이야기 (총비용 공개)','월 60만원으로 태국·베트남·캄보디아 3개월. 숙소·식비·교통비 전부 공개합니다.',4230,312,89,7),
(9,'NORMAL',25,'히말라야 트레킹 혼자 다녀온 후기 (EBC 14일)','에베레스트 베이스캠프 14일 일정·준비물·비용·위험 요소 전부 정리했어요.',3780,289,78,25),
(9,'NORMAL',7,'항공권 최저가 잡는 방법 완전 정복 (5년간 연구 결과)','언제 사야 가장 싼지, 어떤 사이트 써야 하는지, 마일리지 활용법까지.',5120,421,112,7),
(9,'NORMAL',6,'혼자 유럽 여행 한 달, 여자 혼자 가도 안전할까?','10개국 혼자 다닌 솔직 후기. 무서웠던 순간, 안전 팁, 추천 도시 공유합니다.',4670,378,103,6),
(9,'NORMAL',24,'국내 숨은 여행지 15곳 (관광객 없는 진짜 여행)','SNS에 안 나오는 곳들만 골랐어요. 지역민만 아는 풍경들입니다.',3120,245,67,24);

-- ── COMM 10: 뮤직 & 밴드 (SN 55~60) ────────────────────────
INSERT INTO TBL_COMM_POST (COMM_SN,POST_TYP,USER_SN,POST_TTL,POST_CNTNT,VIEW_CNT,LIKE_CNT,CMT_CNT,CREATR_SN) VALUES
(10,'NOTICE',11,'[공지] 뮤직 & 밴드 운영 안내','저작권 있는 악보·음원 무단 공유는 금지됩니다.',100,2,1,11),
(10,'NORMAL',11,'30살에 기타 독학 시작해서 공연까지 한 이야기','음악 지식 제로에서 1년 만에 홍대 공연까지. 독학 커리큘럼 공유합니다.',2340,178,54,11),
(10,'NORMAL',27,'밴드 하면서 배운 것들 (음악보다 사람이 문제)','악기보다 멤버 관리가 더 어렵더라고요. 밴드 운영 10년의 경험 공유합니다.',1890,145,43,27),
(10,'NORMAL',11,'집에서 홈레코딩 시작하는 방법 (50만원으로 세팅)','DAW·인터페이스·마이크. 최소 비용으로 꽤 쓸만한 홈스튜디오 만드는 법.',2120,167,51,11),
(10,'NORMAL',27,'유튜브 음악 채널 수익화까지 걸린 시간 (1년 기록)','구독자 0에서 수익화까지. 어떤 콘텐츠가 터졌는지 분석합니다.',2560,198,61,27),
(10,'NORMAL',17,'음악이 우울증 치료에 도움이 될까? 내 경험','약 대신 기타를 잡았습니다. 음악 치료의 효과를 직접 경험한 이야기.',3120,245,78,17);

-- ── COMM 11: 포토 렌즈 (SN 61~66) ──────────────────────────
INSERT INTO TBL_COMM_POST (COMM_SN,POST_TYP,USER_SN,POST_TTL,POST_CNTNT,VIEW_CNT,LIKE_CNT,CMT_CNT,CREATR_SN) VALUES
(11,'NOTICE',6,'[공지] 포토 렌즈 운영 방침','타인 사진 무단 게시·출처 미표기는 금지됩니다.',110,3,1,6),
(11,'NORMAL',6,'입문자가 카메라 사기 전에 꼭 알아야 할 것 (5년차 조언)','미러리스 vs DSLR, 화소 함정, 렌즈가 더 중요한 이유. 돈 낭비 안 하는 법.',3450,267,78,6),
(11,'NORMAL',28,'스마트폰으로 DSLR 같은 사진 찍는 방법','카메라 없어도 됩니다. 구도·빛·타이밍. 장비보다 눈이 먼저입니다.',4120,334,89,28),
(11,'NORMAL',6,'사진작가로 부업 월 100 버는 방법','결혼식·증명사진·상업 사진. 어떻게 첫 고객 만들었는지 공유합니다.',3780,289,78,6),
(11,'NORMAL',28,'Lightroom 보정 전후 비교 (무료 프리셋 공유)','같은 사진인데 보정하면 완전 달라져요. 제가 쓰는 프리셋 무료로 드립니다.',5120,421,112,28),
(11,'NORMAL',23,'필름 카메라로 돌아온 이유 (디지털 세대의 아날로그 경험)','일부러 느리게 찍는다는 게 무슨 의미인지 필름 사용 6개월 후기.',2340,178,54,23);

-- ── COMM 12: 쿡방 클럽 (SN 67~72) ──────────────────────────
INSERT INTO TBL_COMM_POST (COMM_SN,POST_TYP,USER_SN,POST_TTL,POST_CNTNT,VIEW_CNT,LIKE_CNT,CMT_CNT,CREATR_SN) VALUES
(12,'NOTICE',10,'[공지] 쿡방 클럽 이용 안내','상업 광고성 홍보글은 사전 허가 필요합니다.',100,2,1,10),
(12,'NORMAL',10,'요리 잘하는 사람들의 공통점 (셰프 10년이 알려주는 비밀)','레시피 외우는 게 아니에요. 비율과 원리를 이해하면 뭐든 만들 수 있습니다.',3120,245,67,10),
(12,'NORMAL',26,'1만원으로 4인 가족 한 끼 해결하는 레시피 5개','마트 세일 식재료로 만드는 진짜 맛있는 요리. 가성비 끝판왕.',4560,367,98,26),
(12,'NORMAL',10,'집에서 미슐랭 레스토랑 스테이크 굽는 방법','팬·온도·레스팅. 3가지만 지키면 집에서도 레스토랑 맛 납니다.',5670,456,123,10),
(12,'NORMAL',9,'혼자 사는 사람을 위한 밀프렙 주간 루틴','일요일 2시간으로 한 주 식사 해결하는 방법. 재료·레시피·보관법 공유.',3450,267,78,9),
(12,'NORMAL',26,'편의점 재료로 만드는 진짜 맛있는 요리 7가지','편의점 가면 뭔가 있긴 한데 뭘 해야 할지 모르겠다는 분들을 위해.',4120,334,89,26);

-- ── COMM 13: 커리어 업 (SN 73~78) ──────────────────────────
INSERT INTO TBL_COMM_POST (COMM_SN,POST_TYP,USER_SN,POST_TTL,POST_CNTNT,VIEW_CNT,LIKE_CNT,CMT_CNT,CREATR_SN) VALUES
(13,'NOTICE',12,'[공지] 커리어 업 이용 안내','특정 기업·개인 비방은 금지됩니다.',130,4,2,12),
(13,'NORMAL',12,'이직 협상에서 연봉 30% 올린 방법 (실전 대화 스크립트)','연봉 협상은 기술입니다. 제가 실제로 한 말, 반박에 어떻게 대응했는지 전부 공개합니다.',6780,534,167,12),
(13,'NORMAL',3,'나쁜 상사 때문에 퇴사하기 전에 해볼 것들','관계 개선 시도, 인사팀 활용, 그래도 안 될 때 퇴사하는 타이밍. 경험 공유합니다.',5340,421,134,3),
(13,'NORMAL',13,'면접관이 싫어하는 대답 TOP 10 (HR 담당자 관점)','면접 1000번 이상 본 HR 담당자가 실제로 탈락 결정한 이유들.',4890,389,112,13),
(13,'NORMAL',12,'연봉 1억 받는 사람들의 공통점 (데이터 분석)','링크드인 데이터, 채용 공고 분석, 실제 인터뷰. 스펙보다 중요한 게 있습니다.',5120,412,123,12),
(13,'NORMAL',2,'사수 없는 신입의 3년 생존기','아무도 안 가르쳐줬지만 어떻게든 살아남은 방법. 주도적으로 배운 것들 공유합니다.',3780,298,87,2);

-- ── COMM 14: 부자 되는 길 (SN 79~84) ───────────────────────
INSERT INTO TBL_COMM_POST (COMM_SN,POST_TYP,USER_SN,POST_TTL,POST_CNTNT,VIEW_CNT,LIKE_CNT,CMT_CNT,CREATR_SN) VALUES
(14,'NOTICE',21,'[공지] 부자 되는 길 운영 방침','투자 권유·리딩방 홍보는 즉시 삭제됩니다.',150,4,2,21),
(14,'NORMAL',21,'월급 300으로 10년 만에 순자산 10억 만든 방법','특별한 투자 아닙니다. 저축률·복리·시간의 마법. 연도별 포트폴리오 공개합니다.',7890,623,189,21),
(14,'NORMAL',9,'주식 vs 부동산, 30년 데이터로 보면 답이 나온다','감이 아닌 데이터로 비교했습니다. 어떤 게 더 나은지는 개인마다 다른 이유.',5670,445,134,9),
(14,'NORMAL',21,'ETF 하나로 노후 준비하는 방법 (월 30만원으로 가능)','S&P500·나스닥·전세계 분산. 어떤 ETF를 어떻게 사야 하는지 실전 가이드.',4560,356,103,21),
(14,'NORMAL',5,'코인 폭락 후 주식으로 갈아탄 이유 (포트폴리오 대공개)','코인 500만원 → 300만원 됐을 때 주식 전환 결정. 지금 수익률 공개합니다.',3890,298,87,5),
(14,'NORMAL',20,'절세 모르면 손해 보는 것들 (직장인 세금 총정리)','연말정산·ISA·IRP·주택청약. 연봉 5000이면 100만원은 돌려받을 수 있어요.',6120,489,145,20);

-- ── COMM 15: 그로스 랩 (SN 85~90) ──────────────────────────
INSERT INTO TBL_COMM_POST (COMM_SN,POST_TYP,USER_SN,POST_TTL,POST_CNTNT,VIEW_CNT,LIKE_CNT,CMT_CNT,CREATR_SN) VALUES
(15,'NOTICE',1,'[공지] 그로스 랩 운영 안내','근거 없는 자기계발 정보는 삭제됩니다. 실증적인 이야기만 나눠요.',110,3,1,1),
(15,'NORMAL',1,'아침 5시 기상 1년 후 삶이 바뀐 것들 (데이터 포함)','억지로 일어나던 사람이 자연스럽게 일어나게 된 과정. 수면·생산성·멘탈 변화 공유.',4560,356,103,1),
(15,'NORMAL',18,'독서·운동·명상 동시에 하는 사람의 루틴 공개','하루 24시간으로 어떻게 다 하냐고요? 루틴의 핵심은 순서입니다.',3780,289,78,18),
(15,'NORMAL',22,'요가 5년, 몸보다 마음이 먼저 바뀐 이야기','유연성이 아니라 마음 챙김이었어요. 어떻게 멘탈이 강해졌는지 공유합니다.',3120,245,67,22),
(15,'NORMAL',1,'작심삼일을 200번 반복하다 드디어 습관 만든 방법','의지력 문제가 아니었어요. 환경 설계가 전부였습니다.',5340,421,123,1),
(15,'NORMAL',17,'책 속 지식을 실제 행동으로 연결하는 방법','읽기만 하면 소용없어요. 어떻게 읽은 것을 삶에 적용했는지 구체적으로 씁니다.',2890,223,61,17);


-- ============================================================
--  6. TBL_COMM_POST_CMT (댓글)
--  댓글 SN 흐름:
--    게시글 2  댓글 4개 → SN 1~4   (PRNT: SN2→SN1)
--    게시글 3  댓글 5개 → SN 5~9   (PRNT: SN7→SN5)
--    게시글 8  댓글 4개 → SN 10~13 (PRNT: SN12→SN10)
--    게시글 15 댓글 5개 → SN 14~18 (PRNT: SN16→SN14)
--    게시글 20 댓글 4개 → SN 19~22 (PRNT: SN21→SN19)
--    게시글 21 댓글 4개 → SN 23~26 (PRNT: SN25→SN23)
--    게시글 28 댓글 4개 → SN 27~30 (PRNT: SN29→SN27)
--    게시글 34 댓글 4개 → SN 31~34
--    게시글 74 댓글 5개 → SN 35~39 (PRNT: SN37→SN35)
--    게시글 80 댓글 4개 → SN 40~43
-- ============================================================

-- 게시글 2 (연봉 1억) → SN 1~4
INSERT INTO TBL_COMM_POST_CMT (COMM_POST_SN,USER_SN,PRNT_CMT_SN,CMT_CNTNT,LIKE_CNT,CREATR_SN) VALUES
(2,4,NULL,'스톡옵션 제외하면 결국 시장 평균이라는 거 다들 알면서 포장하는 거죠 뭐',34,4),  -- SN 1
(2,12,1,  '맞아요. 현금 연봉 기준으로는 대기업 시니어가 오히려 높을 수도 있어요',21,12), -- SN 2
(2,3,NULL,'저 작년에 찍었는데 스타트업 아니에요. 빅테크 이직이 답입니다',28,3),          -- SN 3
(2,2,NULL,'5년차면 이제 시니어 도전해야죠. 점프 타이밍이에요',19,2);                      -- SN 4

-- 게시글 3 (사이드프로젝트) → SN 5~9
INSERT INTO TBL_COMM_POST_CMT (COMM_POST_SN,USER_SN,PRNT_CMT_SN,CMT_CNTNT,LIKE_CNT,CREATR_SN) VALUES
(3,2,NULL,'월 300이면 세금 떼고 220 정도인데, 안정성 생각하면 좀 더 키우고 나오는 게 낫지 않을까요',45,2), -- SN 5
(3,4,5,  '맞아요. 6개월치 생활비 모아두고 결정하는 게 맞습니다',22,4),                                      -- SN 6
(3,12,NULL,'저도 비슷한 상황인데 못 나가겠어요. 300이 유지된다는 보장이 없잖아요',38,12),                    -- SN 7
(3,13,NULL,'Product Hunt 어떻게 1위 했는지 그게 더 궁금한데요ㅋㅋ',31,13),                                   -- SN 8
(3,3,8,  '새벽에 론칭하고 커뮤니티 마케팅이요. 따로 글 써드릴게요',18,3);                                    -- SN 9

-- 게시글 8 (GPT-5) → SN 10~13
INSERT INTO TBL_COMM_POST_CMT (COMM_POST_SN,USER_SN,PRNT_CMT_SN,CMT_CNTNT,LIKE_CNT,CREATR_SN) VALUES
(8,2,NULL,'결국 가장 위험한 건 AI를 두려워하기만 하고 안 써본 사람이라고 생각해요',56,2),     -- SN 10
(8,16,10, '100% 동의. 이미 격차가 벌어지고 있는 게 느껴져요',34,16),                          -- SN 11
(8,4,NULL,'LLM 아키텍처 이해하는 사람이 결국 살아남는다고 봐요',42,4),                         -- SN 12
(8,23,NULL,'유튜브 크리에이터 입장에서 Sora는 진짜 위협입니다. 지금도 대체되고 있어요',38,23); -- SN 13

-- 게시글 15 (비트코인 10만달러) → SN 14~18
INSERT INTO TBL_COMM_POST_CMT (COMM_POST_SN,USER_SN,PRNT_CMT_SN,CMT_CNTNT,LIKE_CNT,CREATR_SN) VALUES
(15,30,NULL,'온체인 데이터는 좋은데 거시경제 변수 빠진 것 같아요. 미국 금리가 변수 아닐까요?',45,30), -- SN 14
(15,5,14,  '맞아요. 금리 인하 시나리오 같이 넣어야 완성되는 분석이긴 해요',28,5),                    -- SN 15
(15,9,NULL,'2020년에도 이 말 했고 2021년에도 이 말 했잖아요ㅋㅋ 그때마다 틀리지 않았긴 하지만',52,9), -- SN 16
(15,21,NULL,'반감기 사이클 무시하는 사람들이 항상 늦게 타요',34,21),                                   -- SN 17
(15,30,NULL,'데이터 공유 감사합니다. 개인적으로 12만 달러는 볼 것 같아요',29,30);                       -- SN 18

-- 게시글 20 (시드 투자 3억) → SN 19~22
INSERT INTO TBL_COMM_POST_CMT (COMM_POST_SN,USER_SN,PRNT_CMT_SN,CMT_CNTNT,LIKE_CNT,CREATR_SN) VALUES
(20,29,NULL,'덱 공유 정말 감사합니다. 문제 정의 슬라이드가 특히 인상적이었어요',67,29),  -- SN 19
(20,13,19, '저도 보면서 우리 덱이랑 비교했는데 차이가 확 느껴졌어요. 바로 수정했습니다',34,13), -- SN 20
(20,3,NULL,'투자사 이름 공개 가능한가요? 시드 단계 투자 활발한 곳이 어딘지 알고 싶어서',41,3),    -- SN 21
(20,15,NULL,'팀 구성 부분도 자세히 써주실 수 있을까요? 2명으로 어떻게 설득했는지',38,15);           -- SN 22

-- 게시글 21 (창업 3년 폐업) → SN 23~26
INSERT INTO TBL_COMM_POST_CMT (COMM_POST_SN,USER_SN,PRNT_CMT_SN,CMT_CNTNT,LIKE_CNT,CREATR_SN) VALUES
(21,3,NULL,'공동창업자 갈등 부분 더 자세히 써주실 수 있나요? 지금 저도 비슷한 상황이라서',62,3),  -- SN 23
(21,29,23, '지분 계약을 처음부터 명확하게 안 한 게 제일 컸어요. 따로 글 쓸게요',47,29),          -- SN 24
(21,2,NULL,'폐업한 게 실패가 아니라 이런 글 쓸 수 있는 게 진짜 자산이에요. 감사합니다',89,2),    -- SN 25
(21,13,NULL,'MAU 10만인데 왜 망했는지 수익화 파트 더 자세히 보고 싶어요',34,13);                   -- SN 26

-- 게시글 28 (직장인 30분 운동) → SN 27~30
INSERT INTO TBL_COMM_POST_CMT (COMM_POST_SN,USER_SN,PRNT_CMT_SN,CMT_CNTNT,LIKE_CNT,CREATR_SN) VALUES
(28,8,NULL,'루틴 공유 감사해요. 저도 비슷하게 했는데 식단을 같이 바꿨더니 효과가 훨씬 좋았어요',52,8), -- SN 27
(28,22,27, '식단 어떻게 바꾸셨어요? 운동만으로는 한계 느끼고 있어서요',28,22),                          -- SN 28
(28,25,NULL,'맨몸 운동 초보인데 구체적인 루틴 공유 가능할까요?',41,25),                                   -- SN 29
(28,24,NULL,'1년 꾸준히 한 게 진짜 대단한 거예요. 저도 시작해봐야겠네요',34,24);                           -- SN 30

-- 게시글 34 (전세 사기 체크리스트) → SN 31~34
INSERT INTO TBL_COMM_POST_CMT (COMM_POST_SN,USER_SN,PRNT_CMT_SN,CMT_CNTNT,LIKE_CNT,CREATR_SN) VALUES
(34,9,NULL,'전입신고 하기 전에 확정일자 받는 것도 꼭 추가해주세요. 순서가 중요해요',67,9),        -- SN 31
(34,21,31, '맞아요. 전입신고 당일 확정일자 같이 받아야 보호 효력이 생깁니다',45,21),              -- SN 32
(34,20,NULL,'등기부등본 근저당 설정 금액 확인이 제일 기본이고 중요한데 모르는 분들 너무 많아요',58,20), -- SN 33
(34,1,NULL,'직접 피해 당하신 분이 쓰셨다니 더 신뢰가 가요. 주변에 많이 공유할게요',43,1);            -- SN 34

-- 게시글 74 (연봉 협상) → SN 35~39
INSERT INTO TBL_COMM_POST_CMT (COMM_POST_SN,USER_SN,PRNT_CMT_SN,CMT_CNTNT,LIKE_CNT,CREATR_SN) VALUES
(74,2,NULL,'스크립트 공유 진짜 감사해요. 저는 협상 시작도 못 하고 제시하는 대로 받았거든요',78,2),      -- SN 35
(74,12,35, '협상 못 하는 게 능력 없는 게 아니에요. 그냥 배운 적이 없는 거예요. 저도 처음엔 그랬어요',45,12), -- SN 36
(74,3,NULL,'오퍼 받은 후 72시간 기다리는 전략은 진짜 효과 있어요. 실제로 해봤습니다',56,3),               -- SN 37
(74,13,NULL,'HR 입장에서 말씀드리면 협상 하는 게 나쁜 인상 안 줘요. 오히려 자기 가치 아는 사람으로 봐요',67,13), -- SN 38
(74,15,NULL,'연봉 외에 복지·재택·스톡 같은 비금전적 요소도 협상 가능한 거 모르는 분들 많아요',42,15);         -- SN 39

-- 게시글 80 (월급 300 → 순자산 10억) → SN 40~43
INSERT INTO TBL_COMM_POST_CMT (COMM_POST_SN,USER_SN,PRNT_CMT_SN,CMT_CNTNT,LIKE_CNT,CREATR_SN) VALUES
(80,9,NULL,'저축률이 핵심인 것 같아요. 연봉 얼마였는지보다 얼마나 아꼈는지가 차이를 만드는 거죠',89,9),   -- SN 40
(80,5,NULL,'코인·주식 비중은 어떻게 가져가셨나요? 현금 비중이 궁금해요',45,5),                             -- SN 41
(80,20,NULL,'부동산은 언제 처음 들어가셨어요? 지금 같은 시장에서도 가능할까요?',56,20),                     -- SN 42
(80,21,NULL,'10년이라는 시간이 주는 교훈이네요. 빨리 시작하는 게 제일 중요한 것 같아요',67,21);              -- SN 43


-- ============================================================
--  7. TBL_COMM_POST_LIKE (인기 게시글 좋아요)
--  (COMM_POST_SN, USER_SN) UNIQUE 제약 준수
-- ============================================================

-- 게시글 3 (사이드프로젝트)
INSERT INTO TBL_COMM_POST_LIKE (COMM_POST_SN,USER_SN,LIKE_TYP,CREATR_SN) VALUES
(3,4,'UP',4),(3,12,'UP',12),(3,13,'UP',13),(3,16,'UP',16),(3,23,'UP',23);

-- 게시글 5 (비전공 합격)
INSERT INTO TBL_COMM_POST_LIKE (COMM_POST_SN,USER_SN,LIKE_TYP,CREATR_SN) VALUES
(5,2,'UP',2),(5,4,'UP',4),(5,13,'UP',13),(5,15,'UP',15),(5,16,'UP',16);

-- 게시글 10 (프롬프트 엔지니어링)
INSERT INTO TBL_COMM_POST_LIKE (COMM_POST_SN,USER_SN,LIKE_TYP,CREATR_SN) VALUES
(10,4,'UP',4),(10,16,'UP',16),(10,15,'UP',15),(10,23,'UP',23),(10,9,'UP',9);

-- 게시글 15 (비트코인 10만달러)
INSERT INTO TBL_COMM_POST_LIKE (COMM_POST_SN,USER_SN,LIKE_TYP,CREATR_SN) VALUES
(15,9,'UP',9),(15,30,'UP',30),(15,21,'UP',21),(15,3,'UP',3);

-- 게시글 15에 DOWN도 일부
INSERT INTO TBL_COMM_POST_LIKE (COMM_POST_SN,USER_SN,LIKE_TYP,CREATR_SN) VALUES
(15,1,'DOWN',1),(15,2,'DOWN',2);

-- 게시글 15 (코인 1억 손실)
INSERT INTO TBL_COMM_POST_LIKE (COMM_POST_SN,USER_SN,LIKE_TYP,CREATR_SN) VALUES
(16,5,'UP',5),(16,9,'UP',9),(16,21,'UP',21),(16,1,'UP',1),(16,2,'UP',2);

-- 게시글 21 (창업 폐업)
INSERT INTO TBL_COMM_POST_LIKE (COMM_POST_SN,USER_SN,LIKE_TYP,CREATR_SN) VALUES
(21,3,'UP',3),(21,2,'UP',2),(21,13,'UP',13),(21,15,'UP',15);

-- 게시글 28 (직장인 30분 운동)
INSERT INTO TBL_COMM_POST_LIKE (COMM_POST_SN,USER_SN,LIKE_TYP,CREATR_SN) VALUES
(28,8,'UP',8),(28,22,'UP',22),(28,25,'UP',25),(28,7,'UP',7);

-- 게시글 34 (전세 사기)
INSERT INTO TBL_COMM_POST_LIKE (COMM_POST_SN,USER_SN,LIKE_TYP,CREATR_SN) VALUES
(34,9,'UP',9),(34,21,'UP',21),(34,1,'UP',1),(34,3,'UP',3),(34,2,'UP',2);

-- 게시글 74 (연봉 협상)
INSERT INTO TBL_COMM_POST_LIKE (COMM_POST_SN,USER_SN,LIKE_TYP,CREATR_SN) VALUES
(74,2,'UP',2),(74,3,'UP',3),(74,13,'UP',13),(74,15,'UP',15),(74,29,'UP',29);

-- 게시글 80 (순자산 10억)
INSERT INTO TBL_COMM_POST_LIKE (COMM_POST_SN,USER_SN,LIKE_TYP,CREATR_SN) VALUES
(80,9,'UP',9),(80,5,'UP',5),(80,20,'UP',20),(80,3,'UP',3),(80,1,'UP',1);


-- ============================================================
--  8. TBL_COMM_POST_TAG
-- ============================================================

-- COMM1: 개발자 라이프
INSERT INTO TBL_COMM_POST_TAG (COMM_POST_SN,TAG_SN,CREATR_SN) VALUES
(2,1,2),(2,17,2),           -- 연봉 1억: #개발 #커리어
(3,1,3),(3,6,3),(3,5,3),    -- 사이드프로젝트: #개발 #사이드프로젝트 #창업
(4,1,4),(4,19,4),           -- ChatGPT: #개발 #ChatGPT
(5,1,12),(5,17,12),(5,18,12),-- 비전공 합격: #개발 #커리어 #이직
(6,1,2),(6,17,2);           -- 스타트업vs대기업: #개발 #커리어

-- COMM2: AI 연구소
INSERT INTO TBL_COMM_POST_TAG (COMM_POST_SN,TAG_SN,CREATR_SN) VALUES
(8,2,4),(8,19,4),           -- GPT-5: #AI #ChatGPT
(9,2,16),(9,20,16),         -- Sora: #AI #자동화
(10,2,2),(10,19,2),(10,20,2),-- 프롬프트: #AI #ChatGPT #자동화
(11,2,4),(11,19,4),         -- AI활용법: #AI #ChatGPT
(12,2,23);                  -- Claude비교: #AI

-- COMM3: 코인토크
INSERT INTO TBL_COMM_POST_TAG (COMM_POST_SN,TAG_SN,CREATR_SN) VALUES
(14,3,5),(14,11,5),         -- 비트코인: #코인 #투자
(15,3,30),(15,11,30),       -- 1억 손실: #코인 #투자
(16,3,9),(16,4,9),(16,11,9),-- 이더리움: #코인 #주식 #투자
(17,3,5),(17,11,5),         -- 알트코인: #코인 #투자
(18,3,30),(18,11,30);       -- DCA: #코인 #투자

-- COMM4: 스타트업 허브
INSERT INTO TBL_COMM_POST_TAG (COMM_POST_SN,TAG_SN,CREATR_SN) VALUES
(20,5,3),(20,11,3),         -- 시드투자: #창업 #투자
(21,5,29),                  -- 폐업: #창업
(22,5,2),(22,17,2),         -- VC이야기: #창업 #커리어
(23,5,3),(23,6,3),          -- 직장+창업: #창업 #사이드프로젝트
(24,5,29),(24,6,29);        -- MVP: #창업 #사이드프로젝트

-- COMM5: 헬시라이프
INSERT INTO TBL_COMM_POST_TAG (COMM_POST_SN,TAG_SN,CREATR_SN) VALUES
(26,9,8),(26,10,8),         -- 체지방: #헬스 #다이어트
(27,9,22),                  -- 근육: #헬스
(28,9,24),(28,10,24),       -- 직장인운동: #헬스 #다이어트
(29,9,8),                   -- 커피수면: #헬스
(30,10,22);                 -- 간헐적단식: #다이어트

-- COMM6: 부동산
INSERT INTO TBL_COMM_POST_TAG (COMM_POST_SN,TAG_SN,CREATR_SN) VALUES
(32,12,20),(32,11,20),      -- 갭투자: #부동산 #투자
(33,12,9),                  -- 청약: #부동산
(34,12,20),                 -- 전세사기: #부동산
(35,12,21),(35,11,21),      -- 재개발: #부동산 #투자
(36,12,1),(36,11,1);        -- 건물주: #부동산 #투자

-- COMM7: 게임
INSERT INTO TBL_COMM_POST_TAG (COMM_POST_SN,TAG_SN,CREATR_SN) VALUES
(38,15,19),(39,15,2),(40,15,16),(41,15,19),(42,15,23);

-- COMM8: 독서
INSERT INTO TBL_COMM_POST_TAG (COMM_POST_SN,TAG_SN,CREATR_SN) VALUES
(44,16,18),(44,23,18),(45,16,17),(46,16,18),(47,16,1),(48,16,17),(48,23,17);

-- COMM9: 여행
INSERT INTO TBL_COMM_POST_TAG (COMM_POST_SN,TAG_SN,CREATR_SN) VALUES
(50,7,7),(51,7,25),(51,25,25),(52,7,7),(53,7,6),(54,7,24);

-- COMM10: 음악
INSERT INTO TBL_COMM_POST_TAG (COMM_POST_SN,TAG_SN,CREATR_SN) VALUES
(56,14,11),(57,14,27),(58,14,11),(59,14,27),(60,14,17);

-- COMM11: 사진
INSERT INTO TBL_COMM_POST_TAG (COMM_POST_SN,TAG_SN,CREATR_SN) VALUES
(62,13,6),(63,13,28),(64,13,6),(65,13,28),(66,13,23);

-- COMM12: 요리
INSERT INTO TBL_COMM_POST_TAG (COMM_POST_SN,TAG_SN,CREATR_SN) VALUES
(68,21,10),(68,8,10),(69,21,26),(70,21,10),(71,21,9),(72,21,26),(72,8,26);

-- COMM13: 커리어
INSERT INTO TBL_COMM_POST_TAG (COMM_POST_SN,TAG_SN,CREATR_SN) VALUES
(74,17,12),(74,18,12),      -- 연봉협상: #커리어 #이직
(75,17,3),(76,17,13),(77,17,12),(78,17,2),(78,1,2);

-- COMM14: 재테크
INSERT INTO TBL_COMM_POST_TAG (COMM_POST_SN,TAG_SN,CREATR_SN) VALUES
(80,22,21),(80,11,21),      -- 순자산10억: #재테크 #투자
(81,4,9),(81,12,9),(81,11,9),-- 주식vs부동산: #주식 #부동산 #투자
(82,22,21),(82,11,21),      -- ETF: #재테크 #투자
(83,3,5),(83,11,5),         -- 코인→주식: #코인 #투자
(84,22,20);                 -- 절세: #재테크

-- COMM15: 자기계발
INSERT INTO TBL_COMM_POST_TAG (COMM_POST_SN,TAG_SN,CREATR_SN) VALUES
(86,23,1),(86,9,1),         -- 아침기상: #자기계발 #헬스
(87,23,18),(87,16,18),      -- 루틴: #자기계발 #독서
(88,23,22),(89,23,1),(90,23,17),(90,16,17);
