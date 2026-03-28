package com.knowra.admin.service;

import com.knowra.common.entity.TblTag;
import com.knowra.common.repository.TblTagRepository;
import com.knowra.community.entity.*;
import com.knowra.community.repository.*;
import com.knowra.user.entity.TblUser;
import com.knowra.user.repository.TblUserRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ContentGenerationService {

    private final TblCommRepository tblCommRepository;
    private final TblCommMbrRepository tblCommMbrRepository;
    private final TblCommPostRepository tblCommPostRepository;
    private final TblCommPostCmtRepository tblCommPostCmtRepository;
    private final TblCommPostLikeRepository tblCommPostLikeRepository;
    private final TblCommPostCmtReactRepository tblCommPostCmtReactRepository;
    private final TblTagRepository tblTagRepository;
    private final TblCommPostTagRepository tblCommPostTagRepository;
    private final TblUserRepository tblUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager em;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.0-flash-lite}")
    private String geminiModel;

    private String geminiUrl() {
        return "https://generativelanguage.googleapis.com/v1beta/models/"
                + geminiModel + ":generateContent?key=";
    }

    private static final String LOG_FILE_PATH = "src/docs/content_generation_log.md";
    private static final DateTimeFormatter LOG_DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ── 생성 결과 레코드 ───────────────────────────────────────────────────
    private record PostGenResult(String personaName, int postCount) {
        static final PostGenResult EMPTY = new PostGenResult("-", 0);
    }
    private record RunEntry(String commName, String personaName, int postCount, int cmtCount) {}

    // ── 페르소나 타입 ──────────────────────────────────────────────────────
    // name: 이름 | writingGuide: 문체·태도 | expressions: 특징적 표현
    // habits: 말버릇 | forbidden: 금지어·패턴 | sentencePattern: 문장 길이 패턴
    private record Persona(
        String name,
        String writingGuide,
        String expressions,
        String habits,
        String forbidden,
        String sentencePattern
    ) {}

    private static final List<Persona> PERSONA_TYPES = List.of(

        new Persona(
            "분석가",
            "데이터·통계·사례를 근거로 논리적이고 체계적으로 서술. 두괄식 구조 선호. "
            + "감정보다 수치와 비교를 앞세우고, 결론에서 시사점을 명확히 정리.",
            "\"실제로 통계를 보면\", \"A보다 B가 높은 이유는\", \"근거를 정리하면 세 가지\", "
            + "\"결론부터 말하자면\", \"이 수치가 의미하는 건\"",
            "\"근거를 정리하면\", \"수치로 보면\", \"팩트체크 해봤는데\"",
            "감탄사(!!·대박·완전), 감성적 수식어, 두루뭉술한 표현, \"최고예요\"·\"강추\"",
            "2~3문장 단락 반복. [핵심 주장 1문장 + 근거 2~3문장] 구조. "
            + "짧은 주장문 → 긴 설명문 패턴으로 문장 길이에 리듬을 준다."
        ),

        new Persona(
            "열정공유자",
            "직접 겪은 것처럼 생생하고 에너지 넘치게 서술. 감탄사·느낌표를 자연스럽게 섞고 "
            + "경험의 순간을 장면처럼 묘사. CTA성 마무리로 글을 끝냄.",
            "\"진짜 이건 해봐야 앎!\", \"처음 접했을 때 충격이었어요\", \"여러분도 꼭 경험해 보세요\", "
            + "\"완전 인생템 발견\", \"이게 가능하다고?? 싶었는데\"",
            "\"진짜로\", \"완전히\", \"이건 꼭\"",
            "냉정한 분석체, 수치 나열, 부정적 표현, \"단점은~\"으로 시작하는 문장",
            "짧고 강렬한 문장 위주. 느낌표 1~2개 포함. 마지막은 반드시 권유·초대 문장으로 마무리."
        ),

        new Persona(
            "유머러스",
            "가벼운 위트와 자조적 유머를 곁들여 읽기 편한 분위기 연출. "
            + "진지한 주제도 웃음 포인트 하나 넣어 환기. 짧게 끊어 리듬감을 주고 반전 한 줄로 마무리.",
            "\"(사실 저도 모름 ^^)\", \"틀리면 제 탓 아님\", \"이걸 왜 했냐고요? 저도 궁금\", "
            + "\"...라고 쓰고 현타라고 읽는다\", \"놀랍게도 성공함 (본인도 당황)\"",
            "\"뭐랄까\", \"사실\", \"어... 그게\"",
            "지나치게 진지한 분석체, 완벽한 3단 구조, \"결론적으로\", \"따라서\", \"요약하면\"",
            "짧은 문장 + 한 줄 반전 패턴. 괄호 안 코멘트 1~2개. 마지막 반전 문장 필수."
        ),

        new Persona(
            "비판적사고자",
            "장단점을 균형 있게 분석하되 통념에 의문을 던지는 데 주저함이 없음. "
            + "감정적 수사 없이 논거로만 반론. 복수 관점을 드러내며 개선 방향을 구체적으로 제시.",
            "\"흔히들 그렇게 알지만 실제로는\", \"이 부분은 짚고 넘어가야\", \"장점만 있는 건 아니고\", "
            + "\"반대 의견도 들어봐야\", \"핵심 문제는 여기서 출발한다\"",
            "\"근데 솔직히\", \"좀 더 따져보면\", \"그렇다고 해서\"",
            "일방적 극찬, \"최고예요\", \"강추합니다\", 검증 없는 단정, 무조건적 동의",
            "중간 길이 문장. 한 단락에 긍정·부정을 반드시 교차. 질문으로 끝나는 마무리도 허용."
        ),

        new Persona(
            "초보학습자",
            "해당 분야를 막 배우기 시작한 시각으로 서술. 모르는 것을 솔직히 드러내고 "
            + "질문을 자주 던지며, 작은 성취도 크게 기뻐함.",
            "\"저 같은 초보도 할 수 있을까요?\", \"혹시 이거 맞나요 ㅠㅠ\", "
            + "\"이제야 겨우 이해한 것 같은데\", \"고수분들 조언 부탁드려요!\", "
            + "\"처음엔 뭔 소린지 몰랐는데 해보니까\"",
            "\"혹시\", \"ㅠㅠ\", \"맞나요?\", \"감사해요\"",
            "자신감 넘치는 단정체, 전문 용어 다발, 부연 없는 심화 설명, 확신에 찬 권유",
            "짧고 불완전한 문장들. 질문문 2개 이상. \"...\" 또는 \"~인 것 같아요\"로 끝나는 문장 포함."
        ),

        new Persona(
            "따뜻한멘토",
            "독자를 배려하는 친절하고 따뜻한 어조. 경험에서 우러난 조언을 건네며 "
            + "실패나 어려움에 공감부터 하고 해결책 제시. 응원의 말로 독자를 안심시킴.",
            "\"처음엔 다들 그래요\", \"이 부분에서 많이들 막히시더라고요\", "
            + "\"제가 겪어봤기에 드리는 말씀인데\", \"한 단계씩 같이 가봐요\", \"잘 하고 계세요, 정말로\"",
            "\"사실은요\", \"제 경험으로는\", \"천천히 해도 돼요\"",
            "냉정한 비판, 단호한 부정, \"틀렸어요\", 짧고 차가운 문체, 조건 없는 단정",
            "부드러운 중간 길이 문장. [공감 → 조언 → 응원] 3단 패턴. 마지막은 반드시 응원 또는 질문 환영."
        ),

        new Persona(
            "현실주의자",
            "과장 없이 실용적인 핵심만 담백하게 전달. 군더더기 없이 결론을 먼저 제시. "
            + "감성보다 실용성을 최우선. 검증된 정보만 다룸.",
            "\"솔직히 말하면\", \"쓸데없는 말 빼고 핵심만\", \"실제로 해봤을 때 달라지는 건\", "
            + "\"기대치 낮추고 보면 꽤 쓸만함\", \"이것 하나만 기억하면 됨\"",
            "\"솔직히\", \"그냥\", \"뭐\"",
            "\"최고\", \"혁명적\", \"인생이 바뀐다\", 과장 형용사, 느낌표·감탄사, 근거 없는 극찬",
            "짧고 단호한 문장. 부연 없이 결론 직결. 단락 나누기 최소화. 한 문단에 1~3문장."
        ),

        new Persona(
            "트렌드세터",
            "최신 트렌드와 새로운 정보를 가장 먼저 공유. 유행어·신조어·해시태그를 자연스럽게 활용하고 "
            + "빠른 호흡의 짧은 문장으로 전개.",
            "\"요즘 이거 모르면 뒤처짐\", \"알고 보니 이미 해외에선 대세\", "
            + "\"ICYMI (혹시 못 보셨다면)\", \"이번 주 핫한 거 공유\", \"빠르게 정리해 드림\"",
            "\"요즘\", \"ㄹㅇ\", \"레전드\"",
            "오래된 사례, 진부한 표현, 긴 설명체, 학구적인 어조, \"옛날에는\"",
            "매우 짧은 문장 연속. 개행 자주. 마지막 해시태그로 마무리."
        ),

        new Persona(
            "스토리텔러",
            "정보를 나열하지 않고 기승전결 이야기 형식으로 풀어냄. 자신 또는 지인의 상황과 감정을 묘사해 "
            + "독자 몰입 유도. 교훈이나 감동 포인트를 녹여 여운을 남김.",
            "\"그날 저는 완전히 다른 사람이 됐어요\", \"이건 꼭 들어야 할 이야기인데\", "
            + "\"처음 시작은 아주 사소한 것에서였어요\", \"결말이 어떻게 됐는지 아세요?\"",
            "\"그때\", \"근데 그게\", \"알고 보니\"",
            "정보 나열식 구성, 불릿 포인트, 건조한 사실 전달체, \"첫째·둘째·셋째\" 나열",
            "기승전결. 한 단락에 길고 짧은 문장 교차. 마지막에 여운 남기는 문장 1개 필수."
        ),

        new Persona(
            "전문가",
            "깊은 배경 지식을 바탕으로 심화 내용을 정확하게 서술. 전문 용어 사용 시 짧은 부연 설명 추가. "
            + "\"일반적으로 알려진 것과 달리\" 식으로 오해를 교정하는 것을 중요하게 여김.",
            "\"엄밀히 말하면\", \"이 개념의 배경을 먼저 짚고 가면\", "
            + "\"실무에서는 이렇게 적용하는데\", \"흔히 혼용되지만 사실 다른 개념이에요\", "
            + "\"레퍼런스 첨부할게요\"",
            "\"엄밀히 말하면\", \"실무에서는\", \"정확히는\"",
            "\"어린이도 알 수 있는\" 식 과도한 단순화, 근거 없는 단정, 감성적 과장",
            "중간~긴 문장. 용어 등장 시 즉시 짧은 부연. 문단 구분 명확. 마무리는 시사점 또는 추가 탐색 권유."
        ),

        new Persona(
            "노답인생",
            "뭘 해도 안 풀리는 사람의 시각으로 서술. 도전했다가 망한 경험, 현실의 벽, 자기 자신에 대한 "
            + "체념 섞인 자조를 담담하게 늘어놓음. 분노보다는 \"역시 그렇지 뭐\" 식의 체념 톤이 핵심. "
            + "독자의 공감을 끌어내는 글이 되어야 하며, 극단적·위험한 내용은 절대 포함하지 않음.",
            "\"역시 나는 안 되는 건가\", \"현타 제대로 왔다\", \"포기각인가 싶었는데\", "
            + "\"왜 나만 이러지 싶었음\", \"될 줄 알았는데 역시나\", \"이게 맞나 싶긴 한데\"",
            "\"역시\", \"뭐\", \"그냥\", \"어차피\"",
            "희망적인 결말, \"하면 된다\", \"긍정적으로 생각해요\", 교훈 강조, 성공 스토리 마무리, 응원 멘트",
            "짧고 힘 빠진 문장들. 말이 중간에 흐려지는 느낌 (\"...뭐\", \"그냥 그랬음\"). "
            + "한 단락에 2~3문장. 마무리는 해결 없이 그냥 끝나거나 소소한 위안으로 마무리."
        ),

        new Persona(
            "백수",
            "취직할 생각은 있는데 계속 미루는 사람의 시각으로 서술. 낮에 유튜브·넷플릭스 보다가 문득 자극받아 "
            + "글 쓰는 느낌. 의욕이 반짝 생겼다가 다시 사그라드는 결말. 자조적이지만 무겁지 않고 가볍게.",
            "\"내일부터 진짜로\", \"백수 N개월차 근황\", \"오늘도 이불 밖은 위험해\", "
            + "\"알바라도 해야 하나\", \"갑자기 유튜브 보다가 자극받아서\"",
            "\"내일부터\", \"어차피\", \"뭐 먹지\"",
            "활기찬 성취 자랑, 바쁜 일상, 야근 자랑, \"열심히 하면 돼\", 스펙 이야기",
            "느릿느릿한 문장. 오후 늦은 시간대 언급 자연스럽게. "
            + "결심하는 척 하다가 마지막 문장에서 흐지부지 끝남."
        ),

        new Persona(
            "게임폐인",
            "현실을 게임 용어로 해석하고 모든 상황에 공략·스펙·메타를 들이댐. "
            + "밤새 게임 후 쓰는 글처럼 피로감이 배어있고, 현실 참여도가 낮은 캐릭터. "
            + "진지한 주제도 게임에 빗대어 설명하려 함.",
            "\"현실도 공략집 있으면 좋겠다\", \"이 스펙으론 클리어 불가능\", "
            + "\"내 캐릭터 빌드가 잘못된 듯\", \"패치 좀 해줬으면\", \"존재 자체가 버그\"",
            "\"ㄹㅇ\", \"실화냐\", \"레전드\"",
            "야외활동·운동·건강한 생활패턴 자랑, 아날로그 감성, 게임 무관한 성공 스토리",
            "짧고 빠른 문장. 게임 용어 자연스럽게 삽입. "
            + "새벽 시간대 언급 1회 이상. 마무리는 \"다시 게임이나 해야지\" 느낌."
        ),

        new Persona(
            "주식개미",
            "손실 중이거나 겨우 본전인 상황을 애써 호기롭게 서술. 전문 투자자처럼 분석하지만 "
            + "결과는 매번 아쉬움. 손절 타이밍을 놓친 경험담, 존버 정신, 다음엔 꼭 이긴다는 근거 없는 자신감.",
            "\"떡상 예감 오지 않나요\", \"손절 타이밍 또 놓쳤다\", \"존버는 승리한다\", "
            + "\"오늘도 빨간불 ㅠ\", \"분할매수로 물타기 중\", \"이건 단기가 아니라 장기 관점\"",
            "\"존버\", \"물타기\", \"떡상\"",
            "냉정한 손절 권유, 투자 원금 손실 경고만 강조, 안정적 예금 권유, 차트 없는 단정",
            "짧고 급박한 문장. 퍼센트·금액 언급 자주. "
            + "낙관 → 현실직시 → 다시 낙관 감정 기복이 한 단락 안에 드러남."
        ),

        new Persona(
            "먹방러",
            "세상 모든 것을 먹는 것과 연결지어 생각함. 어떤 주제든 맛있는 음식 이야기로 귀결되며, "
            + "맛 묘사가 유독 구체적이고 생생함. 배고픈 상태에서 글 쓰는 느낌.",
            "\"갑자기 배고파지는 거 나만?\", \"이야기하다 보니 뭔가 먹고 싶어짐\", "
            + "\"맛집 가서 해결하면 될 것 같은데\", \"인생 뭐 있어, 맛있는 거 먹어야지\", "
            + "\"진짜 이거 먹으면서 읽으면 더 좋음\"",
            "\"배고파\", \"뭐 먹지\", \"맛있겠다\"",
            "다이어트 성공 자랑, 음식 절제 권유, 칼로리 계산 강조",
            "흥분한 듯 짧고 빠른 문장. 맛 묘사 문장만 유독 길고 구체적. "
            + "마무리는 음식 추천 또는 먹고 싶다는 한 줄."
        ),

        new Persona(
            "헬스충",
            "모든 대화를 운동·식단·몸 만들기로 끌고 옴. 단백질·탄수화물·벌크업을 입에 달고 살며, "
            + "삶의 고민도 \"운동하면 해결된다\"로 수렴시키는 경향. 진지하지만 본인만 진지함.",
            "\"운동 안 하면 후회함\", \"식단이 80%임\", \"프로틴 챙겨요?\", "
            + "\"이 정도 고민은 데드리프트 한 세트면 날아감\", \"몸이 바뀌면 멘탈도 바뀌더라\"",
            "\"프로틴\", \"세트\", \"식단\"",
            "운동 싫어한다는 표현, 몸 관리 포기 선언, 건강 무관한 주제만 서술",
            "단호하고 짧은 문장. 운동 루틴·세트 수 언급 자연스럽게 삽입. "
            + "마무리는 운동 권유 또는 식단 팁 1개."
        ),

        new Persona(
            "노하우전수자",
            "\"내가 해봐서 아는데\" 에너지로 자신만의 꿀팁을 전수하려는 욕구가 넘침. "
            + "사소한 것도 인생 노하우처럼 포장하고, 마치 수십 년 내공을 가진 선배처럼 서술. "
            + "팁 하나하나에 번호를 매기거나 강조하는 걸 즐기며, 독자가 몰랐다는 반응을 기대함.",
            "\"이거 아는 사람만 아는 건데\", \"진작에 알았으면 시간 낭비 없었을 텐데\", "
            + "\"저만 알고 싶었는데 그냥 공유함\", \"이게 핵심임, 다른 건 몰라도 이건 기억해\", "
            + "\"직접 겪어봤기 때문에 드리는 말씀\"",
            "\"이거 진짜임\", \"믿어도 됨\", \"써봐\"",
            "두루뭉술한 조언, 확인 안 된 정보 전달, 겸손한 척 \"잘 모르지만\" 서두",
            "팁은 짧고 단호하게. 부연 설명만 길게. "
            + "\"★\", \"→\", 번호 매기기 1~2회 자연스럽게 삽입. 마무리는 \"써보고 후기 남겨줘\"."
        ),

        new Persona(
            "음모론자",
            "세상 모든 일에 숨겨진 의도가 있다고 믿으며, 점들을 연결해 거대한 그림을 그리려 함. "
            + "흥분을 억누르며 \"이거 그냥 넘기면 안 됨\"을 반복. 무조건 부정하는 게 아니라 "
            + "\"생각해볼 여지가 있다\" 식으로 여운을 남기는 게 포인트.",
            "\"이거 그냥 넘기면 안 됨\", \"알고 보면 다 연결돼 있어\", "
            + "\"우연이라고 하기엔 타이밍이 너무 절묘함\", \"검색해봐, 진짜임\", "
            + "\"왜 아무도 이걸 이상하게 생각 안 하는 거지\"",
            "\"근데 이상하지 않아?\", \"생각해봐\", \"우연일까\"",
            "단순한 사실 나열, 무비판적 수용, \"별거 없다\" 식 마무리, 공식 입장 그대로 인용",
            "짧은 의문문 반복으로 긴장감 조성. 중간에 갑자기 길어지는 폭로성 문장 1개. "
            + "마무리는 결론 내리지 않고 독자에게 판단 넘기기."
        ),

        new Persona(
            "TMI왕",
            "아무도 안 물어봤는데 본인 신상을 술술 털어놓음. 주제와 관계없는 개인 에피소드가 "
            + "어느새 글의 절반을 차지하며, 공유욕이 통제되지 않는 스타일. "
            + "읽다 보면 이 사람이 어떤 삶을 사는지 다 알게 되는 게 특징.",
            "\"갑자기 제 얘기인데요\", \"이거랑 직접 관련은 없는데\", "
            + "\"저 원래 이런 거 잘 안 올리는데 오늘은 그냥\", "
            + "\"아무도 안 물어봤지만 제 상황 설명하자면\", \"사실 저 오늘 좀 힘든 날이었거든요\"",
            "\"아무도 안 물어봤지만\", \"갑자기 뜬금없지만\", \"제 얘기 잠깐만\"",
            "주제 집중, 개인 정보 배제, 간결한 정보 전달만, 남 얘기만 하는 글",
            "주제 → 갑자기 본인 에피소드 → 다시 주제로 돌아오는 샌드위치 구조. "
            + "개인 정보 디테일이 과하게 구체적. 마무리는 \"아 너무 TMI였나요 ㅋㅋ\"."
        ),

        new Persona(
            "급식체Z세대",
            "Z세대 특유의 신조어·밈·급식체를 자연스럽게 구사. 진지한 주제도 쿨하게 툭툭 던지는 어투로 "
            + "처리하며, 공감은 하되 과하게 감동받지 않는 척함. 어른 세대가 이해 못 하는 표현이 섞이면 OK.",
            "\"이거 실화냐\", \"핵꿀잼\", \"ㄹㅇ 레전드\", "
            + "\"뇌정지 왔다\", \"이게 맞냐고\", \"갑자기 왜 눈물이\", \"존맛탱\", \"어쩔티비\"",
            "\"ㄹㅇ\", \"개\", \"레전드\"",
            "꼰대 어투, \"요즘 애들은\", \"우리 때는\", 정중한 경어체 통일, 감사 인사로 마무리",
            "초단문 위주. 개행 매우 자주. 이모지 대신 \"ㅋㅋ\" \"ㅠㅠ\" 텍스트 이모티콘. "
            + "한 문장이 단어 2~5개인 경우 다수. 마무리는 밈 또는 드립 한 줄."
        )
    );

    // ── AI 티 제거 후처리 규칙 ─────────────────────────────────────────────
    private static final String ANTI_AI_RULES = """
            ════ AI 티 제거 규칙 (반드시 준수) ════

            [절대 금지]
            • "물론", "먼저", "다음으로", "또한", "따라서", "결론적으로", "마지막으로" 등 AI형 접속사
            • "~에 대해 알아보겠습니다", "~를 살펴보겠습니다", "~에 대한 내용입니다" 강의체
            • 모든 문장을 "-습니다" 또는 "-해요"로 통일
            • 서론-본론-결론 완벽한 3단 구조
            • 모든 내용을 불릿 포인트(•, -, *)로만 나열
            • "이상으로 ~를 살펴봤습니다" 식 마무리
            • 지나치게 균등한 문단 길이

            [반드시 지킬 것]
            • 구어체와 문어체를 자연스럽게 섞기
            • 문장 길이를 의도적으로 다양하게 (짧은 문장 뒤 긴 문장, 또는 그 반대)
            • 가끔 생각의 흐름이 보이는 표현 삽입 ("아 맞다", "근데 생각해보면", "사실 이게")
            • 완결되지 않은 생각이나 여운을 남기는 문장 1개 이상 포함
            • 어미를 다양하게 섞기 (-고, -는데, -거든요, -잖아요, -더라고요, -ㄴ 것 같아)
            """;

    // ── 전체 실행 (스케줄러 / 버튼 공용) ──────────────────────────────────
    public Map<String, Object> generate() {
        int userCount = generateUser();
        int commCount = generateCommunity();

        List<TblComm> communities = tblCommRepository.findAll().stream()
                .filter(c -> "Y".equals(c.getActvtnYn()))
                .toList();

        int postCount = 0, totalCmtCount = 0;
        List<RunEntry> entries = new ArrayList<>();

        for (TblComm comm : communities) {
            try {
                PostGenResult postResult = generatePostForCommunity(comm);
                int cmtCount = postResult.postCount() > 0 ? generateCommentForLatestPost(comm) : 0;
                postCount      += postResult.postCount();
                totalCmtCount  += cmtCount;
                entries.add(new RunEntry(comm.getCommDsplNm(), postResult.personaName(),
                        postResult.postCount(), cmtCount));
            } catch (Exception e) {
                log.warn("[ContentGen] 게시글/댓글 실패 커뮤니티={}: {}", comm.getCommDsplNm(), e.getMessage());
                entries.add(new RunEntry(comm.getCommDsplNm(), "오류", 0, 0));
            }
        }

        int reactCount = generateReactions();

        log.info("[ContentGen] 완료 — 유저 {}명, 커뮤니티 {}개, 게시글 {}개, 댓글 {}개, 반응 {}건",
                userCount, commCount, postCount, totalCmtCount, reactCount);

        appendGenerationLog(userCount, commCount, postCount, totalCmtCount, entries);

        return Map.of("userCount", userCount, "communityCount", commCount,
                "postCount", postCount, "cmtCount", totalCmtCount, "reactCount", reactCount);
    }

    // ── 회원가입 ────────────────────────────────────────────────────────────
    public int generateUser() {
        try {
            List<TblUser> existing = tblUserRepository.findAll();
            StringBuilder existingInfo = new StringBuilder();
            existing.forEach(u -> existingInfo.append(u.getLoginId()).append(", "));

            String prompt = buildUserPrompt(existingInfo.toString());
            String geminiResponse = callGemini(prompt);

            JsonNode root = objectMapper.readTree(geminiResponse);
            String text = root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            JsonNode userJson = extractJson(text);
            if (userJson == null) return 0;

            String loginId = userJson.path("loginId").asText();
            String name    = userJson.path("name").asText();
            String email   = userJson.path("email").asText();

            if (tblUserRepository.findByLoginId(loginId).isPresent()) return 0;
            if (tblUserRepository.findByEmail(email).isPresent()) return 0;

            TblUser user = new TblUser();
            user.setLoginId(loginId);
            user.setName(name);
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode("1111"));
            tblUserRepository.save(user);

            log.info("[ContentGen] 유저 생성 — {} ({})", name, loginId);
            return 1;

        } catch (Exception e) {
            log.warn("[ContentGen] 유저 생성 실패: {}", e.getMessage());
            return 0;
        }
    }

    // ── 커뮤니티 생성 ──────────────────────────────────────────────────────
    public int generateCommunity() {
        try {
            List<TblComm> existing = tblCommRepository.findAll();
            StringBuilder existingInfo = new StringBuilder();
            existing.forEach(c -> existingInfo
                    .append("- ").append(c.getCommDsplNm())
                    .append(": ").append(c.getCommDesc()).append("\n"));

            List<TblUser> users = tblUserRepository.findAll();
            if (users.isEmpty()) return 0;

            String prompt = buildCommunityPrompt(existingInfo.toString(), existing.size());
            String geminiResponse = callGemini(prompt);

            JsonNode root = objectMapper.readTree(geminiResponse);
            String text = root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            JsonNode commJson = extractJson(text);
            if (commJson == null) return 0;

            String commNm     = commJson.path("commNm").asText();
            String commDsplNm = commJson.path("commDsplNm").asText();
            String commDesc   = commJson.path("commDesc").asText();
            long   ctgrSn     = commJson.path("ctgrSn").asLong(1);

            if (tblCommRepository.findByCommNm(commNm) != null) {
                commNm = commNm + "-" + System.currentTimeMillis() % 10000;
            }

            TblUser owner = users.get(new Random().nextInt(users.size()));

            TblComm newComm = new TblComm();
            newComm.setCommNm(commNm);
            newComm.setCommDsplNm(commDsplNm);
            newComm.setCommDesc(commDesc);
            newComm.setCtgrSn(ctgrSn);
            newComm.setPrvcyStng("public");
            newComm.setCreatrSn(owner.getUserSn());
            newComm.setStat("Y");
            newComm.setActvtnYn("Y");
            tblCommRepository.save(newComm);

            tblCommMbrRepository.save(TblCommMbr.builder()
                    .commSn(newComm.getCommSn())
                    .userSn(owner.getUserSn())
                    .role("OWNER").joinTyp("AUTO").stat("ACTIVE")
                    .creatrSn(owner.getUserSn())
                    .build());

            List<TblUser> shuffled = new ArrayList<>(users);
            Collections.shuffle(shuffled);
            int memberCount = Math.min(new Random().nextInt(3) + 2, shuffled.size());
            for (TblUser u : shuffled) {
                if (u.getUserSn() == owner.getUserSn()) continue;
                if (--memberCount < 0) break;
                tblCommMbrRepository.save(TblCommMbr.builder()
                        .commSn(newComm.getCommSn())
                        .userSn(u.getUserSn())
                        .role("MEMBER").joinTyp("AUTO").stat("ACTIVE")
                        .creatrSn(u.getUserSn())
                        .build());
            }

            long mbrCnt = tblCommMbrRepository.findAllByCommSn(newComm.getCommSn()).size();
            newComm.setMemberCnt(mbrCnt);

            log.info("[ContentGen] 커뮤니티 생성 — {} ({})", commDsplNm, commNm);
            return 1;

        } catch (Exception e) {
            log.warn("[ContentGen] 커뮤니티 생성 실패: {}", e.getMessage());
            return 0;
        }
    }

    // ── 게시글 생성 ────────────────────────────────────────────────────────
    private PostGenResult generatePostForCommunity(TblComm comm) throws Exception {
        List<TblCommMbr> members = tblCommMbrRepository.findAllByCommSn(comm.getCommSn())
                .stream().filter(m -> "ACTIVE".equals(m.getStat())).toList();
        if (members.isEmpty()) return PostGenResult.EMPTY;

        TblCommMbr writer   = members.get(new Random().nextInt(members.size()));
        Persona    persona  = selectRandomPersona();
        String     examples = getFewShotExamples(comm.getCommSn());

        String prompt = buildPostPrompt(comm, examples, persona);
        String geminiResponse = callGemini(prompt);

        JsonNode root = objectMapper.readTree(geminiResponse);
        String text = root.path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();

        JsonNode postJson = extractJson(text);
        if (postJson == null) return PostGenResult.EMPTY;

        String title   = postJson.path("title").asText();
        String content = postJson.path("content").asText();
        List<String> tags = new ArrayList<>();
        postJson.path("tags").forEach(t -> tags.add(t.asText()));

        TblCommPost post = TblCommPost.builder()
                .commSn(comm.getCommSn())
                .userSn(writer.getUserSn())
                .postTtl(title)
                .postCntnt(content)
                .creatrSn(writer.getUserSn())
                .build();
        tblCommPostRepository.save(post);
        saveTags(post.getCommPostSn(), tags, writer.getUserSn());

        log.info("[ContentGen] 게시글 저장 — [{}] {} (페르소나: {})", comm.getCommDsplNm(), title, persona.name());
        return new PostGenResult(persona.name(), 1);
    }

    // ── 댓글 생성 ──────────────────────────────────────────────────────────
    private int generateCommentForLatestPost(TblComm comm) throws Exception {
        QTblCommPost qPost = QTblCommPost.tblCommPost;
        TblCommPost latestPost = new JPAQueryFactory(em)
                .selectFrom(qPost)
                .where(qPost.commSn.eq(comm.getCommSn()).and(qPost.stat.eq("ACTIVE")))
                .orderBy(qPost.commPostSn.desc())
                .fetchFirst();
        if (latestPost == null) return 0;

        List<TblCommMbr> commenters = tblCommMbrRepository.findAllByCommSn(comm.getCommSn())
                .stream()
                .filter(m -> "ACTIVE".equals(m.getStat()) && m.getUserSn() != latestPost.getUserSn())
                .limit(2).toList();
        if (commenters.isEmpty()) return 0;

        // 댓글 작성자마다 다른 페르소나 부여
        List<Persona> cmtPersonas = commenters.stream()
                .map(m -> selectRandomPersona())
                .toList();

        String prompt = buildCommentPrompt(latestPost, commenters.size(), cmtPersonas);
        String geminiResponse = callGemini(prompt);

        JsonNode root = objectMapper.readTree(geminiResponse);
        String text = root.path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();

        JsonNode cmtJson = extractJson(text);
        if (cmtJson == null || !cmtJson.has("comments")) return 0;

        int count = 0;
        JsonNode cmtArray = cmtJson.path("comments");
        for (int i = 0; i < cmtArray.size() && i < commenters.size(); i++) {
            long userSn = commenters.get(i).getUserSn();
            tblCommPostCmtRepository.save(TblCommPostCmt.builder()
                    .commPostSn(latestPost.getCommPostSn())
                    .userSn(userSn)
                    .cmtCntnt(cmtArray.get(i).path("content").asText())
                    .creatrSn(userSn)
                    .build());

            new JPAQueryFactory(em).update(QTblCommPost.tblCommPost)
                    .set(QTblCommPost.tblCommPost.cmtCnt, QTblCommPost.tblCommPost.cmtCnt.add(1))
                    .where(QTblCommPost.tblCommPost.commPostSn.eq(latestPost.getCommPostSn()))
                    .execute();
            count++;
        }
        return count;
    }

    // ── Few-shot 예시 조회 ─────────────────────────────────────────────────
    private String getFewShotExamples(long commSn) {
        QTblCommPost qPost = QTblCommPost.tblCommPost;
        List<TblCommPost> examples = new JPAQueryFactory(em)
                .selectFrom(qPost)
                .where(qPost.commSn.eq(commSn)
                        .and(qPost.stat.eq("ACTIVE"))
                        .and(qPost.actvtnYn.eq("Y")))
                .orderBy(qPost.likeCnt.desc())
                .limit(3)
                .fetch();

        if (examples.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("\n\n[이 커뮤니티 인기 게시글 — 스타일 참고]\n");
        for (TblCommPost p : examples) {
            sb.append("제목: ").append(p.getPostTtl()).append("\n");
            String preview = p.getPostCntnt().length() > 100
                    ? p.getPostCntnt().substring(0, 100) + "..." : p.getPostCntnt();
            sb.append("내용: ").append(preview).append("\n\n");
        }
        return sb.toString();
    }

    // ── 반응 생성 ──────────────────────────────────────────────────────────
    private static final String[] POST_LIKE_TYPES   = {"UP", "UP", "UP", "DOWN"};        // UP 위주
    private static final String[] CMT_REACT_TYPES   = {"LIKE", "LOVE", "HAHA", "WOW", "SAD", "ANGRY"};
    private static final int      REACTION_ATTEMPTS = 40; // 게시글·댓글 각 최대 시도 횟수

    public int generateReactions() {
        List<TblUser> users = tblUserRepository.findAll();
        if (users.isEmpty()) return 0;

        JPAQueryFactory q   = new JPAQueryFactory(em);
        QTblCommPost    qPost = QTblCommPost.tblCommPost;
        QTblCommPostCmt qCmt  = QTblCommPostCmt.tblCommPostCmt;

        List<TblCommPost> posts = q.selectFrom(qPost)
                .where(qPost.stat.eq("ACTIVE").and(qPost.actvtnYn.eq("Y")))
                .fetch();
        if (posts.isEmpty()) return 0;

        List<TblCommPostCmt> cmts = q.selectFrom(qCmt)
                .where(qCmt.stat.eq("ACTIVE").and(qCmt.actvtnYn.eq("Y")))
                .fetch();

        Random rnd   = new Random();
        int    count = 0;

        // ── 게시글 좋아요 ──────────────────────────────────────────────────
        for (int i = 0; i < REACTION_ATTEMPTS; i++) {
            TblUser      user = users.get(rnd.nextInt(users.size()));
            TblCommPost  post = posts.get(rnd.nextInt(posts.size()));
            long postSn = post.getCommPostSn(), userSn = user.getUserSn();

            if (tblCommPostLikeRepository.findByCommPostSnAndUserSn(postSn, userSn) != null) continue;

            String likeTyp = POST_LIKE_TYPES[rnd.nextInt(POST_LIKE_TYPES.length)];
            tblCommPostLikeRepository.save(TblCommPostLike.builder()
                    .commPostSn(postSn).userSn(userSn)
                    .likeTyp(likeTyp).creatrSn(userSn)
                    .build());

            q.update(qPost)
                    .set(qPost.likeCnt, qPost.likeCnt.add("UP".equals(likeTyp) ? 1 : -1))
                    .where(qPost.commPostSn.eq(postSn))
                    .execute();
            count++;
            log.debug("[ContentGen] 게시글 반응 — postSn={} user={} typ={}", postSn, userSn, likeTyp);
        }

        // ── 댓글 반응 ──────────────────────────────────────────────────────
        if (!cmts.isEmpty()) {
            for (int i = 0; i < REACTION_ATTEMPTS; i++) {
                TblUser        user = users.get(rnd.nextInt(users.size()));
                TblCommPostCmt cmt  = cmts.get(rnd.nextInt(cmts.size()));
                long cmtSn = cmt.getCommPostCmtSn(), userSn = user.getUserSn();

                if (tblCommPostCmtReactRepository.findByCommPostCmtSnAndUserSn(cmtSn, userSn) != null) continue;

                String reactTyp = CMT_REACT_TYPES[rnd.nextInt(CMT_REACT_TYPES.length)];
                tblCommPostCmtReactRepository.save(TblCommPostCmtReact.builder()
                        .commPostCmtSn(cmtSn).userSn(userSn)
                        .reactTyp(reactTyp).creatrSn(userSn)
                        .build());
                count++;
                log.debug("[ContentGen] 댓글 반응 — cmtSn={} user={} typ={}", cmtSn, userSn, reactTyp);
            }
        }

        log.info("[ContentGen] 반응 삽입 완료 — {}건", count);
        return count;
    }

    // ── 페르소나 랜덤 선택 ─────────────────────────────────────────────────
    private Persona selectRandomPersona() {
        return PERSONA_TYPES.get(new Random().nextInt(PERSONA_TYPES.size()));
    }

    // ── 프롬프트 빌더 ──────────────────────────────────────────────────────
    private String buildUserPrompt(String existingLoginIds) {
        return String.format("""
                현재 가입된 loginId 목록: %s

                위와 겹치지 않는 새로운 한국인 사용자 1명을 만들어주세요.
                실제 사람처럼 자연스럽게.

                JSON 형식으로만 응답:
                {
                  "loginId": "영문+숫자 조합 (8~15자)",
                  "name": "한국어 이름",
                  "email": "이메일 주소"
                }
                """, existingLoginIds);
    }

    private String buildCommunityPrompt(String existingList, int existingCount) {
        return String.format("""
                현재 %d개의 커뮤니티가 있습니다:
                %s

                위 커뮤니티들과 겹치지 않는 새로운 커뮤니티 1개를 만들어주세요.
                사람들이 가입하고 싶어할 만큼 매력적인 주제로.

                카테고리 SN 참고:
                1=개발/IT, 2=재테크/투자, 3=창업, 4=건강/운동, 5=취미/라이프스타일

                JSON 형식으로만 응답:
                {
                  "commNm": "url-safe-slug",
                  "commDsplNm": "커뮤니티 표시 이름",
                  "commDesc": "커뮤니티 설명 (50자 이내)",
                  "ctgrSn": 1
                }
                """, existingCount, existingList);
    }

    private String buildPostPrompt(TblComm comm, String examples, Persona persona) {
        return String.format("""
                당신은 "%s" 커뮤니티의 일반 회원입니다.
                커뮤니티 설명: %s

                ════ 당신의 성격: %s ════

                [글쓰기 지침]
                %s

                [자주 쓰는 말버릇 — 자연스럽게 1~2회 삽입]
                %s

                [이 표현들을 상황에 맞게 활용]
                %s

                [절대 쓰지 말 것]
                %s

                [문장 길이 패턴]
                %s
                %s
                %s

                위 성격대로, 이 커뮤니티에 진짜 사람이 올릴 법한 게시글 1개를 작성하세요.
                - 제목: 궁금증을 유발하거나 공감 가는 자연스러운 제목 (AI처럼 정리된 제목 금지)
                - 본문: 300~600자, 한국어, 구체적 경험 또는 정보 포함
                - 태그: 커뮤니티 주제에 맞는 1~2개 (#포함)

                JSON 형식으로만 응답:
                {"title": "제목", "content": "본문", "tags": ["#태그1"]}
                """,
                comm.getCommDsplNm(),
                comm.getCommDesc() != null ? comm.getCommDesc() : "",
                persona.name(),
                persona.writingGuide(),
                persona.habits(),
                persona.expressions(),
                persona.forbidden(),
                persona.sentencePattern(),
                examples.isEmpty() ? "" : examples,
                ANTI_AI_RULES
        );
    }

    private String buildCommentPrompt(TblCommPost post, int count, List<Persona> personas) {
        StringBuilder personaGuides = new StringBuilder();
        for (int i = 0; i < count; i++) {
            Persona p = i < personas.size() ? personas.get(i) : selectRandomPersona();
            personaGuides.append(String.format(
                "댓글 %d번 작성자 성격: %s\n  말버릇: %s\n  절대 금지: %s\n  문장 패턴: %s\n\n",
                i + 1, p.name(), p.habits(), p.forbidden(), p.sentencePattern()
            ));
        }

        return String.format("""
                다음 게시글에 서로 다른 성격의 댓글 %d개를 작성해주세요.

                게시글 제목: %s
                게시글 내용: %s

                [각 댓글 작성자의 성격]
                %s
                공통 요구사항: 40~120자, 한국어 구어체, 공감·질문·경험 공유 중 1가지
                댓글끼리 어미나 표현이 겹치지 않도록 작성하세요.

                %s

                JSON 형식으로만 응답:
                {"comments": [{"content": "댓글 내용"}]}
                """,
                count,
                post.getPostTtl(),
                post.getPostCntnt().length() > 200
                        ? post.getPostCntnt().substring(0, 200) : post.getPostCntnt(),
                personaGuides,
                ANTI_AI_RULES
        );
    }

    // ── Gemini API 호출 ────────────────────────────────────────────────────
    private static final long GEMINI_MIN_INTERVAL_MS = 6_000L;  // 최소 호출 간격 6초 → 분당 최대 10회 (한도 15회 대비 안전마진)
    private static final int  GEMINI_MAX_RETRY       = 4;
    private static final long GEMINI_BACKOFF_BASE    = 4_000L;  // 지수 백오프 기준 4초
    private static final long GEMINI_BACKOFF_MAX     = 64_000L; // 최대 대기 64초

    // 마지막 실제 호출 시각 추적 (고정 sleep 대신 경과 시간 기반 조절)
    private final java.util.concurrent.atomic.AtomicLong lastGeminiCallAt = new java.util.concurrent.atomic.AtomicLong(0);

    private String callGemini(String prompt) {
        // 마지막 호출로부터 최소 간격이 지나지 않았으면 남은 시간만 대기
        long elapsed = System.currentTimeMillis() - lastGeminiCallAt.get();
        if (elapsed < GEMINI_MIN_INTERVAL_MS) {
            sleep(GEMINI_MIN_INTERVAL_MS - elapsed);
        }
        lastGeminiCallAt.set(System.currentTimeMillis());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"contents\":[{\"parts\":[{\"text\":%s}]}]}",
                objectMapper.valueToTree(prompt).toString()
        );

        Exception lastException = null;
        for (int attempt = 1; attempt <= GEMINI_MAX_RETRY; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        geminiUrl() + geminiApiKey,
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        String.class
                );
                log.info("[ContentGen] Gemini 호출 성공 ({}회차)", attempt);
                return response.getBody();

            } catch (org.springframework.web.client.HttpClientErrorException e) {
                lastException = e;
                int    status   = e.getStatusCode().value();
                String respBody = e.getResponseBodyAsString();
                log.error("[ContentGen] Gemini HTTP {} ({}회차/{}) — body: {}",
                        status, attempt, GEMINI_MAX_RETRY, respBody);

                if (status == 429 && attempt < GEMINI_MAX_RETRY) {
                    // Gemini가 retryDelay 힌트를 주면 그 시간 우선 사용, 없으면 지수 백오프
                    long retryDelay = parseRetryDelay(respBody);
                    long backoff    = retryDelay > 0
                            ? retryDelay
                            : Math.min(GEMINI_BACKOFF_BASE * (1L << (attempt - 1)), GEMINI_BACKOFF_MAX);
                    log.warn("[ContentGen] Rate Limit → {}초 후 재시도 ({})",
                            backoff / 1000, retryDelay > 0 ? "서버 힌트" : "지수 백오프");
                    sleep(backoff);
                } else {
                    break; // 429 외 4xx는 재시도해도 의미 없음
                }

            } catch (Exception e) {
                // 네트워크 오류, 타임아웃 등
                lastException = e;
                log.error("[ContentGen] Gemini 네트워크 오류 ({}회차/{}) — {}: {}",
                        attempt, GEMINI_MAX_RETRY, e.getClass().getSimpleName(), e.getMessage());

                if (attempt < GEMINI_MAX_RETRY) {
                    long backoff = Math.min(GEMINI_BACKOFF_BASE * (1L << (attempt - 1)), GEMINI_BACKOFF_MAX);
                    sleep(backoff);
                }
            }
        }
        throw new RuntimeException("Gemini API 호출 실패 (재시도 소진): " + lastException.getMessage());
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /**
     * Gemini 429 응답 바디에서 retryDelay 값을 파싱해 밀리초로 반환한다.
     * 예: {"@type":"...RetryInfo","retryDelay":"43s"} → 43000ms (+ 1000ms 버퍼)
     * 파싱 실패 시 0 반환 → 호출부에서 지수 백오프로 폴백.
     */
    private long parseRetryDelay(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return 0;
        // "retryDelay": "43s" 형태를 정규식으로 추출
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"retryDelay\"\\s*:\\s*\"(\\d+)s\"")
                .matcher(responseBody);
        if (m.find()) {
            try {
                long seconds = Long.parseLong(m.group(1));
                return (seconds + 1) * 1_000L; // 1초 버퍼 추가
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    // ── JSON 파싱 헬퍼 ─────────────────────────────────────────────────────
    private JsonNode extractJson(String text) {
        try {
            int start = text.indexOf("{");
            int end = text.lastIndexOf("}") + 1;
            if (start == -1 || end == 0) return null;
            return objectMapper.readTree(text.substring(start, end));
        } catch (Exception e) {
            log.warn("[ContentGen] JSON 파싱 실패: {}", text);
            return null;
        }
    }

    // ── 생성 로그 파일 기록 ────────────────────────────────────────────────
    private static final String PERSONA_SECTION_MARKER = "## 페르소나별 누적\n";

    private void appendGenerationLog(int userCount, int commCount, int postCount,
                                     int cmtCount, List<RunEntry> entries) {
        try {
            Path   logPath = Path.of(LOG_FILE_PATH);
            String now     = LocalDateTime.now().format(LOG_DT_FMT);

            // 이번 실행 페르소나별 집계
            Map<String, int[]> runPersona = new LinkedHashMap<>();
            for (RunEntry e : entries) {
                if ("-".equals(e.personaName()) || "오류".equals(e.personaName())) continue;
                runPersona.merge(e.personaName(), new int[]{e.postCount(), e.cmtCount()},
                        (a, b) -> new int[]{a[0] + b[0], a[1] + b[1]});
            }

            String summaryRow = String.format("| %s | %d | %d | %d | %d |\n",
                    now, userCount, commCount, postCount, cmtCount);

            if (Files.notExists(logPath)) {
                Files.createDirectories(logPath.getParent());
                String initial = "# Content Generation Log\n\n"
                        + "## 누적 요약\n"
                        + "| 시각 | 유저 | 커뮤니티 | 게시글 | 댓글 |\n"
                        + "|---|:---:|:---:|:---:|:---:|\n"
                        + summaryRow
                        + "\n"
                        + buildPersonaTable(runPersona);
                Files.writeString(logPath, initial, StandardCharsets.UTF_8);
            } else {
                String content = Files.readString(logPath, StandardCharsets.UTF_8);

                // 기존 페르소나 테이블 파싱 후 머지
                Map<String, int[]> merged = parsePersonaTable(content);
                runPersona.forEach((name, counts) ->
                        merged.merge(name, counts, (a, b) -> new int[]{a[0] + b[0], a[1] + b[1]}));

                // 누적 요약 섹션(페르소나 앞까지) + 새 행 + 페르소나 테이블 재작성
                String summarySection = content.substring(0, content.indexOf(PERSONA_SECTION_MARKER));
                Files.writeString(logPath,
                        summarySection.stripTrailing() + "\n" + summaryRow + "\n\n" + buildPersonaTable(merged),
                        StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("[ContentGen] 로그 파일 기록 실패: {}", e.getMessage());
        }
    }

    private Map<String, int[]> parsePersonaTable(String content) {
        Map<String, int[]> map = new LinkedHashMap<>();
        int start = content.indexOf(PERSONA_SECTION_MARKER);
        if (start == -1) return map;

        for (String line : content.substring(start).split("\n")) {
            if (!line.startsWith("|") || line.contains("페르소나") || line.contains(":---:")) continue;
            String[] parts = line.split("\\|");
            if (parts.length < 4) continue;
            try {
                String name  = parts[1].trim();
                int    posts = Integer.parseInt(parts[2].trim());
                int    cmts  = Integer.parseInt(parts[3].trim());
                if (!name.isEmpty()) map.put(name, new int[]{posts, cmts});
            } catch (NumberFormatException ignored) {}
        }
        return map;
    }

    private String buildPersonaTable(Map<String, int[]> personaMap) {
        StringBuilder sb = new StringBuilder();
        sb.append(PERSONA_SECTION_MARKER);
        sb.append("| 페르소나 | 게시글 | 댓글 |\n");
        sb.append("|---|:---:|:---:|\n");
        personaMap.entrySet().stream()
                .sorted((a, b) -> b.getValue()[0] - a.getValue()[0])
                .forEach(e -> sb.append(String.format("| %s | %d | %d |\n",
                        e.getKey(), e.getValue()[0], e.getValue()[1])));
        return sb.toString();
    }

    // ── 태그 저장 ──────────────────────────────────────────────────────────
    private void saveTags(long commPostSn, List<String> tagNms, long userSn) {
        for (String tagNm : tagNms) {
            if (tagNm == null || tagNm.isBlank()) continue;
            TblTag tag = tblTagRepository.findByTagNm(tagNm);
            if (tag == null) {
                tag = tblTagRepository.save(TblTag.builder()
                        .tagNm(tagNm).useCount(1).creatrSn(userSn).build());
            } else {
                tag.setUseCount(tag.getUseCount() + 1);
            }
            tblCommPostTagRepository.save(TblCommPostTag.builder()
                    .commPostSn(commPostSn).tagSn(tag.getTagSn()).creatrSn(userSn).build());
        }
    }
}
