package com.knowra.cmm.config;

import com.knowra.cmm.security.CurrentUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final com.knowra.cmm.ratelimit.RateLimitInterceptor rateLimitInterceptor;

    @Value("${File.Store.Path}")
    private String uploadPath;

    /**
     * 업로드 파일 서빙. 위치는 <b>FileUtil 이 쓰는 경로와 똑같이</b> 푼다(new File(path) → 절대 경로).
     *
     * <p>예전엔 {@code "file:///" + path} 를 그대로 붙였다. 로컬 설정 {@code C:/home/upload} 는 윈도우에선
     * 절대 경로지만 macOS 에선 <b>작업 폴더 아래 상대 경로</b>라, 파일은 프로젝트 안 {@code C:/home/upload}
     * 에 쓰이는데 서빙은 {@code /C:/home/upload} 를 찾아 모든 업로드(커뮤니티 로고·편집기 사진)가 404 였다.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = new java.io.File(uploadPath).getAbsoluteFile().toURI().toString();
        if (!location.endsWith("/")) location += "/";
        // 예전 업로드(새 파일은 StorageService). ⚠️ 이미지 확장자만 내보낸다 — 확장자 허용 목록이 생기기 전에
        // 올라간 html·svg 가 남아 있으면 이 API 출처에서 문서로 열려 리프레시 쿠키로 토큰을 얻을 수 있었다.
        registry.addResourceHandler("/upload/**").addResourceLocations(location)
                .resourceChain(true)
                .addResolver(new org.springframework.web.servlet.resource.PathResourceResolver() {
                    @Override
                    protected org.springframework.core.io.Resource getResource(String resourcePath, org.springframework.core.io.Resource dir) throws java.io.IOException {
                        if (!resourcePath.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(png|jpe?g|gif|webp)$")) return null;
                        return super.getResource(resourcePath, dir);
                    }
                });
    }

    /** {@code @CurrentUser Long me} 를 컨트롤러에서 쓸 수 있게 한다. */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    /** 쓰기·발송 창구 도배 제한 — 경로별 한도는 RateLimitInterceptor.RULES. */
    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor);
    }
}
