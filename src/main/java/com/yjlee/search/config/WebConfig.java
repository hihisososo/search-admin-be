package com.yjlee.search.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOrigins("*")
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .exposedHeaders("X-Trace-ID")
        .maxAge(3600);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new RequestLoggingInterceptor());
  }

  @Slf4j
  public static class RequestLoggingInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
        HttpServletRequest request, HttpServletResponse response, Object handler) {

      String traceId = UUID.randomUUID().toString().substring(0, 8);
      MDC.put("traceId", traceId);

      response.setHeader("X-Trace-ID", traceId);

      log.debug(
          "Request started - Method: {}, URI: {}, RemoteAddr: {}, UserAgent: {}",
          request.getMethod(),
          request.getRequestURI(),
          getClientIpAddress(request),
          request.getHeader("User-Agent"));

      request.setAttribute("startTime", System.currentTimeMillis());
      return true;
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
      try {
        long startTime = (Long) request.getAttribute("startTime");
        long duration = System.currentTimeMillis() - startTime;

        log.debug(
            "Request completed - Status: {}, Duration: {}ms, ContentType: {}",
            response.getStatus(),
            duration,
            response.getContentType());

        if (ex != null) {
          log.error("Request failed with exception", ex);
        }

      } finally {
        MDC.clear();
      }
    }

    private String getClientIpAddress(HttpServletRequest request) {
      String xForwardedFor = request.getHeader("X-Forwarded-For");
      if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
        return xForwardedFor.split(",")[0].trim();
      }

      String xRealIp = request.getHeader("X-Real-IP");
      if (xRealIp != null && !xRealIp.isEmpty()) {
        return xRealIp;
      }

      return request.getRemoteAddr();
    }
  }
}
