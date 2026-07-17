package com.trackflow.server.util;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class CryptoUtilTest {
  @Test void hmacIsStableAndConstantTimeCompareWorks() {
    String sig = CryptoUtil.hmacSha256Hex("secret", "123\nbody");
    assertThat(sig).hasSize(64);
    assertThat(CryptoUtil.constantTimeEquals(sig, sig)).isTrue();
    assertThat(CryptoUtil.constantTimeEquals(sig, "bad")).isFalse();
  }
}
