package com.yjlee.search.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HttpRequestUtilsTest {

  private HttpRequestUtils httpRequestUtils;
  private HttpServletRequest mockRequest;

  @BeforeEach
  void setUp() {
    httpRequestUtils = new HttpRequestUtils();
    mockRequest = mock(HttpServletRequest.class);
  }

  @Test
  @DisplayName("X-Forwarded-For 헤더에서 클라이언트 IP 추출")
  void should_get_client_ip_from_x_forwarded_for_header() {
    when(mockRequest.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100, 10.0.0.1, 172.16.0.1");

    String clientIp = httpRequestUtils.getClientIp(mockRequest);

    assertThat(clientIp).isEqualTo("192.168.1.100");
  }

  @Test
  @DisplayName("X-Forwarded-For 헤더가 공백 포함시 trim 처리")
  void should_trim_client_ip_from_x_forwarded_for_header() {
    when(mockRequest.getHeader("X-Forwarded-For")).thenReturn("  192.168.1.100  , 10.0.0.1");

    String clientIp = httpRequestUtils.getClientIp(mockRequest);

    assertThat(clientIp).isEqualTo("192.168.1.100");
  }

  @Test
  @DisplayName("X-Real-IP 헤더에서 클라이언트 IP 추출")
  void should_get_client_ip_from_x_real_ip_header() {
    when(mockRequest.getHeader("X-Forwarded-For")).thenReturn(null);
    when(mockRequest.getHeader("X-Real-IP")).thenReturn("192.168.1.200");

    String clientIp = httpRequestUtils.getClientIp(mockRequest);

    assertThat(clientIp).isEqualTo("192.168.1.200");
  }

  @Test
  @DisplayName("헤더가 없을 때 RemoteAddr에서 IP 추출")
  void should_get_client_ip_from_remote_addr() {
    when(mockRequest.getHeader("X-Forwarded-For")).thenReturn(null);
    when(mockRequest.getHeader("X-Real-IP")).thenReturn(null);
    when(mockRequest.getRemoteAddr()).thenReturn("192.168.1.300");

    String clientIp = httpRequestUtils.getClientIp(mockRequest);

    assertThat(clientIp).isEqualTo("192.168.1.300");
  }

  @Test
  @DisplayName("X-Forwarded-For가 빈 문자열일 때 X-Real-IP 확인")
  void should_check_x_real_ip_when_x_forwarded_for_is_empty() {
    when(mockRequest.getHeader("X-Forwarded-For")).thenReturn("");
    when(mockRequest.getHeader("X-Real-IP")).thenReturn("192.168.1.400");

    String clientIp = httpRequestUtils.getClientIp(mockRequest);

    assertThat(clientIp).isEqualTo("192.168.1.400");
  }

  @Test
  @DisplayName("User-Agent 헤더 추출")
  void should_get_user_agent_from_header() {
    String expectedUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/91.0.4472.124";
    when(mockRequest.getHeader("User-Agent")).thenReturn(expectedUserAgent);

    String userAgent = httpRequestUtils.getUserAgent(mockRequest);

    assertThat(userAgent).isEqualTo(expectedUserAgent);
  }

  @Test
  @DisplayName("User-Agent 헤더가 없을 때 unknown 반환")
  void should_return_unknown_when_user_agent_is_null() {
    when(mockRequest.getHeader("User-Agent")).thenReturn(null);

    String userAgent = httpRequestUtils.getUserAgent(mockRequest);

    assertThat(userAgent).isEqualTo("unknown");
  }

  @Test
  @DisplayName("IPv6 주소 처리")
  void should_handle_ipv6_address() {
    when(mockRequest.getHeader("X-Forwarded-For")).thenReturn("2001:0db8:85a3:0000:0000:8a2e:0370:7334");

    String clientIp = httpRequestUtils.getClientIp(mockRequest);

    assertThat(clientIp).isEqualTo("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
  }
}