package com.yjlee.search.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yjlee.search.common.util.HttpRequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("HttpRequestUtils 테스트")
class HttpRequestUtilsTest {

  @Mock private HttpServletRequest request;

  private HttpRequestUtils httpRequestUtils;

  @BeforeEach
  void setUp() {
    httpRequestUtils = new HttpRequestUtils();
  }

  @Test
  @DisplayName("X-Forwarded-For 헤더에서 클라이언트 IP 추출")
  void testGetClientIpFromXForwardedFor() {
    // given
    when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1, 10.0.0.1, 172.16.0.1");

    // when
    String clientIp = httpRequestUtils.getClientIp(request);

    // then
    assertThat(clientIp).isEqualTo("192.168.1.1");
  }

  @Test
  @DisplayName("X-Forwarded-For가 비어있을 때 X-Real-IP에서 IP 추출")
  void testGetClientIpFromXRealIp() {
    // given
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(request.getHeader("X-Real-IP")).thenReturn("192.168.1.2");

    // when
    String clientIp = httpRequestUtils.getClientIp(request);

    // then
    assertThat(clientIp).isEqualTo("192.168.1.2");
  }

  @Test
  @DisplayName("프록시 헤더가 없을 때 RemoteAddr 반환")
  void testGetClientIpFromRemoteAddr() {
    // given
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(request.getHeader("X-Real-IP")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("192.168.1.3");

    // when
    String clientIp = httpRequestUtils.getClientIp(request);

    // then
    assertThat(clientIp).isEqualTo("192.168.1.3");
  }

  @Test
  @DisplayName("X-Forwarded-For 헤더가 빈 문자열일 때 처리")
  void testGetClientIpWithEmptyXForwardedFor() {
    // given
    when(request.getHeader("X-Forwarded-For")).thenReturn("");
    when(request.getHeader("X-Real-IP")).thenReturn("192.168.1.4");

    // when
    String clientIp = httpRequestUtils.getClientIp(request);

    // then
    assertThat(clientIp).isEqualTo("192.168.1.4");
  }

  @Test
  @DisplayName("X-Forwarded-For 헤더에 공백이 포함된 경우 trim 처리")
  void testGetClientIpWithSpaces() {
    // given
    when(request.getHeader("X-Forwarded-For")).thenReturn("  192.168.1.5  , 10.0.0.2");

    // when
    String clientIp = httpRequestUtils.getClientIp(request);

    // then
    assertThat(clientIp).isEqualTo("192.168.1.5");
  }

  @Test
  @DisplayName("User-Agent 헤더 추출")
  void testGetUserAgent() {
    // given
    String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    when(request.getHeader("User-Agent")).thenReturn(userAgent);

    // when
    String result = httpRequestUtils.getUserAgent(request);

    // then
    assertThat(result).isEqualTo(userAgent);
  }

  @Test
  @DisplayName("User-Agent 헤더가 없을 때 unknown 반환")
  void testGetUserAgentWhenNull() {
    // given
    when(request.getHeader("User-Agent")).thenReturn(null);

    // when
    String result = httpRequestUtils.getUserAgent(request);

    // then
    assertThat(result).isEqualTo("unknown");
  }

  @Test
  @DisplayName("User-Agent 헤더가 빈 문자열일 때")
  void testGetUserAgentWhenEmpty() {
    // given
    when(request.getHeader("User-Agent")).thenReturn("");

    // when
    String result = httpRequestUtils.getUserAgent(request);

    // then
    assertThat(result).isEqualTo("");
  }
}
