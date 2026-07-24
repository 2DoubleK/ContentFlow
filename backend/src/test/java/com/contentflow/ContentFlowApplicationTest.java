package com.contentflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

class ContentFlowApplicationTest {
    @Test
    void scansOnlyMyBatisMapperPackages() {
        MapperScan mapperScan = ContentFlowApplication.class.getAnnotation(MapperScan.class);

        assertThat(mapperScan.value()).containsExactlyInAnyOrder(
                "com.contentflow.auth.mapper",
                "com.contentflow.project.mapper",
                "com.contentflow.document.mapper",
                "com.contentflow.content.mapper",
                "com.contentflow.agent.mapper");
    }
}
