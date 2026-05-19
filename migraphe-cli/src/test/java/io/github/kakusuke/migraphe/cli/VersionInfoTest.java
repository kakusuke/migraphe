package io.github.kakusuke.migraphe.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VersionInfoTest {

    @Test
    @DisplayName("format() が \"migraphe <version> (<commit>)\" 形式の文字列を返す")
    void shouldFormatVersionAndCommit() {
        VersionInfo info = new VersionInfo("0.1.0", "abc1234");

        assertThat(info.format()).isEqualTo("migraphe 0.1.0 (abc1234)");
    }

    @Test
    @DisplayName("リソースが見つからないときは version/commit ともに 'unknown' を返す")
    void shouldReturnUnknownWhenResourceMissing() {
        ClassLoader emptyLoader = new ClassLoader(null) {}; // 親なし → リソース皆無
        VersionInfo info = VersionInfo.load(emptyLoader);
        assertThat(info.version()).isEqualTo("unknown");
        assertThat(info.commit()).isEqualTo("unknown");
    }

    @Test
    @DisplayName("実 classpath にあるリソースから version/commit をロードできる (ビルド経由で生成されている)")
    void shouldLoadFromBuiltClasspath() {
        VersionInfo info = VersionInfo.load(VersionInfo.class.getClassLoader());

        assertThat(info.version()).isNotEqualTo("unknown");
        assertThat(info.commit()).isNotEqualTo("unknown");
    }

    @Test
    @DisplayName("リソースが見つかれば properties の version/commit を読む")
    void shouldLoadVersionAndCommitFromProperties() throws Exception {
        byte[] props =
                "version=1.2.3\ncommit=deadbee".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ClassLoader testLoader =
                new ClassLoader(null) {
                    @Override
                    public java.io.InputStream getResourceAsStream(String name) {
                        if ("migraphe-version.properties".equals(name)) {
                            return new java.io.ByteArrayInputStream(props);
                        }
                        return null;
                    }
                };

        VersionInfo info = VersionInfo.load(testLoader);

        assertThat(info.version()).isEqualTo("1.2.3");
        assertThat(info.commit()).isEqualTo("deadbee");
    }
}
